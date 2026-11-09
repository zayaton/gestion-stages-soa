package ma.inpt.stages.notifications;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ma.inpt.stages.common.Correlation;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;
import ma.inpt.stages.notifications.Modeles.BilanRappels;
import ma.inpt.stages.notifications.Modeles.EtudiantActif;
import ma.inpt.stages.notifications.Modeles.LigneRappel;
import ma.inpt.stages.notifications.Modeles.Message;
import ma.inpt.stages.notifications.Modeles.NouvelleNotification;
import ma.inpt.stages.notifications.Modeles.Notification;
import ma.inpt.stages.notifications.Modeles.OffreOuverte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Règles du service S5 : chaque notification est envoyée puis enregistrée, avec le statut ENVOYEE ou ECHEC. */
public final class ServiceNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceNotifications.class);
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final int LIMITE_PAR_DEFAUT = 100;

    private final NotificationDao dao;
    private final Expediteur expediteur;
    private final SourcesRappels sources;
    private final Clock horloge;

    public ServiceNotifications(NotificationDao dao, Expediteur expediteur, SourcesRappels sources, Clock horloge) {
        this.dao = dao;
        this.expediteur = expediteur;
        this.sources = sources;
        this.horloge = horloge;
    }

    /**
     * Envoie puis enregistre. Un échec SMTP n'est pas une erreur de la requête : la notification est conservée
     * avec le statut ECHEC, pour que le responsable la voie, et l'appelant poursuit son traitement.
     */
    public Notification envoyer(NouvelleNotification demande) {
        if (demande == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of("Une notification attendue en JSON."));
        }
        Validateur.verifier(demande);
        Message message = Gabarits.rendre(demande.type(), Modeles.sansNull(demande.variables()));
        String statut = "ENVOYEE";
        String erreur = null;
        try {
            expediteur.envoyer(demande.destinataire().trim(), message, Correlation.courant());
        } catch (Expediteur.EchecEnvoi e) {
            statut = "ECHEC";
            erreur = e.getMessage();
            LOG.warn("Notification {} à {} non envoyée : {}", demande.type(), demande.destinataire(), e.getMessage());
        }
        Notification n = dao.inserer(new Notification(0, demande.type(), demande.destinataire().trim(), message.sujet(), message.html(),
                LocalDateTime.now(horloge), statut, erreur, demande.candidatureId(), demande.offreId(), Correlation.courant()));
        LOG.info("Notification {} n° {} à {} : {}", n.type(), n.id(), n.destinataire(), n.statut());
        return n;
    }

    public Notification consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable("NOTIFICATION_INTROUVABLE",
                "Aucune notification ne correspond à l'identifiant " + id + "."));
    }

    public List<Notification> lister(String destinataire, Long candidatureId, String type, Integer limite) {
        if (type != null && !type.isBlank() && !Gabarits.existe(type)) {
            throw ErreurMetier.requeteInvalide("Paramètre de recherche invalide.", List.of("Type inconnu : " + type + "."));
        }
        int n = limite == null ? LIMITE_PAR_DEFAUT : limite;
        if (n < 1 || n > 500) {
            throw ErreurMetier.requeteInvalide("Paramètre de recherche invalide.", List.of("La limite doit être comprise entre 1 et 500."));
        }
        return dao.lister(new NotificationDao.Filtre(destinataire, candidatureId, type, n));
    }

    /**
     * Rappels de date limite : pour chaque offre ouverte qui ferme dans les {@code jours} prochains jours, un courriel
     * à chaque étudiant ACTIF compatible (filière et niveau, comme la règle R7) qui n'a pas candidaté.
     * Toutes les données sont lues avant le premier envoi : si un service manque, rien n'est envoyé (503).
     * Un rappel déjà envoyé pour la même offre n'est jamais renvoyé : l'exécution peut être relancée sans risque.
     */
    public BilanRappels executerRappels(int jours) {
        if (jours < 0 || jours > 30) {
            throw ErreurMetier.requeteInvalide("Paramètre invalide.", List.of("Le nombre de jours doit être compris entre 0 et 30."));
        }
        LocalDate aujourdhui = LocalDate.now(horloge);
        List<OffreOuverte> offres = sources.offresOuvertes().stream()
                .filter(o -> SourcesRappels.dansLaFenetre(o.dateLimite(), aujourdhui, jours)).toList();
        List<EtudiantActif> etudiants = offres.isEmpty() ? List.of() : sources.etudiantsActifs();
        Map<Long, Set<Long>> candidats = new HashMap<>();
        Map<Long, String> entreprises = new HashMap<>();
        for (OffreOuverte o : offres) {
            candidats.put(o.id(), sources.candidats(o.id()));
            entreprises.computeIfAbsent(o.entrepriseId(), sources::raisonSociale);
        }

        int envoyes = 0;
        int deja = 0;
        int echecs = 0;
        List<LigneRappel> details = new ArrayList<>();
        for (OffreOuverte o : offres) {
            for (EtudiantActif e : etudiants) {
                boolean compatible = o.filieres().contains(e.filiere()) && e.niveau().compareTo(o.niveauMinimum()) >= 0;
                if (!compatible || candidats.get(o.id()).contains(e.id())) {
                    continue;
                }
                if (dao.rappelDejaEnvoye(o.id(), e.email())) {
                    deja++;
                    details.add(new LigneRappel(o.id(), o.titre(), e.email(), "DEJA_ENVOYE"));
                    continue;
                }
                Map<String, String> variables = Map.of("nomEtudiant", e.nomComplet(), "titreOffre", o.titre(),
                        "entreprise", entreprises.get(o.entrepriseId()), "dateLimite", o.dateLimite().format(DATE_FR));
                Notification n = envoyer(new NouvelleNotification("RAPPEL_DATE_LIMITE", e.email(), variables, null, o.id()));
                if ("ENVOYEE".equals(n.statut())) {
                    envoyes++;
                } else {
                    echecs++;
                }
                details.add(new LigneRappel(o.id(), o.titre(), e.email(), n.statut()));
            }
        }
        LOG.info("Rappels : {} offre(s), {} envoyé(s), {} déjà envoyé(s), {} échec(s)", offres.size(), envoyes, deja, echecs);
        return new BilanRappels(jours, offres.size(), envoyes, deja, echecs, details);
    }
}
