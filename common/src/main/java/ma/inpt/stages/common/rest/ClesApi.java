package ma.inpt.stages.common.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** Clés d'API acceptées, une par client. Seule l'empreinte SHA-256 de chaque clé est en configuration. */
public final class ClesApi {

    private final Map<String, byte[]> empreintes = new HashMap<>();

    /** Format de soa.api.cles : client:sha256hex,client:sha256hex */
    public ClesApi(String definition) {
        for (String entree : definition.split(",")) {
            int separateur = entree.indexOf(':');
            if (separateur < 1) {
                throw new IllegalStateException("Clé d'API mal formée dans soa.api.cles.");
            }
            empreintes.put(entree.substring(0, separateur).trim(), HexFormat.of().parseHex(entree.substring(separateur + 1).trim()));
        }
    }

    /** Nom du client propriétaire de la clé, ou vide si la clé est inconnue. */
    public Optional<String> client(String cle) {
        if (cle == null || cle.isBlank()) {
            return Optional.empty();
        }
        byte[] empreinte = sha256(cle.trim());
        // Comparaison à temps constant sur toutes les entrées : la durée ne révèle rien de la clé.
        String trouve = null;
        for (Map.Entry<String, byte[]> e : empreintes.entrySet()) {
            if (MessageDigest.isEqual(e.getValue(), empreinte)) {
                trouve = e.getKey();
            }
        }
        return Optional.ofNullable(trouve);
    }

    static byte[] sha256(String texte) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
