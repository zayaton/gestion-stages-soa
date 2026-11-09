package ma.inpt.stages.portail;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import org.glassfish.jersey.server.ResourceConfig;

/** Point d'entrée : Portail et passerelle. */
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
        ServeurRest serveur = new ServeurRest(hote, port, new ResourceConfig(), sante, null);
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
