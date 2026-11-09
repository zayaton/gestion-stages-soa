package ma.inpt.stages.common;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/** Traduction des erreurs JDBC : aucun détail SQL ne sort d'un service. */
public final class Sql {

    private Sql() {
    }

    /** Une violation d'unicité devient un conflit, tout autre échec une indisponibilité. */
    public static ErreurMetier traduire(SQLException e, String messageConflit) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return ErreurMetier.conflit("CONFLIT_DONNEES", messageConflit);
        }
        return indisponible(e);
    }

    public static ErreurMetier indisponible(SQLException e) {
        org.slf4j.LoggerFactory.getLogger(Sql.class).error("Erreur d'accès aux données : {}", e.getMessage());
        return ErreurMetier.indisponible("BASE_INDISPONIBLE", "La base de données est momentanément indisponible.");
    }
}
