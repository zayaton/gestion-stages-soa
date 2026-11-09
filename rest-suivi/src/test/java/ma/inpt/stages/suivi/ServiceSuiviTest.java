package ma.inpt.stages.suivi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.suivi.Modeles.ChangementStatut;
import ma.inpt.stages.suivi.Modeles.Creation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceSuiviTest {

    private final StageDaoMemoire dao = new StageDaoMemoire();
    private final ServiceSuivi service = new ServiceSuivi(dao);

    private static NouveauStage nouveau(long candidature) {
        return new NouveauStage(candidature, 1L, 2L, 3L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 1), "Ghita Sentissi", "Pr. Essaadi");
    }

    private static ErreurMetier.Type type(Runnable r) {
        return assertThrows(ErreurMetier.class, r::run).type();
    }

    // Création (R10)

    @Test
    void unNouveauStageEstPlanifieEtHistorise() {
        Creation c = service.creer(nouveau(21), "processus");
        assertFalse(c.existait());
        assertEquals("PLANIFIE", c.stage().statut());
        assertEquals("CREATION", dao.historique(c.stage().id()).get(0).operation());
        assertEquals("processus", dao.historique(c.stage().id()).get(0).acteur());
    }

    @Test
    void r10RejouerLaCreationRenvoieLeMemeStageSansDoublon() {
        long id = service.creer(nouveau(21), "processus").stage().id();
        Creation bis = service.creer(nouveau(21), "processus");
        assertTrue(bis.existait());
        assertEquals(id, bis.stage().id());
        assertEquals(1, dao.stages.size());
    }

    @Test
    void unAutreStagePourLaMemeCandidatureEstUnConflit() {
        service.creer(nouveau(21), "processus");
        NouveauStage autre = new NouveauStage(21L, 9L, 2L, 3L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 1), null, null);
        assertEquals("STAGE_EXISTANT", assertThrows(ErreurMetier.class, () -> service.creer(autre, "x")).code());
    }

    @Test
    void laDateDeFinDoitSuivreLaDateDeDebut() {
        NouveauStage inverse = new NouveauStage(21L, 1L, 2L, 3L, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 1), null, null);
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.creer(inverse, "x"));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, e.type());
        assertTrue(e.details().get(0).contains("postérieure"));
    }

    @Test
    void lesChampsObligatoiresSontListes() {
        NouveauStage vide = new NouveauStage(null, null, -1L, null, null, null, null, null);
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.creer(vide, "x"));
        assertEquals(6, e.details().size(), e.details().toString());
    }

    @Test
    void unCorpsAbsentEstUneRequeteInvalide() {
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, type(() -> service.creer(null, "x")));
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, type(() -> service.changerStatut(1, null, "x")));
    }

    // Consultation

    @Test
    void unStageInconnuEstIntrouvable() {
        assertEquals("STAGE_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.consulter(9)).code());
        assertEquals("STAGE_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.parCandidature(9)).code());
        assertEquals(ErreurMetier.Type.INTROUVABLE, type(() -> service.historique(9)));
    }

    @Test
    void unIdentifiantNonPositifEstInvalide() {
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, type(() -> service.consulter(0)));
    }

    @Test
    void unStatutDeRechercheInconnuEstRefuse() {
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, type(() -> service.lister("EN_VACANCES", null, null)));
    }

    @Test
    void lHistoriqueDUnEtudiantNeContientQueSesStages() {
        service.creer(nouveau(21), "x");
        service.creer(new NouveauStage(22L, 7L, 2L, 3L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 1), null, null), "x");
        assertEquals(1, service.historiqueEtudiant(7).size());
        assertEquals(0, service.historiqueEtudiant(99).size());
    }

    // Statut

    @ParameterizedTest
    @CsvSource({"PLANIFIE,EN_COURS", "PLANIFIE,INTERROMPU", "EN_COURS,TERMINE", "EN_COURS,INTERROMPU"})
    void lesTransitionsPrevuesSontPermisesEtHistorisees(String de, String vers) {
        long id = dao.avecStatut(de).id();
        assertEquals(vers, service.changerStatut(id, new ChangementStatut(vers, "motif"), "responsable").statut());
        assertEquals("CHANGEMENT_STATUT", dao.historique(id).get(dao.historique(id).size() - 1).operation());
    }

    @ParameterizedTest
    @CsvSource({"PLANIFIE,TERMINE", "EN_COURS,PLANIFIE", "TERMINE,EN_COURS", "INTERROMPU,EN_COURS", "TERMINE,INTERROMPU"})
    void lesAutresTransitionsDonnentUnConflit(String de, String vers) {
        long id = dao.avecStatut(de).id();
        assertEquals("TRANSITION_INTERDITE", assertThrows(ErreurMetier.class, () -> service.changerStatut(id, new ChangementStatut(vers, null), "x")).code());
        assertEquals(de, dao.trouver(id).orElseThrow().statut());
    }

    @Test
    void reprendreLeMemeStatutEstSansEffet() {
        long id = dao.avecStatut("EN_COURS").id();
        int avant = dao.historique(id).size();
        assertEquals("EN_COURS", service.changerStatut(id, new ChangementStatut("EN_COURS", null), "x").statut());
        assertEquals(avant, dao.historique(id).size());
    }

    @Test
    void unStatutInconnuEstInvalide() {
        long id = dao.avecStatut("EN_COURS").id();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, type(() -> service.changerStatut(id, new ChangementStatut("FINI", null), "x")));
    }

    // Comptes rendus et évaluations

    private static NouveauCompteRendu cr() {
        return new NouveauCompteRendu("Semaines 1 à 4", "Prise en main du sujet.", "Pr. Essaadi");
    }

    private static NouvelleEvaluation note(String valeur) {
        return new NouvelleEvaluation(new BigDecimal(valeur), "Bon travail", "Pr. Essaadi");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_COURS", "TERMINE"})
    void unStageCommenceAccepteCompteRenduEtEvaluation(String statut) {
        long id = dao.avecStatut(statut).id();
        assertEquals("Semaines 1 à 4", service.ajouterCompteRendu(id, cr(), "x").periode());
        assertEquals(new BigDecimal("15.5"), service.ajouterEvaluation(id, note("15.5"), "x").note());
        assertEquals(1, service.comptesRendus(id).size());
        assertEquals(1, service.evaluations(id).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANIFIE", "INTERROMPU"})
    void unStageNonCommenceRefuseCompteRenduEtEvaluation(String statut) {
        long id = dao.avecStatut(statut).id();
        assertEquals("STAGE_NON_COMMENCE", assertThrows(ErreurMetier.class, () -> service.ajouterCompteRendu(id, cr(), "x")).code());
        assertEquals("STAGE_NON_COMMENCE", assertThrows(ErreurMetier.class, () -> service.ajouterEvaluation(id, note("12"), "x")).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.5", "20.01", "21", "12.345"})
    void uneNoteHorsBornesOuTropPreciseEstRefusee(String valeur) {
        long id = dao.avecStatut("TERMINE").id();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, type(() -> service.ajouterEvaluation(id, note(valeur), "x")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "20", "10.25"})
    void lesNotesAuxBornesSontAcceptees(String valeur) {
        long id = dao.avecStatut("TERMINE").id();
        assertEquals(new BigDecimal(valeur), service.ajouterEvaluation(id, note(valeur), "x").note());
    }

    @Test
    void unCompteRenduVideEstRefuse() {
        long id = dao.avecStatut("EN_COURS").id();
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.ajouterCompteRendu(id, new NouveauCompteRendu("", " ", null), "x"));
        assertEquals(3, e.details().size());
    }
}
