package ma.inpt.stages.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ConfigurationTest {

    private static Configuration config(Map<String, String> fichier, Map<String, String> env) {
        Properties proprietes = new Properties();
        proprietes.putAll(fichier);
        return new Configuration(proprietes, env::get);
    }

    @Test
    void lesValeursParDefautViennentDuSocleCommun() {
        assertEquals("3307", Configuration.charger().valeur("soa.db.port"));
    }

    @Test
    void laVariableEnvironnementSurchargeLeFichier() {
        Configuration c = config(Map.of("etudiants.port", "8081"), Map.of("ETUDIANTS_PORT", "9081"));
        assertEquals(9081, c.entier("etudiants.port"));
    }

    @Test
    void laProprieteSystemeSurchargeLaVariableEnvironnement() {
        Configuration c = config(Map.of("test.cle.systeme", "fichier"), Map.of("TEST_CLE_SYSTEME", "env"));
        System.setProperty("test.cle.systeme", "systeme");
        try {
            assertEquals("systeme", c.valeur("test.cle.systeme"));
        } finally {
            System.clearProperty("test.cle.systeme");
        }
    }

    @Test
    void uneVariableVideNeSurchargePas() {
        Configuration c = config(Map.of("a.b", "fichier"), Map.of("A_B", "  "));
        assertEquals("fichier", c.valeur("a.b"));
    }

    @Test
    void uneCleManquanteDonneUnMessageEnFrancais() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config(Map.of(), Map.of()).valeur("x.y"));
        assertTrue(e.getMessage().contains("manquant"));
    }

    @Test
    void unEntierInvalideEstRefuse() {
        assertThrows(IllegalStateException.class, () -> config(Map.of("p", "abc"), Map.of()).entier("p"));
    }
}
