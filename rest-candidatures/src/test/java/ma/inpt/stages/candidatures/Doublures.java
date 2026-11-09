package ma.inpt.stages.candidatures;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.Historique;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.common.ErreurMetier;

/** Doublures de test : base en mémoire et partenaires simulés, dont on peut provoquer les pannes. */
final class Doublures {

    private Doublures() {
    }

    static final class Dao implements CandidatureDao {
        final Map<Long, Candidature> candidatures = new LinkedHashMap<>();
        final Map<Long, Soumission> soumissions = new LinkedHashMap<>();
        final Map<Long, List<Etape>> etapes = new HashMap<>();
        final Map<Long, List<Historique>> historiques = new HashMap<>();

        Candidature ajouter(long etudiantId, long offreId, String statut) {
            long id = candidatures.size() + 1L;
            Candidature c = new Candidature(id, null, etudiantId, offreId, 1, "Lettre", 50, statut, LocalDateTime.now(), LocalDateTime.now());
            candidatures.put(id, c);
            return c;
        }

        @Override
        public Soumission creerSoumission(long etudiantId, long offreId, String lettre) {
            long id = soumissions.size() + 1L;
            soumissions.put(id, new Soumission(id, etudiantId, offreId, lettre, "EN_TRAITEMENT", null, null, null, null, LocalDateTime.now(), List.of()));
            return trouverSoumission(id).orElseThrow();
        }

        @Override
        public Optional<Soumission> trouverSoumission(long id) {
            Soumission s = soumissions.get(id);
            if (s == null) {
                return Optional.empty();
            }
            Long candidature = candidatures.values().stream().filter(c -> Long.valueOf(id).equals(c.soumissionId())).map(Candidature::id).findFirst().orElse(null);
            return Optional.of(new Soumission(s.id(), s.etudiantId(), s.offreId(), s.lettreMotivation(), s.statut(), s.regleRejetee(), s.motifRejet(),
                    s.bonitaCaseId(), candidature, s.creeLe(), etapes(id)));
        }

        @Override
        public List<Soumission> soumissionsEtudiant(long etudiantId) {
            return soumissions.keySet().stream().map(id -> trouverSoumission(id).orElseThrow()).filter(s -> s.etudiantId() == etudiantId).toList();
        }

        @Override
        public void terminerSoumission(long id, String statut, String regle, String motif) {
            Soumission s = soumissions.get(id);
            soumissions.put(id, new Soumission(s.id(), s.etudiantId(), s.offreId(), s.lettreMotivation(), statut, regle, motif, null, null, s.creeLe(), List.of()));
        }

        @Override
        public void ajouterEtape(long soumissionId, String etape, String resultat, String detail, String acteur) {
            etapes.computeIfAbsent(soumissionId, k -> new ArrayList<>()).add(new Etape(etape, resultat, detail, LocalDateTime.now(), acteur));
        }

        @Override
        public List<Etape> etapes(long soumissionId) {
            return List.copyOf(etapes.getOrDefault(soumissionId, List.of()));
        }

        @Override
        public Enregistrement enregistrer(NouvelleCandidature n, int limite, String acteur) {
            Optional<Candidature> doublon = candidatureNonAnnulee(n.etudiantId(), n.offreId());
            if (doublon.isPresent()) {
                return new Enregistrement(null, "R5", doublon.get().id());
            }
            if (compterActives(n.etudiantId()) >= limite) {
                return new Enregistrement(null, "R6", null);
            }
            long id = candidatures.size() + 1L;
            Candidature c = new Candidature(id, n.soumissionId(), n.etudiantId(), n.offreId(), n.entrepriseId(), n.lettreMotivation(),
                    n.scoreCompetences(), "DEPOSEE", LocalDateTime.now(), LocalDateTime.now());
            candidatures.put(id, c);
            ajouterHistorique(id, null, "DEPOSEE", acteur, "Candidature enregistrée");
            return new Enregistrement(c, null, null);
        }

        @Override
        public Optional<Candidature> trouver(long id) {
            return Optional.ofNullable(candidatures.get(id));
        }

        @Override
        public List<Candidature> lister(Filtre f) {
            return candidatures.values().stream()
                    .filter(c -> f.statut() == null || f.statut().equals(c.statut()))
                    .filter(c -> f.etudiantId() == null || f.etudiantId() == c.etudiantId())
                    .filter(c -> f.offreId() == null || f.offreId() == c.offreId())
                    .filter(c -> f.entrepriseId() == null || f.entrepriseId() == c.entrepriseId())
                    .toList();
        }

        @Override
        public Optional<Candidature> candidatureNonAnnulee(long etudiantId, long offreId) {
            return candidatures.values().stream().filter(c -> c.etudiantId() == etudiantId && c.offreId() == offreId && !"ANNULEE".equals(c.statut())).findFirst();
        }

