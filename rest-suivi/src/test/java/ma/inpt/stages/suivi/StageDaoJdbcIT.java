package ma.inpt.stages.suivi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sur le vrai MySQL : chaque test travaille dans une transaction annulée. */
class StageDaoJdbcIT {

    private static BaseDeDonnees base;
    private ConnexionAnnulable connexion;
    private StageDao dao;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "suivi");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    @BeforeEach
    void transaction() throws Exception {
        connexion = new ConnexionAnnulable(base.connexion());
        dao = new StageDaoJdbc(connexion);
    }

    @AfterEach
    void annuler() throws Exception {
        connexion.close();
    }

    private static NouveauStage nouveau(long candidature) {
        return new NouveauStage(candidature, 1L, 1L, 1L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 1), "Ghita Sentissi", null);
    }

    @Test
    void lesDonneesDeDemonstrationSontLisibles() {
        Stage s = dao.trouver(2).orElseThrow();
        assertEquals("TERMINE", s.statut());
        assertEquals(11L, s.candidatureId());
        assertEquals("Pr. Leila Ghazali", s.encadrantPedagogique());
        assertEquals(1, dao.evaluations(2).size());
        assertEquals(0, new BigDecimal("16.50").compareTo(dao.evaluations(2).get(0).note()));
        assertEquals("Mi-parcours", dao.comptesRendus(2).get(0).periode());
        assertEquals(List.of("CREATION", "CHANGEMENT_STATUT", "COMPTE_RENDU", "CHANGEMENT_STATUT", "EVALUATION"),
                dao.historique(2).stream().map(Operation::operation).toList());
    }

    @Test
    void insererCreeAussiLaLigneDHistorique() {
        Stage s = dao.inserer(nouveau(901), "processus");
        assertEquals("PLANIFIE", s.statut());
        assertEquals(s, dao.trouverParCandidature(901).orElseThrow());
        assertEquals("processus", dao.historique(s.id()).get(0).acteur());
    }

    @Test
    void laBaseRefuseUnSecondStagePourLaMemeCandidature() {
        dao.inserer(nouveau(901), "x");
        assertEquals(ErreurMetier.Type.CONFLIT, assertThrows(ErreurMetier.class, () -> dao.inserer(nouveau(901), "x")).type());
    }

    @Test
    void listerCombineLesFiltres() {
        assertEquals(3, dao.lister(new StageDao.Filtre(null, null, null)).size());
        assertEquals(1, dao.lister(new StageDao.Filtre("EN_COURS", null, null)).size());
        assertEquals(1, dao.lister(new StageDao.Filtre(null, 19L, null)).size());
        assertEquals(1, dao.lister(new StageDao.Filtre(null, null, 7L)).size());
        assertTrue(dao.lister(new StageDao.Filtre("' OR '1'='1", null, null)).isEmpty());
    }

    @Test
    void statutCompteRenduEtEvaluationSontEnregistresEtHistorises() {
        Stage s = dao.inserer(nouveau(902), "x");
        dao.changerStatut(s.id(), "PLANIFIE", "EN_COURS", "Arrivée", "responsable");
        dao.ajouterCompteRendu(s.id(), new NouveauCompteRendu("Semaines 1 à 4", "Ateliers métier.", "Pr. Essaadi"), "responsable");
        dao.ajouterEvaluation(s.id(), new NouvelleEvaluation(new BigDecimal("14.75"), "Bien", "Pr. Essaadi"), "responsable");
        assertEquals("EN_COURS", dao.trouver(s.id()).orElseThrow().statut());
        assertEquals(0, new BigDecimal("14.75").compareTo(dao.evaluations(s.id()).get(0).note()));
        List<Operation> h = dao.historique(s.id());
        assertEquals(4, h.size());
        assertEquals("PLANIFIE → EN_COURS : Arrivée", h.get(1).detail());
        assertEquals("Note attribuée : 14.75/20".replace('.', ','), h.get(3).detail());
    }
}
