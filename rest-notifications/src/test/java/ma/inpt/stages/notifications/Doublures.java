package ma.inpt.stages.notifications;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.notifications.Modeles.EtudiantActif;
import ma.inpt.stages.notifications.Modeles.Notification;
import ma.inpt.stages.notifications.Modeles.OffreOuverte;

/** Doublures de test : base en mémoire, expéditeur qui enregistre, sources de rappels configurables. */
final class Doublures {

    private Doublures() {
    }

    static final class Dao implements NotificationDao {
        final Map<Long, Notification> lignes = new LinkedHashMap<>();

        @Override
        public Notification inserer(Notification n) {
            long id = lignes.size() + 1L;
            Notification x = new Notification(id, n.type(), n.destinataire(), n.sujet(), n.corps(), n.dateEnvoi(), n.statut(), n.erreur(),
                    n.candidatureId(), n.offreId(), n.correlationId());
            lignes.put(id, x);
            return x;
        }

        @Override
        public Optional<Notification> trouver(long id) {
            return Optional.ofNullable(lignes.get(id));
        }

        @Override
        public List<Notification> lister(Filtre f) {
            return lignes.values().stream()
                    .filter(n -> f.destinataire() == null || f.destinataire().equals(n.destinataire()))
                    .filter(n -> f.type() == null || f.type().equals(n.type()))
                    .limit(f.limite()).toList();
        }

        @Override
        public boolean rappelDejaEnvoye(long offreId, String destinataire) {
            return lignes.values().stream().anyMatch(n -> "RAPPEL_DATE_LIMITE".equals(n.type()) && Long.valueOf(offreId).equals(n.offreId())
                    && destinataire.equals(n.destinataire()) && "ENVOYEE".equals(n.statut()));
        }
    }

    static final class Boite implements Expediteur {
        final List<String> destinataires = new ArrayList<>();
        final List<Modeles.Message> messages = new ArrayList<>();
        boolean enPanne;

        @Override
        public void envoyer(String destinataire, Modeles.Message message, String correlationId) throws EchecEnvoi {
            if (enPanne) {
                throw new EchecEnvoi("Envoi SMTP impossible : connexion refusée");
            }
            destinataires.add(destinataire);
            messages.add(message);
        }
    }

    static final class Sources implements SourcesRappels {
        final List<OffreOuverte> offres = new ArrayList<>();
        final List<EtudiantActif> etudiants = new ArrayList<>();
        final Map<Long, Set<Long>> candidats = new HashMap<>();
        boolean candidaturesEnPanne;
        int lecturesEtudiants;

        @Override
        public List<OffreOuverte> offresOuvertes() {
            return offres;
        }

        @Override
        public List<EtudiantActif> etudiantsActifs() {
            lecturesEtudiants++;
            return etudiants;
        }

        @Override
        public Set<Long> candidats(long offreId) {
            if (candidaturesEnPanne) {
                throw ErreurMetier.indisponible("SERVICE_CANDIDATURES_INDISPONIBLE", "Le service candidatures est momentanément indisponible.");
            }
            return candidats.getOrDefault(offreId, Set.of());
        }

        @Override
        public String raisonSociale(long entrepriseId) {
            return "Entreprise " + entrepriseId;
        }
    }

    static OffreOuverte offre(long id, LocalDate limite, String niveauMin, String... filieres) {
        return new OffreOuverte(id, "Offre " + id, limite, List.of(filieres), niveauMin, 1);
    }

    static EtudiantActif etudiant(long id, String filiere, String niveau) {
        return new EtudiantActif(id, "Étudiant " + id, "e" + id + "@inpt.ac.ma", filiere, niveau);
    }
}
