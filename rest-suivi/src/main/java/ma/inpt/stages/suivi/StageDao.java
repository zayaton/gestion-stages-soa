package ma.inpt.stages.suivi;

import java.util.List;
import java.util.Optional;
import ma.inpt.stages.suivi.Modeles.CompteRendu;
import ma.inpt.stages.suivi.Modeles.Evaluation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;

/** Accès aux données du service S6. Chaque écriture ajoute sa ligne d'historique dans la même transaction. */
public interface StageDao {

    record Filtre(String statut, Long etudiantId, Long entrepriseId) {
    }

    Stage inserer(NouveauStage stage, String acteur);

    Optional<Stage> trouver(long id);

    Optional<Stage> trouverParCandidature(long candidatureId);

    List<Stage> lister(Filtre filtre);

    void changerStatut(long id, String ancien, String nouveau, String commentaire, String acteur);

    CompteRendu ajouterCompteRendu(long stageId, NouveauCompteRendu compteRendu, String acteur);

    List<CompteRendu> comptesRendus(long stageId);

    Evaluation ajouterEvaluation(long stageId, NouvelleEvaluation evaluation, String acteur);

    List<Evaluation> evaluations(long stageId);

    List<Operation> historique(long stageId);
}
