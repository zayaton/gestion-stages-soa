package ma.inpt.stages.offres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sur le vrai MySQL : chaque test travaille dans une transaction annulée, sauf le test de concurrence. */
class OffreDaoJdbcIT {

    private static final LocalDate AUJOURDHUI = LocalDate.now();

    private static BaseDeDonnees base;
    private ConnexionAnnulable connexion;
    private OffreDao dao;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "offres");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    @BeforeEach
    void transaction() throws Exception {
        connexion = new ConnexionAnnulable(base.connexion());
        dao = new OffreDaoJdbc(connexion);
    }

    @AfterEach
    void annuler() throws Exception {
        connexion.close();
    }

    private static OffreDao.Filtre filtre(String statut, String ville, String filiere, String niveau, Long entreprise, String competence, String recherche, LocalDate ouvertes) {
        return new OffreDao.Filtre(statut, ville, filiere, niveau, entreprise, competence, recherche, ouvertes);
    }

    private static Offre nouvelle(int places) {
        return new Offre(null, "Stage d'intégration", "Mission à Fès", List.of("SQL", "Java"), 8, AUJOURDHUI.plusDays(20), places,
                List.of("ASEDS", "AMOA"), "2A", "Fès", 1, "ACTIVE");
    }

    @Test
    void trouverChargeListesEtAccents() {
        Offre o = dao.trouver(6).orElseThrow();
        assertEquals("Assistant ingénieur réseaux 5G", o.titre());
        assertEquals(List.of("5G", "Réseaux IP"), o.competences());
        assertEquals(List.of("ICCN", "Smart ICT"), o.filieres());
        assertEquals("1A", o.niveauMinimum());
        assertEquals(2L, o.entrepriseId());
    }

    @Test
    void lesDatesDeDemonstrationSontRelativesAuJourDeChargement() {
        assertTrue(dao.trouver(13).orElseThrow().dateLimite().isBefore(AUJOURDHUI));
        assertFalse(dao.trouver(1).orElseThrow().dateLimite().isBefore(AUJOURDHUI));
    }

    @Test
    void insererPuisRelireConserveTout() {
        Offre creee = dao.inserer(nouvelle(2));
        Offre lue = dao.trouver(creee.id()).orElseThrow();
        assertEquals(creee.titre(), lue.titre());
        assertEquals(List.of("Java", "SQL"), lue.competences());
        assertEquals(List.of("AMOA", "ASEDS"), lue.filieres());
        assertEquals(creee.dateLimite(), lue.dateLimite());
    }

    @Test
    void listerCombineLesFiltres() {
        assertEquals(16, dao.lister(filtre(null, null, null, null, null, null, null, null)).size());
        assertEquals(1, dao.lister(filtre("BROUILLON", null, null, null, null, null, null, null)).size());
        assertEquals(2, dao.lister(filtre(null, null, null, null, 7L, null, null, null)).size());
        assertEquals(1, dao.lister(filtre(null, "Fès", null, null, null, null, null, null)).size());
        assertEquals(1, dao.lister(filtre(null, null, null, null, null, "Kubernetes", null, null)).size());
        assertEquals(1, dao.lister(filtre(null, null, null, null, null, null, "Paiement mobile", null)).size());
        assertTrue(dao.lister(filtre(null, null, null, null, null, null, "' OR '1'='1", null)).isEmpty());
    }

    @Test
    void leFiltreFiliereSEtendAuxOffresMultiFilieres() {
        List<Long> ids = dao.lister(filtre(null, null, "ICCN", null, null, null, null, null)).stream().map(Offre::id).toList();
        assertTrue(ids.containsAll(List.of(6L, 9L, 11L)), ids.toString());
        assertEquals(3, ids.size());
    }

    @Test
    void leFiltreNiveauGardeLesOffresAccessibles() {
        List<Offre> pour1A = dao.lister(filtre(null, null, null, "1A", null, null, null, null));
        assertTrue(pour1A.stream().allMatch(o -> "1A".equals(o.niveauMinimum())), pour1A.toString());
        assertTrue(dao.lister(filtre(null, null, null, "3A", null, null, null, null)).size() > pour1A.size());
    }

    @Test
    void ouvertesExclutBrouillonDateDepasseeEtOffreComplete() {
        List<Long> ids = dao.lister(filtre(null, null, null, null, null, null, null, AUJOURDHUI)).stream().map(Offre::id).toList();
        assertFalse(ids.contains(11L), "brouillon");
        assertFalse(ids.contains(13L), "date dépassée");
        assertFalse(ids.contains(14L), "sans place");
        assertFalse(ids.contains(15L), "pourvue");
        assertFalse(ids.contains(16L), "clôturée");
        assertTrue(ids.containsAll(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 12L)), ids.toString());
    }

    @Test
    void modifierRemplaceLesListes() {
        Offre creee = dao.inserer(nouvelle(2));
        Offre change = new Offre(creee.id(), "Nouveau", "D", List.of("Spark"), 4, AUJOURDHUI.plusDays(5), 1, List.of("DATA"), "3A", "Rabat", 1, "CLOTUREE");
        dao.modifier(change);
        Offre lue = dao.trouver(creee.id()).orElseThrow();
        assertEquals("Nouveau", lue.titre());
        assertEquals(List.of("Spark"), lue.competences());
        assertEquals(List.of("DATA"), lue.filieres());
        assertEquals("CLOTUREE", lue.statut());
    }

    @Test
    void supprimerRetireAussiLesListes() {
        Offre creee = dao.inserer(nouvelle(2));
        dao.supprimer(creee.id());
        assertTrue(dao.trouver(creee.id()).isEmpty());
    }

    @Test
    void decrementerPasseEnPourvueAZeroEtRefuseEnsuite() {
        Offre creee = dao.inserer(nouvelle(2));
        assertTrue(dao.decrementerPlaces(creee.id()));
        Offre apres = dao.trouver(creee.id()).orElseThrow();
        assertEquals(1, apres.nombrePlaces());
        assertEquals("ACTIVE", apres.statut());

        assertTrue(dao.decrementerPlaces(creee.id()));
        Offre zero = dao.trouver(creee.id()).orElseThrow();
        assertEquals(0, zero.nombrePlaces());
        assertEquals("POURVUE", zero.statut());

        assertFalse(dao.decrementerPlaces(creee.id()));
    }

    @Test
    void decrementerRefuseUneOffreNonActive() {
        assertFalse(dao.decrementerPlaces(11), "brouillon");
        assertFalse(dao.decrementerPlaces(14), "active sans place");
    }

    /** R10 : des acceptations simultanées ne peuvent pas attribuer plus de places qu'il n'en reste. */
    @Test
    void decrementerEstAtomiqueSousConcurrence() throws Exception {
        OffreDao reel = new OffreDaoJdbc(base);
        Offre creee = reel.inserer(nouvelle(3));
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> taches = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                taches.add(() -> reel.decrementerPlaces(creee.id()));
            }
            int reussites = 0;
            for (Future<Boolean> f : pool.invokeAll(taches)) {
                if (f.get()) {
                    reussites++;
                }
            }
            assertEquals(3, reussites);
            Offre fin = reel.trouver(creee.id()).orElseThrow();
            assertEquals(0, fin.nombrePlaces());
            assertEquals("POURVUE", fin.statut());
        } finally {
            pool.shutdownNow();
            reel.supprimer(creee.id());
        }
    }
}
