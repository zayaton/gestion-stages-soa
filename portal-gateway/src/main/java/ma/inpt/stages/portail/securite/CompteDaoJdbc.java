package ma.inpt.stages.portail.securite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;

/** Requêtes JDBC du portail, sur le seul schéma soa_portail. */
public final class CompteDaoJdbc implements CompteDao {

    private final FournisseurConnexion source;

    public CompteDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    private static Long longOuNull(ResultSet r, String colonne) throws SQLException {
        long v = r.getLong(colonne);
        return r.wasNull() ? null : v;
    }

    @Override
    public Optional<Compte> parIdentifiant(String identifiant) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(
                "SELECT id, identifiant, mot_de_passe_hash, role, reference_id, actif FROM compte WHERE identifiant = ?")) {
            p.setString(1, identifiant);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Compte(r.getLong("id"), r.getString("identifiant"), r.getString("mot_de_passe_hash"), r.getString("role"),
                        longOuNull(r, "reference_id"), r.getBoolean("actif")));
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void creerSession(String jetonHash, long compteId, LocalDateTime expiration) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO session (jeton_hash, compte_id, expire_le) VALUES (?,?,?)")) {
            p.setString(1, jetonHash);
            p.setLong(2, compteId);
            p.setTimestamp(3, Timestamp.valueOf(expiration));
            p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public Optional<Utilisateur> session(String jetonHash, LocalDateTime maintenant) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(
                "SELECT c.id, c.identifiant, c.role, c.reference_id FROM session s JOIN compte c ON c.id = s.compte_id "
                        + "WHERE s.jeton_hash = ? AND s.expire_le > ? AND c.actif")) {
            p.setString(1, jetonHash);
            p.setTimestamp(2, Timestamp.valueOf(maintenant));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Utilisateur(r.getLong("id"), r.getString("identifiant"), r.getString("role"), longOuNull(r, "reference_id")));
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void supprimerSession(String jetonHash) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM session WHERE jeton_hash = ?")) {
            p.setString(1, jetonHash);
            p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public int purgerSessions(LocalDateTime maintenant) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM session WHERE expire_le <= ?")) {
            p.setTimestamp(1, Timestamp.valueOf(maintenant));
            return p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }
}
