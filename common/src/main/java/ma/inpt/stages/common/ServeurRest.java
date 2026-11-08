package ma.inpt.stages.common;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/** Serveur Jersey sur Grizzly embarqué, avec GET /health déjà enregistré. */
public final class ServeurRest implements AutoCloseable {

    private final HttpServer serveur;

    public ServeurRest(String hote, int port, ResourceConfig ressources, VerificateurSante sante) throws IOException {
        ressources.register(RessourceSante.class);
        ressources.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(sante).to(VerificateurSante.class);
            }
        });
        serveur = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://" + hote + ":" + port + "/"), ressources, false);
    }

    public void demarrer() throws IOException {
        serveur.start();
    }

    @Override
    public void close() {
        serveur.shutdownNow();
    }

    @Path("/health")
    public static final class RessourceSante {

        private final VerificateurSante sante;

        @Inject
        public RessourceSante(VerificateurSante sante) {
            this.sante = sante;
        }

        @GET
        @Produces("application/json")
        public Response verifier() {
            VerificateurSante.Etat etat = sante.verifier();
            return Response.status(etat.codeHttp()).type("application/json; charset=utf-8").entity(etat.json()).build();
        }
    }
}
