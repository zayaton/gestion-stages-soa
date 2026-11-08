package ma.inpt.stages.offres;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;

/** Point d'entrée : S3 Gestion des offres de stage. */
public final class ServiceOffresMain {

    private static final String NOM = "S3 Gestion des offres de stage";

    private ServiceOffresMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("offres.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "offres");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServeurSoap serveur = new ServeurSoap(hote, port, sante);
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
