package ma.inpt.stages.notifications;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.rest.ClesApi;

/** Point d'entrée : S5 Notifications. */
public final class ServiceNotificationsMain {

    private static final String NOM = "S5 Notifications";

    private ServiceNotificationsMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("notifications.port");
        ZoneId fuseau = ZoneId.of(config.valeur("soa.fuseau"));
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "notifications");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServiceNotifications service = new ServiceNotifications(new NotificationDaoJdbc(base), new Expediteur.Smtp(config),
                new SourcesRappels.Distantes(config), Clock.system(fuseau));
        ServeurRest serveur = new ServeurRest(hote, port, RessourceNotifications.configuration(service), sante,
                new ClesApi(config.valeur("soa.api.cles")));
        PlanificateurRappels planificateur = new PlanificateurRappels(service, LocalTime.parse(config.valeur("notifications.rappels.heure")),
                fuseau, config.entier("notifications.rappels.jours"));
        Lanceur.arretPropre(planificateur, serveur, base);
        serveur.demarrer();
        if (Boolean.parseBoolean(config.valeur("notifications.rappels.actif"))) {
            planificateur.demarrer();
        }
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
