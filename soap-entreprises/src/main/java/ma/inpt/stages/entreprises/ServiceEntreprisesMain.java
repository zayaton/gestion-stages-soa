package ma.inpt.stages.entreprises;

import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.ComptesService;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;

/** Point d'entrée : S2 Gestion des entreprises. */
public final class ServiceEntreprisesMain {

    private static final String NOM = "S2 Gestion des entreprises";

    private ServiceEntreprisesMain() {
    }

    public static void main(String[] args) throws Exception {
        Configuration config = Configuration.charger();
        String hote = config.valeur("soa.ecoute.hote");
        int port = config.entier("entreprises.port");
        BaseDeDonnees base = BaseDeDonnees.depuis(config, "entreprises");
        VerificateurSante sante = new VerificateurSante(NOM, base::estDisponible);
        ServeurSoap serveur = new ServeurSoap(hote, port, sante);
        ServiceEntreprises service = new ServiceEntreprises(new EntrepriseDaoJdbc(base), new ClientOffresSoap(config));
        serveur.publier("/ws/entreprises", new EntreprisesEndpoint(service), new ComptesService(config.valeur("soa.ws.comptes")));
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }
}
