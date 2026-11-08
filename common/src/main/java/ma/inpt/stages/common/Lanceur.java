package ma.inpt.stages.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Démarrage commun des services : journalisation et arrêt propre. */
public final class Lanceur {

    private static final Logger LOG = LoggerFactory.getLogger(Lanceur.class);

    private Lanceur() {
    }

    public static void arretPropre(AutoCloseable... ressources) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (AutoCloseable ressource : ressources) {
                try {
                    ressource.close();
                } catch (Exception e) {
                    LOG.warn("Erreur à l'arrêt : {}", e.getMessage());
                }
            }
        }));
    }

    public static void annoncer(String nomService, String hote, int port) {
        LOG.info("{} démarré sur http://{}:{} (santé : /health)", nomService, hote, port);
    }
}
