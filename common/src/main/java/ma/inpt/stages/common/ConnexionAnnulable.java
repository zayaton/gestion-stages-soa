package ma.inpt.stages.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Pour les tests d'intégration des DAO : toutes les écritures restent dans une transaction annulée
 * à la fin du test, donc les données de démonstration ne changent jamais.
 */
public final class ConnexionAnnulable implements FournisseurConnexion, AutoCloseable {

    private static final Set<String> IGNOREES = Set.of("close", "commit", "rollback", "setAutoCommit");

    private final Connection reelle;
    private final Connection protegee;

    public ConnexionAnnulable(Connection reelle) throws SQLException {
        this.reelle = reelle;
        reelle.setAutoCommit(false);
        this.protegee = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, methode, args) -> {
                    if (IGNOREES.contains(methode.getName())) {
                        return null;
                    }
                    try {
                        return methode.invoke(reelle, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    public Connection connexion() {
        return protegee;
    }

    @Override
    public void close() throws SQLException {
        reelle.rollback();
        reelle.close();
    }
}
