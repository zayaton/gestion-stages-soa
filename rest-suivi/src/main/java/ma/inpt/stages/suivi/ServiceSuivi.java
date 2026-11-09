package ma.inpt.stages.suivi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;
import ma.inpt.stages.suivi.Modeles.ChangementStatut;
import ma.inpt.stages.suivi.Modeles.CompteRendu;
import ma.inpt.stages.suivi.Modeles.Creation;
import ma.inpt.stages.suivi.Modeles.Evaluation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;

/** Règles de gestion du service S6. */
public final class ServiceSuivi {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLANIFIE", Set.of("EN_COURS", "INTERROMPU"),
            "EN_COURS", Set.of("TERMINE", "INTERROMPU"),
            "TERMINE", Set.of(),
            "INTERROMPU", Set.of());
    /** Comptes rendus et évaluations : seulement pour un stage commencé. */
    private static final Set<String> SUIVI_POSSIBLE = Set.of("EN_COURS", "TERMINE");

    private final StageDao dao;

    public ServiceSuivi(StageDao dao) {
        this.dao = dao;
    }

    private static void exiger(Object corps, String nom) {
        if (corps == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of(nom + " attendu en JSON."));
        }
    }

    /**
     * Crée le dossier de stage d'une candidature acceptée (R10). Relancer l'appel pour la même candidature renvoie
     * le stage existant : l'étape 9 du processus peut donc être rejouée sans créer de doublon.
     */
    public Creation creer(NouveauStage saisie, String acteur) {
        exiger(saisie, "Un stage");
        Validateur.verifier(saisie);
        if (!saisie.dateFin().isAfter(saisie.dateDebut())) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("La date de fin doit être postérieure à la date de début."));
        }
        var existant = dao.trouverParCandidature(saisie.candidatureId());
        if (existant.isPresent()) {
            Stage s = existant.get();
            boolean meme = s.etudiantId() == saisie.etudiantId() && s.offreId() == saisie.offreId() && s.entrepriseId() == saisie.entrepriseId();
            if (!meme) {
                throw ErreurMetier.conflit("STAGE_EXISTANT", "Un autre stage existe déjà pour la candidature " + saisie.candidatureId() + ".");
            }
            return new Creation(s, true);
        }
        return new Creation(dao.inserer(saisie, acteur), false);
    }

    public Stage consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable("STAGE_INTROUVABLE", "Aucun stage ne correspond à l'identifiant " + id + "."));
    }

    public Stage parCandidature(long candidatureId) {
        Validateur.verifierIdentifiant(candidatureId);
        return dao.trouverParCandidature(candidatureId).orElseThrow(() ->
                ErreurMetier.introuvable("STAGE_INTROUVABLE", "Aucun stage n'existe pour la candidature " + candidatureId + "."));
    }

    public List<Stage> lister(String statut, Long etudiantId, Long entrepriseId) {
        if (statut != null && !statut.isBlank() && !TRANSITIONS.containsKey(statut)) {
            throw ErreurMetier.requeteInvalide("Paramètre de recherche invalide.", List.of("Le statut doit être PLANIFIE, EN_COURS, TERMINE ou INTERROMPU."));
        }
        return dao.lister(new StageDao.Filtre(statut, etudiantId, entrepriseId));
    }

    public List<Stage> historiqueEtudiant(long etudiantId) {
        Validateur.verifierIdentifiant(etudiantId);
        return dao.lister(new StageDao.Filtre(null, etudiantId, null));
    }

    public Stage changerStatut(long id, ChangementStatut demande, String acteur) {
        exiger(demande, "Un statut");
        Validateur.verifier(demande);
        if (!TRANSITIONS.containsKey(demande.statut())) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("Le statut doit être PLANIFIE, EN_COURS, TERMINE ou INTERROMPU."));
        }
        Stage stage = consulter(id);
        if (Objects.equals(stage.statut(), demande.statut())) {
            return stage;
        }
        if (!TRANSITIONS.get(stage.statut()).contains(demande.statut())) {
            throw ErreurMetier.conflit("TRANSITION_INTERDITE",
                    "Le passage du statut " + stage.statut() + " au statut " + demande.statut() + " n'est pas autorisé.");
        }
        dao.changerStatut(id, stage.statut(), demande.statut(), demande.commentaire(), acteur);
        return consulter(id);
    }

    private Stage suiviPossible(long id, String quoi) {
        Stage stage = consulter(id);
        if (!SUIVI_POSSIBLE.contains(stage.statut())) {
            throw ErreurMetier.conflit("STAGE_NON_COMMENCE",
                    quoi + " n'est possible que pour un stage en cours ou terminé (statut actuel : " + stage.statut() + ").");
        }
        return stage;
    }

    public CompteRendu ajouterCompteRendu(long id, NouveauCompteRendu saisie, String acteur) {
        exiger(saisie, "Un compte rendu");
        Validateur.verifier(saisie);
        suiviPossible(id, "Le dépôt d'un compte rendu");
        return dao.ajouterCompteRendu(id, saisie, acteur);
    }

    public List<CompteRendu> comptesRendus(long id) {
        consulter(id);
        return dao.comptesRendus(id);
    }

    public Evaluation ajouterEvaluation(long id, NouvelleEvaluation saisie, String acteur) {
        exiger(saisie, "Une évaluation");
        Validateur.verifier(saisie);
        suiviPossible(id, "L'évaluation");
        return dao.ajouterEvaluation(id, saisie, acteur);
    }

    public List<Evaluation> evaluations(long id) {
        consulter(id);
        return dao.evaluations(id);
    }

    public List<Operation> historique(long id) {
        consulter(id);
        return dao.historique(id);
    }
}
