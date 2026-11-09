package ma.inpt.stages.candidatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.ChangementStatut;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.NouvelleEtape;
import ma.inpt.stages.candidatures.Modeles.Rejet;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Règles locales : R5 et R6 à l'enregistrement, R8, R9, démarrage de l'évaluation, journal des étapes. */
class ServiceCandidaturesTest {

    private final Doublures.Dao dao = new Doublures.Dao();
    private final ServiceCandidatures service = new ServiceCandidatures(dao, 5);

    private static NouvelleCandidature nouvelle(long etudiant, long offre) {
        return new NouvelleCandidature(null, etudiant, offre, 1L, "Lettre de motivation", 80);
    }

    private static String code(Runnable r) {
        return assertThrows(ErreurMetier.class, r::run).code();
    }

    // Enregistrement : R5, R6

    @Test
    void unEnregistrementCreeUneCandidatureDeposeeEtHistorisee() {
        Candidature c = service.enregistrer(nouvelle(1, 1), "client:bonita");
        assertEquals("DEPOSEE", c.statut());
        assertEquals("client:bonita", dao.historique(c.id()).get(0).acteur());
    }

    @Test
    void r5UnDoublonDonne409() {
        service.enregistrer(nouvelle(1, 1), "x");
        assertEquals("CANDIDATURE_EN_DOUBLE", code(() -> service.enregistrer(nouvelle(1, 1), "x")));
        assertTrue(service.verifierDoublon(1L, 1L).doublon());
        assertFalse(service.verifierDoublon(1L, 2L).doublon());
    }

    @Test
    void r6UneSixiemeCandidatureActiveDonne409() {
        for (long offre = 1; offre <= 5; offre++) {
            service.enregistrer(nouvelle(1, offre), "x");
        }
        assertTrue(service.compterActives(1).limiteAtteinte());
        assertEquals(5, service.compterActives(1).actives());
        assertEquals("LIMITE_CANDIDATURES_ATTEINTE", code(() -> service.enregistrer(nouvelle(1, 6), "x")));
    }

    @Test
    void uneSoumissionNeDonneQuUneCandidatureEtDuMemeCouple() {
        long soumission = dao.creerSoumission(1, 1, "Lettre").id();
        service.enregistrer(new NouvelleCandidature(soumission, 1L, 1L, 1L, "Lettre", 50), "x");
        assertEquals("ENREGISTREE", service.soumission(soumission).statut());
        assertEquals("SOUMISSION_DEJA_ENREGISTREE", code(() -> service.enregistrer(new NouvelleCandidature(soumission, 1L, 1L, 1L, "Lettre", 50), "x")));
        long autre = dao.creerSoumission(2, 1, "Lettre").id();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.enregistrer(new NouvelleCandidature(autre, 1L, 1L, 1L, "Lettre", 50), "x")).type());
    }

    @Test
    void desParametresDeDoublonManquantsSontUneRequeteInvalide() {
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.verifierDoublon(null, 1L)).type());
    }

    // R9

    @ParameterizedTest
    @CsvSource({"DEPOSEE,EN_COURS_EVALUATION", "DEPOSEE,ANNULEE", "EN_COURS_EVALUATION,ACCEPTEE", "EN_COURS_EVALUATION,REFUSEE"})
    void r9LesTransitionsPermises(String de, String vers) {
        long id = dao.ajouter(1, 1, de).id();
        assertEquals(vers, service.changerStatut(id, new ChangementStatut(vers, null), "x").statut());
        assertEquals(vers, dao.historique(id).get(0).nouveauStatut());
    }

    @ParameterizedTest
    @CsvSource({"DEPOSEE,ACCEPTEE", "DEPOSEE,REFUSEE", "EN_COURS_EVALUATION,DEPOSEE", "EN_COURS_EVALUATION,ANNULEE",
            "ACCEPTEE,REFUSEE", "REFUSEE,ACCEPTEE", "ANNULEE,DEPOSEE", "ACCEPTEE,EN_COURS_EVALUATION"})
    void r9ToutAutreTransitionDonne409(String de, String vers) {
        long id = dao.ajouter(1, 1, de).id();
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.changerStatut(id, new ChangementStatut(vers, null), "x"));
        assertEquals("TRANSITION_INTERDITE", e.code());
        assertTrue(e.getMessage().startsWith("Règle R9"));
        assertEquals(de, service.consulter(id).statut());
    }

    @Test
    void r9ReprendreLeStatutActuelEstSansEffet() {
        long id = dao.ajouter(1, 1, "ACCEPTEE").id();
        assertEquals("ACCEPTEE", service.changerStatut(id, new ChangementStatut("ACCEPTEE", null), "x").statut());
        assertTrue(dao.historique(id).isEmpty());
    }

    @Test
    void unStatutInconnuEstInvalide() {
        long id = dao.ajouter(1, 1, "DEPOSEE").id();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.changerStatut(id, new ChangementStatut("VALIDEE", null), "x")).type());
    }

    // R8

    @Test
    void r8UneCandidatureDeposeeSAnnule() {
        long id = dao.ajouter(1, 1, "DEPOSEE").id();
        assertEquals("ANNULEE", service.annuler(id, "etudiant:1").statut());
        assertEquals("etudiant:1", dao.historique(id).get(0).acteur());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_COURS_EVALUATION", "ACCEPTEE", "REFUSEE", "ANNULEE"})
    void r8ApresLeDepotLAnnulationEstRefusee(String statut) {
        long id = dao.ajouter(1, 1, statut).id();
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.annuler(id, "x"));
        assertEquals("ANNULATION_IMPOSSIBLE", e.code());
        assertTrue(e.getMessage().startsWith("Règle R8"));
    }

    @Test
    void demarrerLEvaluationNeSeFaitQuUneFois() {
        long id = dao.ajouter(1, 1, "DEPOSEE").id();
        assertEquals("EN_COURS_EVALUATION", service.demarrerEvaluation(id, "entreprise:1").statut());
        assertEquals("EVALUATION_IMPOSSIBLE", code(() -> service.demarrerEvaluation(id, "entreprise:1")));
    }

    @Test
    void uneCandidatureInconnueEstIntrouvable() {
        assertEquals("CANDIDATURE_INTROUVABLE", code(() -> service.consulter(77)));
        assertEquals("CANDIDATURE_INTROUVABLE", code(() -> service.annuler(77, "x")));
        assertEquals("SOUMISSION_INTROUVABLE", code(() -> service.soumission(77)));
    }

    @Test
    void unFiltreDeStatutInconnuEstRefuse() {
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.lister("FINIE", null, null, null)).type());
    }

    // Étapes et rejet signalés par Bonita

    @Test
    void bonitaSignaleSesEtapesEtSonRejet() {
        long s = dao.creerSoumission(1, 1, "Lettre").id();
        service.enregistrerEtape(s, new NouvelleEtape("VERIFICATION_ETUDIANT", "OK", "Étudiant actif"), "client:bonita");
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.enregistrerEtape(s, new NouvelleEtape("ETAPE_INVENTEE", "OK", null), "x")).type());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.enregistrerEtape(s, new NouvelleEtape("RECEPTION", "PEUT_ETRE", null), "x")).type());
        assertEquals("REJETEE", service.rejeter(s, new Rejet("R3", "Date limite dépassée."), "client:bonita").statut());
        assertEquals("SOUMISSION_TERMINEE", code(() -> service.rejeter(s, new Rejet("R4", "x"), "x")));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.rejeter(dao.creerSoumission(1, 2, "L").id(), new Rejet("R9", "x"), "x")).type());
    }
}
