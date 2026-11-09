package ma.inpt.stages.etudiants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceEtudiantsTest {

    private final EtudiantDaoMemoire dao = new EtudiantDaoMemoire();
    private final ServiceEtudiants service = new ServiceEtudiants(dao);

    private static Etudiant saisie(String matricule, String filiere, String niveau, String email, String telephone) {
        return new Etudiant(null, matricule, "Alaoui", "Imane", filiere, niveau, email, telephone, "Fès", null, List.of("Java"));
    }

    private static Etudiant valide() {
        return saisie("INPT-2024-0100", "AMOA", "2A", "imane@inpt.ac.ma", "0612345678");
    }

    private void refuseAvec(Etudiant e, String message) {
        ErreurMetier erreur = assertThrows(ErreurMetier.class, () -> service.ajouter(e));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, erreur.type());
        assertTrue(erreur.details().contains(message), erreur.details().toString());
    }

    @Test
    void unNouvelEtudiantEstActifParDefaut() {
        Etudiant cree = service.ajouter(valide());
        assertEquals("ACTIF", cree.statut());
        assertEquals(1L, cree.id());
    }

    @Test
    void leMatriculeDoitRespecterLeFormat() {
        refuseAvec(saisie("X-1", "AMOA", "2A", "a@inpt.ac.ma", "0612345678"), "Le matricule doit respecter le format INPT-AAAA-NNNN.");
    }

    @Test
    void laFiliereDoitExister() {
        refuseAvec(saisie("INPT-2024-0100", "Philo", "2A", "a@inpt.ac.ma", "0612345678"), "La filière doit être AMOA, ASEDS, DATA, SESNum, ICCN, Cloud ou Smart ICT.");
    }

    @Test
    void leNiveauDoitExister() {
        refuseAvec(saisie("INPT-2024-0100", "AMOA", "4A", "a@inpt.ac.ma", "0612345678"), "Le niveau doit être 1A, 2A ou 3A.");
    }

    @Test
    void lEmailEtLeTelephoneSontControles() {
        refuseAvec(saisie("INPT-2024-0100", "AMOA", "2A", "pas-un-email", "0612345678"), "L'adresse e-mail est invalide.");
        refuseAvec(saisie("INPT-2024-0100", "AMOA", "2A", "a@inpt.ac.ma", "12345"), "Le téléphone doit compter 10 chiffres et commencer par 05, 06 ou 07.");
    }

    @Test
    void chaqueFiliereInptEstAcceptee() {
        int n = 0;
        for (String filiere : List.of("AMOA", "ASEDS", "DATA", "SESNum", "ICCN", "Cloud", "Smart ICT")) {
            n++;
            assertEquals(filiere, service.ajouter(saisie("INPT-2024-00" + (10 + n), filiere, "1A", "f" + n + "@inpt.ac.ma", "0612345678")).filiere());
        }
    }

    @Test
    void unDoublonDonneUnConflit() {
        service.ajouter(valide());
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.ajouter(valide()));
        assertEquals(ErreurMetier.Type.CONFLIT, e.type());
    }

    @Test
    void unEtudiantInconnuEstIntrouvable() {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.consulter(99));
        assertEquals(ErreurMetier.Type.INTROUVABLE, e.type());
        assertEquals("ETUDIANT_INTROUVABLE", e.code());
    }

    @Test
    void unIdentifiantNonPositifEstInvalidePasIntrouvable() {
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> service.consulter(0)).type());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> service.verifier(-1)).type());
    }

    // Règle R1

    @Test
    void r1UnEtudiantActifEstEligible() {
        ServiceEtudiants.Verification v = service.verifier(dao.ajouter("ACTIF").id());
        assertTrue(v.existe());
        assertTrue(v.eligible());
        assertNull(v.regle());
        assertNull(v.motif());
        assertEquals("Prénom Nom", v.nomComplet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDU", "EN_STAGE", "DIPLOME"})
    void r1SeulUnEtudiantActifPeutCandidater(String statut) {
        ServiceEtudiants.Verification v = service.verifier(dao.ajouter(statut).id());
        assertTrue(v.existe());
        assertFalse(v.eligible());
        assertEquals(statut, v.statut());
        assertEquals("R1", v.regle());
        assertFalse(v.motif().isBlank());
    }

    @Test
    void r1UnEtudiantInconnuNEstPasEligibleSansFaute() {
        ServiceEtudiants.Verification v = service.verifier(404);
        assertFalse(v.existe());
        assertFalse(v.eligible());
        assertEquals("R1", v.regle());
    }

    // Changements de statut

    @ParameterizedTest
    @CsvSource({"ACTIF,SUSPENDU", "ACTIF,EN_STAGE", "ACTIF,DIPLOME", "SUSPENDU,ACTIF", "EN_STAGE,ACTIF", "EN_STAGE,DIPLOME"})
    void lesTransitionsPrevuesSontPermises(String de, String vers) {
        long id = dao.ajouter(de).id();
        assertEquals(vers, service.changerStatut(id, vers).statut());
        assertEquals(vers, dao.trouver(id).orElseThrow().statut());
    }

    @ParameterizedTest
    @CsvSource({"SUSPENDU,EN_STAGE", "SUSPENDU,DIPLOME", "DIPLOME,ACTIF", "DIPLOME,EN_STAGE"})
    void lesAutresTransitionsDonnentUnConflit(String de, String vers) {
        long id = dao.ajouter(de).id();
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.changerStatut(id, vers));
        assertEquals(ErreurMetier.Type.CONFLIT, e.type());
        assertEquals(de, dao.trouver(id).orElseThrow().statut());
    }

    @Test
    void relancerLeMemeStatutEstSansEffet() {
        long id = dao.ajouter("EN_STAGE").id();
        assertEquals("EN_STAGE", service.changerStatut(id, "EN_STAGE").statut());
    }

    @Test
    void unStatutInconnuEstInvalide() {
        long id = dao.ajouter("ACTIF").id();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> service.changerStatut(id, "EN_VACANCES")).type());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> service.changerStatut(id, null)).type());
    }

    @Test
    void modifierConserveLeStatutSiAbsentEtControleLaTransition() {
        long id = dao.ajouter("SUSPENDU").id();
        assertEquals("SUSPENDU", service.modifier(id, valide()).statut());
        Etudiant versDiplome = valide().avec(null, "DIPLOME");
        assertEquals(ErreurMetier.Type.CONFLIT, assertThrows(ErreurMetier.class, () -> service.modifier(id, versDiplome)).type());
    }

    // Suppression

    @Test
    void unEtudiantEnStageNePeutPasEtreSupprime() {
        long id = dao.ajouter("EN_STAGE").id();
        assertEquals("ETUDIANT_EN_STAGE", assertThrows(ErreurMetier.class, () -> service.supprimer(id)).code());
        assertTrue(dao.trouver(id).isPresent());
    }

    @Test
    void unEtudiantActifPeutEtreSupprime() {
        long id = dao.ajouter("ACTIF").id();
        service.supprimer(id);
        assertTrue(dao.trouver(id).isEmpty());
    }
}
