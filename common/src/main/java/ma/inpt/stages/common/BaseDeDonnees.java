package ma.inpt.stages.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pool de connexions d'un service vers son propre schéma MySQL.
 * Le pool démarre même si MySQL est arrêté : le service reste joignable et /health signale la panne.
 */
public final class BaseDeDonnees implements FournisseurConnexion, AutoCloseable {

    private final HikariDataSource source;

    private BaseDeDonnees(HikariDataSource source) {
        this.source = source;
    }

    /** Lit les paramètres soa.db.* (communs) et &lt;prefixe&gt;.db.* (propres au service). */
    public static BaseDeDonnees depuis(Configuration config, String prefixe) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("pool-" + prefixe);
        hikari.setJdbcUrl("jdbc:mysql://" + config.valeur("soa.db.hote") + ":" + config.valeur("soa.db.port")
                + "/" + config.valeur(prefixe + ".db.schema") + "?" + config.valeur("soa.db.parametres"));
        hikari.setUsername(config.valeur(prefixe + ".db.utilisateur"));
        hikari.setPassword(config.valeur("soa.db.password"));
        hikari.setMaximumPoolSize(config.entier("soa.db.pool.taille"));
        hikari.setConnectionTimeout(config.entier("soa.db.pool.delai.ms"));
        hikari.setValidationTimeout(Math.min(2000, config.entier("soa.db.pool.delai.ms")));
        hikari.setInitializationFailTimeout(-1);
        return new BaseDeDonnees(new HikariDataSource(hikari));
    }

    @Override
    public Connection connexion() throws SQLException {
        return source.getConnection();
    }

    public boolean estDisponible() {
        try (Connection connexion = source.getConnection()) {
            return connexion.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        source.close();
    }
}
