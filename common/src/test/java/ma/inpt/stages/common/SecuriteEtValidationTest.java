package ma.inpt.stages.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SecuriteEtValidationTest {

    private static String hache(String motDePasse) {
        return BCrypt.withDefaults().hashToString(4, motDePasse.toCharArray());
    }

    @Test
    void unBonMotDePasseEstAccepte() {
        ComptesService comptes = new ComptesService("a:" + hache("secret") + ",b:" + hache("autre"));
        assertTrue(comptes.authentifier("a", "secret"));
        assertTrue(comptes.authentifier("b", "autre"));
    }

    @Test
    void unMauvaisMotDePasseOuUnCompteInconnuEstRefuse() {
        ComptesService comptes = new ComptesService("a:" + hache("secret"));
        assertFalse(comptes.authentifier("a", "faux"));
        assertFalse(comptes.authentifier("inconnu", "secret"));
    }

    @Test
    void unComptesMalFormeEmpecheLeDemarrage() {
        assertThrows(IllegalStateException.class, () -> new ComptesService("sans-separateur"));
    }

    @Test
    void lesComptesLivresSontValides() {
        ComptesService comptes = new ComptesService(Configuration.charger().valeur("soa.ws.comptes"));
        assertTrue(comptes.authentifier("services", Configuration.charger().valeur("soa.ws.client.password")));
        assertTrue(comptes.authentifier("portail", "Portail#Ws2026"));
        assertTrue(comptes.authentifier("bonita", "Bonita#Ws2026"));
        assertTrue(comptes.authentifier("soapui", "SoapUI#Ws2026"));
    }

    record Saisie(@NotBlank(message = "Le nom est obligatoire.") @Size(max = 3, message = "Trop long.") String nom) {
    }

    @Test
    void chaqueChampInvalideEstListeEnFrancais() {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> Validateur.verifier(new Saisie("")));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, e.type());
        assertEquals(java.util.List.of("Le nom est obligatoire."), e.details());
        assertDoesNotThrow(() -> Validateur.verifier(new Saisie("abc")));
    }

    @Test
    void unIdentifiantNulOuNegatifEstInvalide() {
        assertThrows(ErreurMetier.class, () -> Validateur.verifierIdentifiant(0));
        assertThrows(ErreurMetier.class, () -> Validateur.verifierIdentifiant(-5));
        assertDoesNotThrow(() -> Validateur.verifierIdentifiant(1));
    }

    @Test
    void lesDatesXmlSontConverties() {
        LocalDate date = LocalDate.of(2026, 11, 8);
        assertEquals(date, Dates.versLocalDate(Dates.versXml(date)));
        assertNull(Dates.versXml(null));
        assertNull(Dates.versLocalDate(null));
    }

    @Test
    void laCorrelationReprendLaValeurRecueSinonEnCreeUne() {
        try {
            assertEquals("abc", Correlation.definir(" abc "));
            assertEquals("abc", Correlation.courant());
            assertFalse(Correlation.definir(null).isBlank());
            assertFalse(Correlation.definir("  ").isBlank());
        } finally {
            Correlation.effacer();
        }
        assertNull(Correlation.courant());
    }
}
