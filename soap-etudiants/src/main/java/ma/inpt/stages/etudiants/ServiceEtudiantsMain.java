package ma.inpt.stages.etudiants;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.ComptesService;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;

/** Point d'entrée : S1 Gestion des étudiants. */
public final class ServiceEtudiantsMain {

    private static final String NOM = "S1 Gestion des étudiants";

    private ServiceEtudiantsMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("etudiants.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "etudiants");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServeurSoap serveur = new ServeurSoap(hote, port, sante);
        serveur.publier("/ws/etudiants", new EtudiantsEndpoint(new ServiceEtudiants(new EtudiantDaoJdbc(base))),
                new ComptesService(config.valeur("soa.ws.comptes")));
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
