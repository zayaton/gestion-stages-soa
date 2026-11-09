package ma.inpt.stages.portail.securite;

import java.time.LocalDateTime;
import java.util.Optional;

/** Comptes et sessions du portail (schéma soa_portail). */
public interface CompteDao {

    record Compte(long id, String identifiant, String hash, String role, Long referenceId, boolean actif) {
    }

    Optional<Compte> parIdentifiant(String identifiant);

    void creerSession(String jetonHash, long compteId, LocalDateTime expiration);

    /** Utilisateur d'une session non expirée, d'un compte actif. */
    Optional<Utilisateur> session(String jetonHash, LocalDateTime maintenant);

    void supprimerSession(String jetonHash);

    int purgerSessions(LocalDateTime maintenant);
}
