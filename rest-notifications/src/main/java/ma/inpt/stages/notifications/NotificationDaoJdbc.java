package ma.inpt.stages.notifications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;
import ma.inpt.stages.notifications.Modeles.Notification;

/** Requêtes JDBC du service S5, sur le seul schéma soa_notifications. */
public final class NotificationDaoJdbc implements NotificationDao {

    private static final String COLONNES = "id, type, destinataire, sujet, corps, date_envoi, statut, erreur, candidature_id, offre_id, correlation_id";

    private final FournisseurConnexion source;

    public NotificationDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    @Override
    public Notification inserer(Notification n) {
        String sql = "INSERT INTO notification (type, destinataire, sujet, corps, date_envoi, statut, erreur, candidature_id, offre_id, correlation_id) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, n.type());
            p.setString(2, n.destinataire());
            p.setString(3, n.sujet());
            p.setString(4, n.corps());
            p.setTimestamp(5, Timestamp.valueOf(n.dateEnvoi()));
            p.setString(6, n.statut());
            p.setString(7, n.erreur() == null ? null : n.erreur().substring(0, Math.min(500, n.erreur().length())));
            longOuNull(p, 8, n.candidatureId());
            longOuNull(p, 9, n.offreId());
            p.setString(10, n.correlationId());
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                r.next();
                return trouver(r.getLong(1)).orElseThrow();
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static void longOuNull(PreparedStatement p, int indice, Long valeur) throws SQLException {
        if (valeur == null) {
            p.setNull(indice, Types.BIGINT);
        } else {
            p.setLong(indice, valeur);
        }
    }

    @Override
    public Optional<Notification> trouver(long id) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("SELECT " + COLONNES + " FROM notification WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(lire(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public List<Notification> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM notification WHERE 1=1");
        List<Object> valeurs = new ArrayList<>();
        if (f.destinataire() != null && !f.destinataire().isBlank()) {
            sql.append(" AND destinataire = ?");
            valeurs.add(f.destinataire().trim());
        }
        if (f.candidatureId() != null) {
            sql.append(" AND candidature_id = ?");
            valeurs.add(f.candidatureId());
        }
        if (f.type() != null && !f.type().isBlank()) {
            sql.append(" AND type = ?");
            valeurs.add(f.type());
        }
        sql.append(" ORDER BY date_envoi DESC, id DESC LIMIT ?");
        valeurs.add(f.limite());
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setObject(i + 1, valeurs.get(i));
            }
            List<Notification> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(lire(r));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public boolean rappelDejaEnvoye(long offreId, String destinataire) {
        String sql = "SELECT 1 FROM notification WHERE type = 'RAPPEL_DATE_LIMITE' AND offre_id = ? AND destinataire = ? AND statut = 'ENVOYEE' LIMIT 1";
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, offreId);
            p.setString(2, destinataire);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static Notification lire(ResultSet r) throws SQLException {
        long candidature = r.getLong("candidature_id");
        Long candidatureId = r.wasNull() ? null : candidature;
        long offre = r.getLong("offre_id");
        Long offreId = r.wasNull() ? null : offre;
        return new Notification(r.getLong("id"), r.getString("type"), r.getString("destinataire"), r.getString("sujet"), r.getString("corps"),
                r.getObject("date_envoi", LocalDateTime.class), r.getString("statut"), r.getString("erreur"), candidatureId, offreId,
                r.getString("correlation_id"));
    }
}
