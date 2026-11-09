package ma.inpt.stages.common;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.util.HashMap;
import java.util.Map;

/** Comptes de service autorisés à appeler les services SOAP. Seuls les hachages BCrypt sont en configuration. */
public final class ComptesService {

    private final Map<String, String> hachages = new HashMap<>();

    /** Format de soa.ws.comptes : utilisateur:hachage,utilisateur:hachage */
    public ComptesService(String definition) {
        for (String compte : definition.split(",")) {
            int separateur = compte.indexOf(':');
            if (separateur < 1) {
                throw new IllegalStateException("Compte de service mal formé dans soa.ws.comptes.");
            }
            hachages.put(compte.substring(0, separateur).trim(), compte.substring(separateur + 1).trim());
        }
    }

    public boolean authentifier(String utilisateur, String motDePasse) {
        String hachage = hachages.get(utilisateur);
        return hachage != null && BCrypt.verifyer().verify(motDePasse.toCharArray(), hachage).verified;
    }
}
