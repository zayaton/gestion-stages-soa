package ma.inpt.stages.portail;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.portail.securite.CompteDaoJdbc;
import ma.inpt.stages.portail.securite.ServiceAuthentification;
import org.glassfish.jersey.server.ResourceConfig;

/** Point d'entrée : portail (interface et passerelle vers les six services). */
public final class PortailMain {

    private static final String NOM = "Portail et passerelle";

    private PortailMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("portail.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "portail");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServiceAuthentification auth = new ServiceAuthentification(new CompteDaoJdbc(base),
                Duration.ofHours(config.entier("portail.session.heures")), Clock.system(ZoneId.of(config.valeur("soa.fuseau"))));
        ResourceConfig ressources = Portail.configuration(auth, new Services(config), adressesSante(config));
        // Pas de clé d'API : le portail est appelé par le navigateur, la sécurité passe par la session.
        ServeurRest serveur = new ServeurRest(hote, port, ressources, sante, null);
        ScheduledExecutorService purge = Executors.newSingleThreadScheduledExecutor();
        purge.scheduleAtFixedRate(() -> {
            try {
                auth.purger();
            } catch (RuntimeException e) {
                // Base momentanément indisponible : la purge reprendra au passage suivant.
            }
        }, 1, 60, TimeUnit.MINUTES);
        Lanceur.arretPropre(purge::shutdownNow, serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }

    private static RessourceResponsable.AdressesSante adressesSante(Configuration config) {
        Map<String, String> adresses = new LinkedHashMap<>();
        adresses.put("S1 Étudiants", sante(config.valeur("soa.ws.url.etudiants")));
        adresses.put("S2 Entreprises", sante(config.valeur("soa.ws.url.entreprises")));
        adresses.put("S3 Offres", sante(config.valeur("soa.ws.url.offres")));
        adresses.put("S4 Candidatures", sante(config.valeur("soa.rest.url.candidatures")));
        adresses.put("S5 Notifications", sante(config.valeur("soa.rest.url.notifications")));
        adresses.put("S6 Suivi des stages", sante(config.valeur("soa.rest.url.suivi")));
        return new RessourceResponsable.AdressesSante(adresses);
    }

    private static String sante(String url) {
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getHost() + ":" + u.getPort() + "/health";
    }
}
