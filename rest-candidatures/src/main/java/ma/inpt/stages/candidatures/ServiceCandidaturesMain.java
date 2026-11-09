package ma.inpt.stages.candidatures;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Lanceur;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.rest.ClesApi;
import org.slf4j.LoggerFactory;

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
        CandidatureDaoJdbc dao = new CandidatureDaoJdbc(base);
        ServiceCandidatures service = new ServiceCandidatures(dao, config.entier("candidatures.limite.actives"));
        Orchestrateur orchestrateur = orchestrateur(config, service, dao);
        ServeurRest serveur = new ServeurRest(hote, port, RessourceCandidatures.configuration(service, orchestrateur), sante,
                new ClesApi(config.valeur("soa.api.cles")));
        Lanceur.arretPropre(serveur, base);
        serveur.demarrer();
        Lanceur.annoncer(NOM, hote, port);
        Thread.currentThread().join();
    }

    /** ORCHESTRATION_MODE (orchestration.mode) : « direct » ou « bonita ». */
    private static Orchestrateur orchestrateur(Configuration config, ServiceCandidatures service, CandidatureDaoJdbc dao) {
        String mode = config.valeur("orchestration.mode").trim().toLowerCase(Locale.ROOT);
        if ("direct".equals(mode)) {
            LoggerFactory.getLogger(ServiceCandidaturesMain.class).info("Orchestration en mode direct (secours de développement et de test)");
            return new OrchestrateurDirect(service, dao, new PartenairesDistants(config), config.valeur("candidatures.responsable.email"),
                    config.entier("candidatures.stage.debut.jours"), Clock.system(ZoneId.of(config.valeur("soa.fuseau"))));
        }
        throw new IllegalStateException("Mode d'orchestration non pris en charge : « " + mode + " ». Valeur possible : direct.");
    }
}
