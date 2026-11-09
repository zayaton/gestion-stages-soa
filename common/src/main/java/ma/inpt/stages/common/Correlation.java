package ma.inpt.stages.common;

import java.util.UUID;
import org.slf4j.MDC;

/** Identifiant suivant une candidature à travers les services, repris dans chaque ligne de journal. */
public final class Correlation {

    public static final String CLE_MDC = "correlationId";
    public static final String ENTETE_HTTP = "X-Correlation-Id";
    public static final String NAMESPACE = "http://stages.inpt.ma/commun";
    public static final String ELEMENT_SOAP = "CorrelationId";

    private Correlation() {
    }

    public static String courant() {
        return MDC.get(CLE_MDC);
    }

    /** Reprend l'identifiant reçu, ou en crée un si l'appel est le premier maillon. */
    public static String definir(String recu) {
        String valeur = recu == null || recu.isBlank() ? UUID.randomUUID().toString() : recu.trim();
        MDC.put(CLE_MDC, valeur);
        return valeur;
    }

    public static void effacer() {
        MDC.remove(CLE_MDC);
    }
}
