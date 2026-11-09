package ma.inpt.stages.portail;

import ma.inpt.stages.portail.securite.FiltreSession;
import ma.inpt.stages.portail.securite.ServiceAuthentification;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/** Assemblage du portail : ressources (une instance par requête), sécurité par session et services partagés. */
public final class Portail {

    private Portail() {
    }

    public static ResourceConfig configuration(ServiceAuthentification auth, Services services, RessourceResponsable.AdressesSante sante) {
        return new ResourceConfig()
                .register(RessourceAuth.class)
                .register(RessourceEtudiant.class)
                .register(RessourceEntreprise.class)
                .register(RessourceResponsable.class)
                .register(RessourceFichiers.class)
                .register(new FiltreSession(auth))
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(auth).to(ServiceAuthentification.class);
                        bind(services).to(Services.class);
                        bind(sante).to(RessourceResponsable.AdressesSante.class);
                    }
                });
    }
}
