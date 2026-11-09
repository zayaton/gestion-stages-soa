package ma.inpt.stages.entreprises;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sur le vrai MySQL : chaque test travaille dans une transaction annulée. */
class EntrepriseDaoJdbcIT {

    private static BaseDeDonnees base;
    private ConnexionAnnulable connexion;
    private EntrepriseDao dao;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "entreprises");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    @BeforeEach
    void transaction() throws Exception {
        connexion = new ConnexionAnnulable(base.connexion());
        dao = new EntrepriseDaoJdbc(connexion);
    }

    @AfterEach
    void annuler() throws Exception {
        connexion.close();
    }

    private static Entreprise nouvelle() {
        return ServiceEntreprisesTest.saisie("009999999000099", "contact@nouvelle-test.ma", "ACTIVE");
    }

    @Test
    void trouverLitLesDonneesDeDemonstrationEtLeursAccents() {
        Entreprise e = dao.trouver(5).orElseThrow();
        assertEquals("Oued Solaire Énergies", e.raisonSociale());
        assertEquals("Fatima Zahra Aït Lahcen", e.contactNom());
        assertEquals("ACTIVE", e.statut());
    }

    @Test
    void insererPuisRelireConserveTout() {
        Entreprise creee = dao.inserer(nouvelle());
        assertTrue(creee.id() > 8);
        assertEquals(creee, dao.trouver(creee.id()).orElseThrow());
    }

    @Test
    void unIceEnDoubleDonneUnConflit() {
        Entreprise doublon = ServiceEntreprisesTest.saisie("001526374000081", "autre@test.ma", "ACTIVE");
        assertEquals(ErreurMetier.Type.CONFLIT, assertThrows(ErreurMetier.class, () -> dao.inserer(doublon)).type());
    }

    @Test
    void listerCombineLesFiltres() {
        assertEquals(8, dao.lister(new EntrepriseDao.Filtre(null, null, null, null)).size());
        assertEquals(2, dao.lister(new EntrepriseDao.Filtre(null, "Casablanca", null, null)).size());
        assertEquals(1, dao.lister(new EntrepriseDao.Filtre(null, null, "INACTIVE", null)).size());
        assertEquals(1, dao.lister(new EntrepriseDao.Filtre("Fintech", "Casablanca", "ACTIVE", null)).size());
        assertEquals(1, dao.lister(new EntrepriseDao.Filtre(null, null, null, "Zellige")).size());
        assertTrue(dao.lister(new EntrepriseDao.Filtre(null, null, null, "' OR '1'='1")).isEmpty());
    }

    @Test
    void modifierEtSupprimer() {
        Entreprise creee = dao.inserer(nouvelle());
        dao.modifier(creee.avec(creee.id(), "INACTIVE"));
        assertEquals("INACTIVE", dao.trouver(creee.id()).orElseThrow().statut());
        dao.supprimer(creee.id());
        assertTrue(dao.trouver(creee.id()).isEmpty());
    }
}
