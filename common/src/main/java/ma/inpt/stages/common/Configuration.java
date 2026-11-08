package ma.inpt.stages.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Configuration d'un service. Ordre de priorité, du plus fort au plus faible :
 * propriété système (-Dcle=valeur), variable d'environnement (etudiants.port donne ETUDIANTS_PORT),
 * application.properties du module, soa-defaults.properties du socle commun.
 */
public final class Configuration {

    private final Properties proprietes;
    private final Function<String, String> environnement;

    Configuration(Properties proprietes, Function<String, String> environnement) {
        this.proprietes = proprietes;
        this.environnement = environnement;
    }

    public static Configuration charger() {
        Properties proprietes = new Properties();
        lire(proprietes, "soa-defaults.properties");
        lire(proprietes, "application.properties");
        Map<String, String> env = System.getenv();
        return new Configuration(proprietes, env::get);
    }

    private static void lire(Properties cible, String ressource) {
        try (InputStream flux = Configuration.class.getClassLoader().getResourceAsStream(ressource)) {
            if (flux != null) {
                cible.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible de la configuration : " + ressource, e);
        }
    }

    static String nomVariable(String cle) {
        return cle.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public String valeur(String cle) {
        String systeme = System.getProperty(cle);
        if (systeme != null) {
            return systeme;
        }
        String env = environnement.apply(nomVariable(cle));
        if (env != null && !env.isBlank()) {
            return env;
        }
        String fichier = proprietes.getProperty(cle);
        if (fichier == null) {
            throw new IllegalStateException("Paramètre de configuration manquant : " + cle);
        }
        return fichier.trim();
    }

    public int entier(String cle) {
        String valeur = valeur(cle);
        try {
            return Integer.parseInt(valeur);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Le paramètre " + cle + " doit être un entier : " + valeur, e);
        }
    }
}
