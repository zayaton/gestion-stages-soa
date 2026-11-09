package ma.inpt.stages.etudiants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sur le vrai MySQL : chaque test travaille dans une transaction annulée, les données de démonstration ne changent pas. */
class EtudiantDaoJdbcIT {

    private static final EtudiantDao.Filtre SANS_FILTRE = new EtudiantDao.Filtre(null, null, null, null, null);

    private static BaseDeDonnees base;
    private ConnexionAnnulable connexion;
    private EtudiantDao dao;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "etudiants");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    @BeforeEach
    void transaction() throws Exception {
        connexion = new ConnexionAnnulable(base.connexion());
        dao = new EtudiantDaoJdbc(connexion);
    }

    @AfterEach
    void annuler() throws Exception {
        connexion.close();
    }

    private static Etudiant nouveau() {
        return new Etudiant(null, "INPT-2026-9001", "Test", "Étudiante", "DATA", "2A", "test.9001@inpt.ac.ma", "0612345678", "Fès", "ACTIF", List.of("Python", "SQL"));
    }

    @Test
    void trouverLitLesDonneesDeDemonstrationEtLeursAccents() {
        Etudiant aicha = dao.trouver(20).orElseThrow();
        assertEquals("Aïcha", aicha.prenom());
        assertEquals("Meknès", aicha.ville());
        assertEquals(List.of("Gestion de projet", "UML"), aicha.competences());
    }

    @Test
    void trouverRenvoieVidePourUnIdentifiantInconnu() {
        assertTrue(dao.trouver(999_999).isEmpty());
    }

    @Test
    void insererPuisRelireConserveToutEtAttribueUnId() {
        Etudiant cree = dao.inserer(nouveau());
        assertTrue(cree.id() > 20);
        Etudiant lu = dao.trouver(cree.id()).orElseThrow();
        assertEquals(cree, lu);
    }

    @Test
    void unMatriculeEnDoubleDonneUnConflit() {
        Etudiant doublon = new Etudiant(null, "INPT-2024-0001", "A", "B", "AMOA", "2A", "autre@inpt.ac.ma", "0612345678", "Rabat", "ACTIF", List.of());
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> dao.inserer(doublon));
        assertEquals(ErreurMetier.Type.CONFLIT, e.type());
    }

    @Test
    void unEmailEnDoubleDonneUnConflit() {
        Etudiant doublon = new Etudiant(null, "INPT-2026-9002", "A", "B", "AMOA", "2A", "salma.elidrissi@inpt.ac.ma", "0612345678", "Rabat", "ACTIF", List.of());
        assertEquals(ErreurMetier.Type.CONFLIT, assertThrows(ErreurMetier.class, () -> dao.inserer(doublon)).type());
    }

    @Test
    void listerSansFiltreRenvoieTout() {
        assertEquals(20, dao.lister(SANS_FILTRE).size());
    }

    @Test
    void listerCombineLesFiltres() {
        assertEquals(5, dao.lister(new EtudiantDao.Filtre("AMOA", null, null, null, null)).size());
        assertEquals(2, dao.lister(new EtudiantDao.Filtre("AMOA", "2A", null, null, null)).size());
        assertEquals(List.of(17L), dao.lister(new EtudiantDao.Filtre(null, null, "SUSPENDU", null, null)).stream().map(Etudiant::id).toList());
        assertEquals(List.of(3L, 9L), dao.lister(new EtudiantDao.Filtre(null, null, null, "Fès", null)).stream().map(Etudiant::id).toList());
    }

    @Test
    void laRechercheCouvreNomPrenomEtMatricule() {
        assertEquals(1, dao.lister(new EtudiantDao.Filtre(null, null, null, null, "Benjelloun")).size());
        assertEquals(1, dao.lister(new EtudiantDao.Filtre(null, null, null, null, "Salma")).size());
        assertEquals(1, dao.lister(new EtudiantDao.Filtre(null, null, null, null, "2024-0003")).size());
    }

    @Test
    void uneRechercheNePermetPasDInjectionSql() {
        assertTrue(dao.lister(new EtudiantDao.Filtre(null, null, null, null, "' OR '1'='1")).isEmpty());
        assertTrue(dao.lister(new EtudiantDao.Filtre("' OR '1'='1", null, null, null, null)).isEmpty());
    }

    @Test
    void modifierRemplaceAussiLesCompetences() {
        Etudiant cree = dao.inserer(nouveau());
        Etudiant change = new Etudiant(cree.id(), cree.matricule(), "Nouveau", cree.prenom(), cree.filiere(), "3A", cree.email(), cree.telephone(), "Agadir", "ACTIF", List.of("Spark"));
        dao.modifier(change);
        Etudiant lu = dao.trouver(cree.id()).orElseThrow();
        assertEquals("Nouveau", lu.nom());
        assertEquals("3A", lu.niveau());
        assertEquals(List.of("Spark"), lu.competences());
    }

    @Test
    void changerStatutModifieLaLigne() {
        dao.changerStatut(1, "SUSPENDU");
        assertEquals("SUSPENDU", dao.trouver(1).orElseThrow().statut());
    }

    @Test
    void supprimerRetireAussiLesCompetences() {
        Etudiant cree = dao.inserer(nouveau());
        dao.supprimer(cree.id());
        assertFalse(dao.trouver(cree.id()).isPresent());
    }
}
