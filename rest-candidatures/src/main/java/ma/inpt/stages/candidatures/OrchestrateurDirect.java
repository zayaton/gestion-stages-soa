package ma.inpt.stages.candidatures;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.candidatures.Partenaires.Compatibilite;
import ma.inpt.stages.candidatures.Partenaires.EtatEntreprise;
import ma.inpt.stages.candidatures.Partenaires.EtatEtudiant;
import ma.inpt.stages.candidatures.Partenaires.EtatOffre;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mode « direct » : les étapes du processus BPMN, dans le même ordre et avec les mêmes règles, exécutées en Java.
 * Secours pour le développement et les tests automatiques ; la référence reste le processus Bonita.
 */
public final class OrchestrateurDirect implements Orchestrateur {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestrateurDirect.class);
    private static final String PROCESSUS = "processus";

    private final ServiceCandidatures service;
    private final CandidatureDao dao;
    private final Partenaires partenaires;
    private final String emailResponsable;
    private final int delaiDebutStageJours;
    private final Clock horloge;

    public OrchestrateurDirect(ServiceCandidatures service, CandidatureDao dao, Partenaires partenaires, String emailResponsable,
                               int delaiDebutStageJours, Clock horloge) {
        this.service = service;
        this.dao = dao;
        this.partenaires = partenaires;
        this.emailResponsable = emailResponsable;
        this.delaiDebutStageJours = delaiDebutStageJours;
        this.horloge = horloge;
    }

    /** Première règle violée, avec son motif ; les contrôles suivants ne sont pas exécutés. */
    private record Rejet(String regle, String motif) {
    }

    // Étapes 1 à 6

    @Override
    public Soumission deposer(NouvelleSoumission demande, String acteur) {
        ServiceCandidatures.exiger(demande, "Une candidature");
        Validateur.verifier(demande);
        // Étape 1 : réception.
        Soumission s = dao.creerSoumission(demande.etudiantId(), demande.offreId(), demande.lettreMotivation().trim());
        dao.ajouterEtape(s.id(), Etapes.RECEPTION, "OK", "Candidature reçue", acteur);
        String etapeEnCours = Etapes.VERIFICATION_ETUDIANT;
        try {
            // Étape 2 : vérification de l'étudiant (S1).
            EtatEtudiant etudiant = partenaires.etudiant(demande.etudiantId());
            Rejet rejet = null;
            if (!etudiant.existe() || !etudiant.eligible()) {
                rejet = new Rejet("R1", etudiant.motif());
            }
            tracer(s, Etapes.VERIFICATION_ETUDIANT, rejet == null ? "OK" : "REJET", rejet == null ? "Étudiant actif (R1)" : rejet.regle() + " : " + rejet.motif());

            // Étape 3 : vérification de l'offre et de l'entreprise (S3, S2), compatibilité du profil (S3).
            EtatOffre offre = null;
            EtatEntreprise entreprise = null;
            Compatibilite compatibilite = null;
            if (rejet == null) {
                etapeEnCours = Etapes.VERIFICATION_OFFRE;
                offre = partenaires.offre(demande.offreId());
                if (!offre.existe() || !"ACTIVE".equals(offre.statut())) {
                    rejet = new Rejet("R2", offre.existe() ? "Cette offre n'est pas active (statut " + offre.statut() + ")." : offre.motif());
                } else {
                    entreprise = partenaires.entreprise(offre.entrepriseId());
                    if (!entreprise.referencee() || !entreprise.active()) {
                        rejet = new Rejet("R2", entreprise.motif());
                    } else {
                        compatibilite = partenaires.compatibilite(demande.offreId(), demande.etudiantId());
                        if (!compatibilite.filiereAcceptee() || !compatibilite.niveauSuffisant()) {
                            rejet = new Rejet("R7", compatibilite.motif());
                        }
                    }
                }
                tracer(s, Etapes.VERIFICATION_OFFRE, rejet == null ? "OK" : "REJET",
                        rejet == null ? "Offre active, entreprise active, profil compatible (R2, R7)" : rejet.regle() + " : " + rejet.motif());
            }

            // Étape 4 : contrôle de la candidature (date limite, places, doublon, limite de 5).
            if (rejet == null) {
                etapeEnCours = Etapes.CONTROLE_CANDIDATURE;
                if (!offre.dateLimiteRespectee()) {
                    rejet = new Rejet("R3", offre.motif());
                } else if (!offre.placesDisponibles()) {
                    rejet = new Rejet("R4", offre.motif());
                } else if (service.verifierDoublon(demande.etudiantId(), demande.offreId()).doublon()) {
                    rejet = new Rejet("R5", "Vous avez déjà une candidature en cours pour cette offre.");
                } else if (service.compterActives(demande.etudiantId()).limiteAtteinte()) {
                    rejet = new Rejet("R6", "Vous avez déjà " + service.limiteActives() + " candidatures actives : c'est le maximum.");
                }
                tracer(s, Etapes.CONTROLE_CANDIDATURE, rejet == null ? "OK" : "REJET",
                        rejet == null ? "Date limite, places, doublon et limite de 5 respectés (R3 à R6)" : rejet.regle() + " : " + rejet.motif());
            }

            // Étape 5 : passerelle de décision.
            String titre = offre != null && offre.titre() != null ? offre.titre() : titreSansBloquer(demande.offreId());
            if (rejet != null) {
                rejeter(s, rejet, etudiant, titre);
                return service.soumission(s.id());
            }
            etapeEnCours = Etapes.ENREGISTREMENT;
            tracer(s, Etapes.PASSERELLE_DECISION, "OK", "Conditions satisfaites");
            Candidature c;
            try {
                c = service.enregistrer(new NouvelleCandidature(s.id(), demande.etudiantId(), demande.offreId(), offre.entrepriseId(),
                        demande.lettreMotivation().trim(), compatibilite.score()), PROCESSUS);
            } catch (ErreurMetier e) {
                if (e.type() != ErreurMetier.Type.CONFLIT) {
                    throw e;
                }
                // Un dépôt simultané a pris la place entre le contrôle et l'enregistrement : R5 ou R6 s'applique.
                rejeter(s, new Rejet("CANDIDATURE_EN_DOUBLE".equals(e.code()) ? "R5" : "R6", e.getMessage()), etudiant, titre);
                return service.soumission(s.id());
            }
            tracer(s, Etapes.ENREGISTREMENT, "OK", "Candidature " + c.id() + " enregistrée");
            notifierEtape(s, Etapes.NOTIFICATION_DEPOT, "CONFIRMATION_DEPOT", etudiant.email(),
                    Map.of("nomEtudiant", etudiant.nomComplet(), "titreOffre", titre, "entreprise", entreprise.raisonSociale()), c.id(), c.offreId());

            // Étape 6 : transmission à l'entreprise.
            notifierEtape(s, Etapes.TRANSMISSION_ENTREPRISE, "NOUVELLE_CANDIDATURE_ENTREPRISE", entreprise.email(),
                    Map.of("entreprise", entreprise.raisonSociale(), "titreOffre", titre, "nomEtudiant", etudiant.nomComplet(),
                            "filiere", etudiant.filiere(), "niveau", etudiant.niveau(), "score", String.valueOf(compatibilite.score())),
                    c.id(), c.offreId());
            return service.soumission(s.id());
        } catch (ErreurMetier e) {
            if (e.type() != ErreurMetier.Type.INDISPONIBLE) {
                throw e;
            }
            // Gestion d'erreur du processus : soumission en ERREUR, responsable alerté, 503 pour l'appelant.
            dao.terminerSoumission(s.id(), "ERREUR", null, e.getMessage());
            tracer(s, Etapes.ERREUR, "ECHEC", nomEtape(etapeEnCours) + " : " + e.getMessage());
            alerter(nomEtape(etapeEnCours), e.getMessage(), "Soumission " + s.id());
            throw ErreurMetier.indisponible(e.code(), e.getMessage()
                    + " La soumission " + s.id() + " est interrompue ; le responsable des stages a été prévenu.");
        }
    }

    /** Rejet avant la lecture de l'offre (R1) : le titre sert seulement au courriel, une panne de S3 ne doit rien bloquer. */
    private String titreSansBloquer(long offreId) {
        try {
            EtatOffre o = partenaires.offre(offreId);
            if (o.existe() && o.titre() != null) {
                return o.titre();
            }
        } catch (ErreurMetier e) {
            LOG.debug("Titre de l'offre {} indisponible : {}", offreId, e.getMessage());
        }
        return "n° " + offreId;
    }

    private void rejeter(Soumission s, Rejet rejet, EtatEtudiant etudiant, String titre) {
        dao.terminerSoumission(s.id(), "REJETEE", rejet.regle(), rejet.motif());
        tracer(s, Etapes.PASSERELLE_DECISION, "REJET", rejet.regle() + " : " + rejet.motif());
        if (etudiant.existe()) {
            notifierEtape(s, Etapes.NOTIFICATION_REJET, "CANDIDATURE_REJETEE", etudiant.email(),
                    Map.of("nomEtudiant", etudiant.nomComplet(), "titreOffre", titre, "motif", rejet.motif(), "regle", rejet.regle()),
                    null, s.offreId());
        } else {
            tracer(s, Etapes.NOTIFICATION_REJET, "INFO", "Étudiant inconnu : aucun destinataire à prévenir");
        }
        LOG.info("Soumission {} rejetée : {} {}", s.id(), rejet.regle(), rejet.motif());
    }

    // Étapes 7 à 9

    @Override
    public ResultatDecision decider(long candidatureId, Decision decision, String acteur) {
        valider(decision);
        String choix = decision.decision();
        String commentaire = decision.commentaire() == null ? null : decision.commentaire().trim();
        Candidature c = enEvaluation(service, candidatureId);
        // Données utiles aux notifications : lues avant tout changement, pour qu'une panne ne laisse rien à moitié fait.
        EtatEtudiant etudiant = partenaires.etudiant(c.etudiantId());
        EtatOffre offre = partenaires.offre(c.offreId());
        EtatEntreprise entreprise = partenaires.entreprise(c.entrepriseId());
        Map<String, String> variables = new HashMap<>(Map.of("nomEtudiant", etudiant.nomComplet(), "titreOffre", offre.titre(),
                "entreprise", entreprise.raisonSociale()));
        if (commentaire != null && !commentaire.isEmpty()) {
            variables.put("commentaire", commentaire);
        }
        List<Etape> etapes = new ArrayList<>();

        switch (choix) {
            case "ACCEPTER" -> {
                // R10 : la place est réservée avant d'enregistrer l'acceptation. S'il n'en reste plus, rien ne change (409).
                partenaires.retirerPlace(c.offreId());
                etapes.add(tracer(c, Etapes.DECISION_ENTREPRISE, "OK", "Acceptée" + suffixe(commentaire), acteur));
                etapes.add(tracer(c, Etapes.MISE_A_JOUR_PLACES, "OK", "Place retirée de l'offre (R10)", PROCESSUS));
                service.transition(c, "ACCEPTEE", acteur, commentaire == null ? "Candidature acceptée" : commentaire);
                etapes.add(notifierEtape(c, "ACCEPTATION", etudiant.email(), variables));
                etapes.addAll(suivi(c, offre, entreprise));
            }
            case "REFUSER" -> {
                service.transition(c, "REFUSEE", acteur, commentaire == null ? "Candidature refusée" : commentaire);
                etapes.add(tracer(c, Etapes.DECISION_ENTREPRISE, "OK", "Refusée" + suffixe(commentaire), acteur));
                etapes.add(notifierEtape(c, "REFUS", etudiant.email(), variables));
            }
            default -> {
                // R11 : la candidature reste en cours d'évaluation ; la décision revient ensuite à l'entreprise.
                dao.ajouterHistorique(c.id(), c.statut(), c.statut(), acteur, "Demande de complément : " + commentaire);
                etapes.add(tracer(c, Etapes.DECISION_ENTREPRISE, "OK", "Complément demandé : " + commentaire, acteur));
                etapes.add(notifierEtape(c, "DEMANDE_COMPLEMENT", etudiant.email(), variables));
            }
        }
        return new ResultatDecision(service.consulter(c.id()), etapes);
    }

    /** Règles communes aux deux modes : décision connue, complément motivé. */
    static void valider(Decision decision) {
        ServiceCandidatures.exiger(decision, "Une décision");
        Validateur.verifier(decision);
        if (!List.of("ACCEPTER", "REFUSER", "DEMANDER_COMPLEMENT").contains(decision.decision())) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("La décision doit être ACCEPTER, REFUSER ou DEMANDER_COMPLEMENT."));
        }
        if ("DEMANDER_COMPLEMENT".equals(decision.decision()) && (decision.commentaire() == null || decision.commentaire().isBlank())) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("Une demande de complément doit préciser l'information attendue (commentaire)."));
        }
    }

    /** Une décision ne porte que sur une candidature en cours d'évaluation. */
    static Candidature enEvaluation(ServiceCandidatures service, long candidatureId) {
        Candidature c = service.consulter(candidatureId);
        if (!"EN_COURS_EVALUATION".equals(c.statut())) {
            throw ErreurMetier.conflit("DECISION_IMPOSSIBLE", "Une décision ne peut être prise que sur une candidature en cours d'évaluation "
                    + "(statut actuel : " + c.statut() + ")." + ("DEPOSEE".equals(c.statut()) ? " Démarrez d'abord l'évaluation." : ""));
        }
        return c;
    }

    /** Alerte au responsable, sans jamais faire échouer l'appelant. */
    void alerterResponsable(String etape, String erreur, String reference) {
        alerter(etape, erreur, reference);
    }

    private static String suffixe(String commentaire) {
        return commentaire == null || commentaire.isEmpty() ? "" : " : " + commentaire;
    }

    /** Étape 9 (R10) : dossier de stage et statut de l'étudiant. Deux opérations sans effet si on les rejoue. */
    private List<Etape> suivi(Candidature c, EtatOffre offre, EtatEntreprise entreprise) {
        List<Etape> etapes = new ArrayList<>();
        LocalDate debut = LocalDate.now(horloge).plusDays(delaiDebutStageJours);
        LocalDate fin = debut.plusWeeks(Math.max(1, offre.dureeSemaines()));
        try {
            partenaires.creerStage(c.id(), c.etudiantId(), c.offreId(), c.entrepriseId(), debut, fin, entreprise.contact());
            etapes.add(tracer(c, Etapes.CREATION_STAGE, "OK", "Dossier de stage créé du " + debut + " au " + fin + " (R10)", PROCESSUS));
        } catch (ErreurMetier e) {
            etapes.add(echecSuivi(c, Etapes.CREATION_STAGE, e));
        }
        try {
            partenaires.passerEnStage(c.etudiantId());
            etapes.add(tracer(c, Etapes.MISE_A_JOUR_ETUDIANT, "OK", "Étudiant passé EN_STAGE (R10)", PROCESSUS));
        } catch (ErreurMetier e) {
            etapes.add(echecSuivi(c, Etapes.MISE_A_JOUR_ETUDIANT, e));
        }
        return etapes;
    }

    private Etape echecSuivi(Candidature c, String etape, ErreurMetier e) {
        alerter(nomEtape(etape), e.getMessage(), "Candidature " + c.id() + " : relancer depuis l'espace responsable");
        return tracer(c, etape, "ECHEC", e.getMessage() + " À relancer : POST /api/v1/candidatures/" + c.id() + "/suivi/reprendre", PROCESSUS);
    }

    @Override
    public ResultatDecision reprendreSuivi(long candidatureId, String acteur) {
        Candidature c = service.consulter(candidatureId);
        if (!"ACCEPTEE".equals(c.statut())) {
            throw ErreurMetier.conflit("SUIVI_IMPOSSIBLE", "Seule une candidature acceptée a un suivi de stage (statut actuel : " + c.statut() + ").");
        }
        EtatOffre offre = partenaires.offre(c.offreId());
        EtatEntreprise entreprise = partenaires.entreprise(c.entrepriseId());
        return new ResultatDecision(c, suivi(c, offre, entreprise));
    }

    @Override
    public String mode() {
        return "direct";
    }

    @Override
    public Candidature annuler(long candidatureId, String acteur) {
        return service.annuler(candidatureId, acteur);
    }

    // Outils

    private void tracer(Soumission s, String etape, String resultat, String detail) {
        dao.ajouterEtape(s.id(), etape, resultat, detail, PROCESSUS);
    }

    private Etape tracer(Candidature c, String etape, String resultat, String detail, String acteur) {
        service.tracer(c, etape, resultat, detail, acteur);
        return new Etape(etape, resultat, detail, java.time.LocalDateTime.now(horloge), acteur);
    }

    /** Une notification qui échoue n'interrompt pas le processus : l'étape est marquée ECHEC et S5 garde la trace. */
    private void notifierEtape(Soumission s, String etape, String type, String destinataire, Map<String, String> variables, Long candidatureId, Long offreId) {
        try {
            partenaires.notifier(type, destinataire, variables, candidatureId, offreId);
            tracer(s, etape, "OK", "Courriel " + type + " envoyé à " + destinataire);
        } catch (ErreurMetier e) {
            tracer(s, etape, "ECHEC", e.getMessage());
            LOG.warn("Soumission {} : notification {} non envoyée ({})", s.id(), type, e.getMessage());
        }
    }

    private Etape notifierEtape(Candidature c, String type, String destinataire, Map<String, String> variables) {
        try {
            partenaires.notifier(type, destinataire, variables, c.id(), c.offreId());
            return tracer(c, Etapes.NOTIFICATION_RESULTAT, "OK", "Courriel " + type + " envoyé à " + destinataire, PROCESSUS);
        } catch (ErreurMetier e) {
            LOG.warn("Candidature {} : notification {} non envoyée ({})", c.id(), type, e.getMessage());
            return tracer(c, Etapes.NOTIFICATION_RESULTAT, "ECHEC", e.getMessage(), PROCESSUS);
        }
    }

    private void alerter(String etape, String erreur, String reference) {
        try {
            partenaires.notifier("ALERTE_RESPONSABLE", emailResponsable, Map.of("etape", etape, "erreur", erreur, "reference", reference), null, null);
        } catch (ErreurMetier e) {
            LOG.error("Alerte au responsable impossible ({}) : étape {}, {}", e.getMessage(), etape, erreur);
        }
    }

    private static String nomEtape(String etape) {
        return switch (etape) {
            case Etapes.VERIFICATION_ETUDIANT -> "Vérification de l'étudiant";
            case Etapes.VERIFICATION_OFFRE -> "Vérification de l'offre";
            case Etapes.CONTROLE_CANDIDATURE -> "Contrôle de la candidature";
            case Etapes.ENREGISTREMENT -> "Enregistrement de la candidature";
            case Etapes.CREATION_STAGE -> "Création du dossier de stage";
            case Etapes.MISE_A_JOUR_ETUDIANT -> "Mise à jour du statut de l'étudiant";
            default -> etape;
        };
    }
}
