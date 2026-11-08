package ma.inpt.stages.candidatures;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import org.glassfish.jersey.server.ResourceConfig;

/** Point d'entrée : S4 Gestion des candidatures. */
public final class ServiceCandidaturesMain {

    private static final String NOM = "S4 Gestion des candidatures";

    private ServiceCandidaturesMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("candidatures.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "candidatures");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServeurRest serveur = new ServeurRest(hote, port, new ResourceConfig(), sante);
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
