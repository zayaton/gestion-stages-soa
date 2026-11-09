package ma.inpt.stages.suivi;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.inpt.stages.suivi.Modeles.CompteRendu;
import ma.inpt.stages.suivi.Modeles.Evaluation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;

/** DAO en mémoire : teste les règles et l'API sans base de données. */
final class StageDaoMemoire implements StageDao {

    final Map<Long, Stage> stages = new LinkedHashMap<>();
    final List<CompteRendu> comptesRendus = new ArrayList<>();
    final List<Evaluation> evaluations = new ArrayList<>();
    final Map<Long, List<Operation>> historiques = new LinkedHashMap<>();
    private long suivant = 1;

    private void tracer(long id, String operation, String detail, String acteur) {
        historiques.computeIfAbsent(id, k -> new ArrayList<>()).add(new Operation(operation, detail, LocalDateTime.now(), acteur));
    }

    Stage avecStatut(String statut) {
        Stage s = inserer(new NouveauStage(100 + suivant, 1L, 1L, 1L, java.time.LocalDate.of(2027, 2, 1), java.time.LocalDate.of(2027, 5, 1), null, null), "test");
        Stage change = new Stage(s.id(), s.candidatureId(), s.etudiantId(), s.offreId(), s.entrepriseId(), s.dateDebut(), s.dateFin(),
                null, null, statut, s.creeLe());
        stages.put(s.id(), change);
        return change;
    }

    @Override
    public Stage inserer(NouveauStage n, String acteur) {
        long id = suivant++;
        Stage s = new Stage(id, n.candidatureId(), n.etudiantId(), n.offreId(), n.entrepriseId(), n.dateDebut(), n.dateFin(),
                n.encadrantEntreprise(), n.encadrantPedagogique(), "PLANIFIE", LocalDateTime.now());
        stages.put(id, s);
        tracer(id, "CREATION", "Dossier de stage créé", acteur);
        return s;
    }

    @Override
    public Optional<Stage> trouver(long id) {
        return Optional.ofNullable(stages.get(id));
    }

    @Override
    public Optional<Stage> trouverParCandidature(long candidatureId) {
        return stages.values().stream().filter(s -> s.candidatureId() == candidatureId).findFirst();
    }

    @Override
    public List<Stage> lister(Filtre f) {
        return stages.values().stream()
                .filter(s -> f.statut() == null || f.statut().equals(s.statut()))
                .filter(s -> f.etudiantId() == null || f.etudiantId() == s.etudiantId())
                .filter(s -> f.entrepriseId() == null || f.entrepriseId() == s.entrepriseId())
                .toList();
    }

    @Override
    public void changerStatut(long id, String ancien, String nouveau, String commentaire, String acteur) {
        Stage s = stages.get(id);
        stages.put(id, new Stage(s.id(), s.candidatureId(), s.etudiantId(), s.offreId(), s.entrepriseId(), s.dateDebut(), s.dateFin(),
                s.encadrantEntreprise(), s.encadrantPedagogique(), nouveau, s.creeLe()));
        tracer(id, "CHANGEMENT_STATUT", ancien + " → " + nouveau, acteur);
    }

    @Override
    public CompteRendu ajouterCompteRendu(long stageId, NouveauCompteRendu n, String acteur) {
        CompteRendu cr = new CompteRendu(comptesRendus.size() + 1L, stageId, n.periode(), n.contenu(), n.auteur(), LocalDateTime.now());
        comptesRendus.add(cr);
        tracer(stageId, "COMPTE_RENDU", n.periode(), acteur);
        return cr;
    }

    @Override
    public List<CompteRendu> comptesRendus(long stageId) {
        return comptesRendus.stream().filter(c -> c.stageId() == stageId).toList();
    }

    @Override
    public Evaluation ajouterEvaluation(long stageId, NouvelleEvaluation n, String acteur) {
        Evaluation ev = new Evaluation(evaluations.size() + 1L, stageId, n.note(), n.commentaire(), n.evaluateur(), LocalDateTime.now());
        evaluations.add(ev);
        tracer(stageId, "EVALUATION", n.note().toPlainString(), acteur);
        return ev;
    }

    @Override
    public List<Evaluation> evaluations(long stageId) {
        return evaluations.stream().filter(e -> e.stageId() == stageId).toList();
    }

    @Override
    public List<Operation> historique(long stageId) {
        return historiques.getOrDefault(stageId, List.of());
    }
}
