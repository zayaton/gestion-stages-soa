package ma.inpt.stages.common;

import java.sql.Connection;
import java.sql.SQLException;

/** Source de connexions des DAO : le pool en production, une connexion annulable dans les tests. */
@FunctionalInterface
public interface FournisseurConnexion {

    Connection connexion() throws SQLException;
}
