package ma.inpt.stages.common;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.BooleanSupplier;

/** Produit la réponse de GET /health, identique pour les services SOAP et REST. */
public final class VerificateurSante {

    /** Réponse HTTP : 200 si le service et sa base répondent, 503 sinon. */
    public record Etat(int codeHttp, String json) {
    }

    private final String nomService;
    private final BooleanSupplier baseDisponible;

    public VerificateurSante(String nomService, BooleanSupplier baseDisponible) {
        this.nomService = nomService;
        this.baseDisponible = baseDisponible;
    }

    public Etat verifier() {
        boolean ok = baseDisponible.getAsBoolean();
        String json = "{\"service\":\"" + echapper(nomService) + "\""
                + ",\"statut\":\"" + (ok ? "OK" : "DEGRADE") + "\""
                + ",\"baseDeDonnees\":\"" + (ok ? "OK" : "INDISPONIBLE") + "\""
                + ",\"horodatage\":\"" + OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS) + "\"}";
        return new Etat(ok ? 200 : 503, json);
    }

    private static String echapper(String texte) {
        return texte.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
