package ma.inpt.stages.notifications;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import org.glassfish.jersey.server.ResourceConfig;

/** Point d'entrée : S5 Notifications. */
public final class ServiceNotificationsMain {

    private static final String NOM = "S5 Notifications";

    private ServiceNotificationsMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("notifications.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "notifications");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServeurRest serveur = new ServeurRest(hote, port, new ResourceConfig(), sante);
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
