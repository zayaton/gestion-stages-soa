package ma.inpt.stages.portail.securite;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.ErreurMetier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connexion par identifiant et mot de passe (hachage BCrypt), session par jeton aléatoire.
 * Seule l'empreinte SHA-256 du jeton est stockée : une copie de la base ne permet pas d'usurper une session.
 */
public final class ServiceAuthentification {

    /** Résultat d'une connexion : le jeton n'est connu que du client. */
    public record Session(String jeton, LocalDateTime expireLe, Utilisateur utilisateur) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAuthentification.class);
    private static final String MESSAGE_REFUS = "Identifiant ou mot de passe incorrect.";
    /** Hachage d'un mot de passe quelconque : vérifié quand l'identifiant est inconnu, pour une durée de réponse égale. */
    private static final String HACHAGE_LEURRE = BCrypt.withDefaults().hashToString(10, "leurre-temps-constant".toCharArray());

    private final CompteDao dao;
    private final Duration duree;
    private final Clock horloge;
    private final SecureRandom aleatoire = new SecureRandom();

    public ServiceAuthentification(CompteDao dao, Duration duree, Clock horloge) {
        this.dao = dao;
        this.duree = duree;
        this.horloge = horloge;
    }

    public Session connecter(String identifiant, String motDePasse) {
        if (identifiant == null || identifiant.isBlank() || motDePasse == null || motDePasse.isEmpty()) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("L'identifiant et le mot de passe sont obligatoires."));
        }
        Optional<CompteDao.Compte> compte = dao.parIdentifiant(identifiant.trim().toLowerCase());
        boolean valide = BCrypt.verifyer().verify(motDePasse.toCharArray(), compte.map(CompteDao.Compte::hash).orElse(HACHAGE_LEURRE)).verified;
        if (compte.isEmpty() || !valide || !compte.get().actif()) {
            LOG.warn("Connexion refusée pour {}", identifiant.trim());
            throw nonAuthentifie(MESSAGE_REFUS);
        }
        CompteDao.Compte c = compte.get();
        byte[] octets = new byte[32];
        aleatoire.nextBytes(octets);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        LocalDateTime expiration = LocalDateTime.now(horloge).plus(duree);
        dao.creerSession(empreinte(jeton), c.id(), expiration);
        LOG.info("Connexion de {} ({})", c.identifiant(), c.role());
        return new Session(jeton, expiration, new Utilisateur(c.id(), c.identifiant(), c.role(), c.referenceId()));
    }

    /** Utilisateur du jeton, ou 401 si le jeton est absent, inconnu ou expiré. */
    public Utilisateur verifier(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            throw nonAuthentifie("Connexion requise.");
        }
        return dao.session(empreinte(jeton.trim()), LocalDateTime.now(horloge))
                .orElseThrow(() -> nonAuthentifie("Session expirée ou invalide : reconnectez-vous."));
    }

    public void deconnecter(String jeton) {
        if (jeton != null && !jeton.isBlank()) {
            dao.supprimerSession(empreinte(jeton.trim()));
        }
    }

    public int purger() {
        return dao.purgerSessions(LocalDateTime.now(horloge));
    }

    private static ErreurMetier nonAuthentifie(String message) {
        return ErreurMetier.nonAuthentifie(message);
    }

    static String empreinte(String jeton) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
