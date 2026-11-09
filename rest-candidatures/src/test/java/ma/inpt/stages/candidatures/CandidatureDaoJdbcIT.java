package ma.inpt.stages.candidatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sur le vrai MySQL : transactions annulées, sauf le test de concurrence qui nettoie derrière lui. */
class CandidatureDaoJdbcIT {

    private static BaseDeDonnees base;
    private ConnexionAnnulable connexion;
    private CandidatureDao dao;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "candidatures");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    @BeforeEach
    void transaction() throws Exception {
        connexion = new ConnexionAnnulable(base.connexion());
        dao = new CandidatureDaoJdbc(connexion);
    }

    @AfterEach
    void annuler() throws Exception {
        connexion.close();
    }

    private static NouvelleCandidature nouvelle(Long soumission, long etudiant, long offre) {
        return new NouvelleCandidature(soumission, etudiant, offre, 1L, "Lettre de test", 50);
    }

    @Test
    void lesDonneesDeDemonstrationOntSoumissionsEtEtapes() {
        Candidature c = dao.trouver(12).orElseThrow();
        assertEquals("ACCEPTEE", c.statut());
        assertEquals(12L, c.soumissionId());
        Soumission s = dao.trouverSoumission(12).orElseThrow();
        assertEquals(12L, s.candidatureId());
        List<String> etapes = s.etapes().stream().map(e -> e.etape()).toList();
        assertEquals(List.of("RECEPTION", "VERIFICATION_ETUDIANT", "VERIFICATION_OFFRE", "CONTROLE_CANDIDATURE", "PASSERELLE_DECISION",
                "ENREGISTREMENT", "NOTIFICATION_DEPOT", "TRANSMISSION_ENTREPRISE", "DEBUT_EVALUATION", "DECISION_ENTREPRISE",
                "MISE_A_JOUR_PLACES", "NOTIFICATION_RESULTAT", "CREATION_STAGE", "MISE_A_JOUR_ETUDIANT"), etapes);
        assertEquals("ANNULATION", dao.etapes(8).get(dao.etapes(8).size() - 1).etape());
    }

    @Test
    void lesControlesR5EtR6SurLesDonneesDeDemonstration() {
        assertTrue(dao.candidatureNonAnnulee(2, 2).isPresent(), "R5 : étudiant 2, offre 2");
        assertTrue(dao.candidatureNonAnnulee(11, 3).isEmpty(), "la candidature annulée ne compte pas");
        assertEquals(5, dao.compterActives(3), "R6 : étudiant 3");
        assertEquals(0, dao.compterActives(1));
    }

    @Test
    void enregistrerAppliqueR5EtR6() {
        Soumission s = dao.creerSoumission(1, 1, "Lettre de test");
        assertEquals("EN_TRAITEMENT", s.statut());
        CandidatureDao.Enregistrement ok = dao.enregistrer(nouvelle(s.id(), 1, 1), 5, "processus");
        assertNull(ok.regleViolee());
        assertEquals("DEPOSEE", ok.candidature().statut());
        assertEquals(ok.candidature().id(), dao.trouverSoumission(s.id()).orElseThrow().candidatureId());
        assertEquals("processus", dao.historique(ok.candidature().id()).get(0).acteur());

        CandidatureDao.Enregistrement doublon = dao.enregistrer(nouvelle(null, 1, 1), 5, "processus");
        assertEquals("R5", doublon.regleViolee());
        assertEquals(ok.candidature().id(), doublon.candidatureExistante());
        assertEquals("R6", dao.enregistrer(nouvelle(null, 3, 1), 5, "processus").regleViolee());
    }

    @Test
    void changerStatutSeulementDepuisLeStatutAttendu() {
        assertTrue(dao.changerStatut(1, "DEPOSEE", "EN_COURS_EVALUATION", "entreprise:3", "Évaluation démarrée"));
        assertFalse(dao.changerStatut(1, "DEPOSEE", "ANNULEE", "etudiant:2", null), "le statut a changé entre-temps");
        assertEquals("EN_COURS_EVALUATION", dao.trouver(1).orElseThrow().statut());
        assertEquals("entreprise:3", dao.historique(1).get(dao.historique(1).size() - 1).acteur());
    }

    @Test
    void filtres() {
        assertEquals(12, dao.lister(new CandidatureDao.Filtre(null, null, null, null)).size());
        assertEquals(5, dao.lister(new CandidatureDao.Filtre(null, 3L, null, null)).size());
        assertEquals(3, dao.lister(new CandidatureDao.Filtre("ACCEPTEE", null, null, null)).size());
        assertEquals(2, dao.lister(new CandidatureDao.Filtre(null, null, 3L, null)).size());
        assertEquals(3, dao.lister(new CandidatureDao.Filtre(null, null, null, 3L)).size());
    }

    @Test
    void soumissionRejeteeEtEtapes() {
        Soumission s = dao.creerSoumission(17, 1, "Lettre");
        dao.ajouterEtape(s.id(), "VERIFICATION_ETUDIANT", "REJET", "R1 : suspendu", "processus");
        dao.terminerSoumission(s.id(), "REJETEE", "R1", "Le compte de l'étudiant est suspendu.");
        Soumission lue = dao.trouverSoumission(s.id()).orElseThrow();
        assertEquals("REJETEE", lue.statut());
        assertEquals("R1", lue.regleRejetee());
        assertEquals(1, lue.etapes().size());
        assertEquals(1, dao.soumissionsEtudiant(17).size());
    }

    /** R6 sous concurrence : dix dépôts simultanés d'un étudiant n'en enregistrent jamais plus que la limite. */
    @Test
    void r6TientSousConcurrence() throws Exception {
        CandidatureDao reel = new CandidatureDaoJdbc(base);
        long etudiant = 9_001;
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<CandidatureDao.Enregistrement>> taches = new ArrayList<>();
            for (long offre = 1; offre <= 10; offre++) {
                long o = offre;
                taches.add(() -> reel.enregistrer(nouvelle(null, etudiant, o), 5, "test"));
            }
            int enregistrees = 0;
            int refusees = 0;
            for (Future<CandidatureDao.Enregistrement> f : pool.invokeAll(taches)) {
                if (f.get().candidature() != null) {
                    enregistrees++;
                } else if ("R6".equals(f.get().regleViolee())) {
                    refusees++;
                }
            }
            assertEquals(5, enregistrees);
            assertEquals(5, refusees);
            assertEquals(5, reel.compterActives(etudiant));
        } finally {
            pool.shutdownNow();
            try (Connection c = base.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM candidature WHERE etudiant_id = ?")) {
                p.setLong(1, etudiant);
                p.executeUpdate();
            }
        }
    }
}