        @Override
        public int compterActives(long etudiantId) {
            return (int) candidatures.values().stream().filter(c -> c.etudiantId() == etudiantId && Modeles.ACTIFS.contains(c.statut())).count();
        }

        @Override
        public boolean changerStatut(long id, String attendu, String nouveau, String acteur, String commentaire) {
            Candidature c = candidatures.get(id);
            if (!c.statut().equals(attendu)) {
                return false;
            }
            candidatures.put(id, new Candidature(c.id(), c.soumissionId(), c.etudiantId(), c.offreId(), c.entrepriseId(), c.lettreMotivation(),
                    c.scoreCompetences(), nouveau, c.creeLe(), LocalDateTime.now()));
            ajouterHistorique(id, attendu, nouveau, acteur, commentaire);
            return true;
        }

        @Override
        public void ajouterHistorique(long id, String ancien, String nouveau, String acteur, String commentaire) {
            historiques.computeIfAbsent(id, k -> new ArrayList<>()).add(new Historique(ancien, nouveau, LocalDateTime.now(), acteur, commentaire));
        }

        @Override
        public List<Historique> historique(long id) {
            return historiques.getOrDefault(id, List.of());
        }
    }

    /** Monde simulé : un étudiant ACTIF n° 1 (AMOA 2A), une offre ACTIVE n° 1 (2 places) d'une entreprise ACTIVE n° 1. */
    static final class Monde implements Partenaires {
        final Map<Long, EtatEtudiant> etudiants = new HashMap<>();
        final Map<Long, EtatOffre> offres = new HashMap<>();
        final Map<Long, EtatEntreprise> entreprises = new HashMap<>();
        final Map<Long, Integer> places = new HashMap<>();
        final Map<Long, Compatibilite> compatibilites = new HashMap<>();
        final Set<String> enPanne = new HashSet<>();
        final List<String> notifications = new ArrayList<>();
        final List<Long> stages = new ArrayList<>();
        final Set<Long> enStage = new HashSet<>();

        Monde() {
            etudiants.put(1L, actif("Salma El Idrissi", "salma@inpt.ac.ma"));
            offres.put(1L, new EtatOffre(true, "Stage AMOA", "ACTIVE", true, true, null, 1, 12));
            places.put(1L, 2);
            entreprises.put(1L, new EtatEntreprise(true, true, null, "NovaPay Maghreb", "contact@novapay.ma", "Ghita Sentissi"));
        }

        static EtatEtudiant actif(String nom, String email) {
            return new EtatEtudiant(true, true, null, nom, email, "AMOA", "2A");
        }

        private void panne(String service) {
            if (enPanne.contains(service)) {
                throw ErreurMetier.indisponible("SERVICE_" + service.toUpperCase() + "_INDISPONIBLE", "Le service " + service + " est momentanément indisponible.");
            }
        }

        List<String> types() {
            return notifications.stream().map(n -> n.substring(0, n.indexOf(' '))).toList();
        }

        @Override
        public EtatEtudiant etudiant(long id) {
            panne("etudiants");
            return etudiants.getOrDefault(id, new EtatEtudiant(false, false, "Aucun étudiant ne correspond à l'identifiant " + id + ".", null, null, null, null));
        }

        @Override
        public EtatOffre offre(long id) {
            panne("offres");
            return offres.getOrDefault(id, new EtatOffre(false, null, null, false, false, "Aucune offre ne correspond à l'identifiant " + id + ".", 0, 0));
        }

        @Override
        public EtatEntreprise entreprise(long id) {
            panne("entreprises");
            return entreprises.get(id);
        }

        @Override
        public Compatibilite compatibilite(long offreId, long etudiantId) {
            panne("offres");
            return compatibilites.getOrDefault(etudiantId, new Compatibilite(true, true, 67, null));
        }

        @Override
        public void retirerPlace(long offreId) {
            panne("offres");
            int reste = places.getOrDefault(offreId, 0);
            if (reste == 0) {
                throw ErreurMetier.conflit("PLUS_DE_PLACE", "Règle R10 : l'offre n'a plus de place disponible ; la candidature ne peut pas être acceptée.");
            }
            places.put(offreId, reste - 1);
        }

        @Override
        public void passerEnStage(long etudiantId) {
            panne("etudiants");
            enStage.add(etudiantId);
        }

        @Override
        public void creerStage(long candidatureId, long etudiantId, long offreId, long entrepriseId, LocalDate debut, LocalDate fin, String encadrant) {
            panne("suivi");
            if (!stages.contains(candidatureId)) {
                stages.add(candidatureId);
            }
        }

        @Override
        public void notifier(String type, String destinataire, Map<String, String> variables, Long candidatureId, Long offreId) {
            panne("notifications");
            notifications.add(type + " " + destinataire + " " + variables);
        }
    }
}
