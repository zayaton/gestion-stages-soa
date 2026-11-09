package ma.inpt.stages.common;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import ma.inpt.stages.common.rest.ClesApi;
import ma.inpt.stages.common.rest.FiltreCorrelation;
import ma.inpt.stages.common.rest.FiltreSecurite;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.common.rest.MappeurErreurs;
import ma.inpt.stages.common.rest.RessourceDocumentation;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * Serveur Jersey sur Grizzly embarqué. Enregistre pour chaque service REST : /health, le contrat OpenAPI
 * (/openapi.yaml), Swagger UI (/docs/), les erreurs JSON uniformes, la corrélation et, si des clés sont
 * fournies, le contrôle X-API-Key.
 */
public final class ServeurRest implements AutoCloseable {

    /** Préfixe de toutes les ressources métier. */
    public static final String API = "/api/v1";

    private final HttpServer serveur;

    public ServeurRest(String hote, int port, ResourceConfig ressources, VerificateurSante sante, ClesApi cles) throws IOException {
        ressources.register(RessourceSante.class);
        ressources.register(RessourceDocumentation.class);
        ressources.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(sante).to(VerificateurSante.class);
            }
        });
        ressources.register(JacksonFeature.withoutExceptionMappers());
        ressources.register(Json.class);
        ressources.register(MappeurErreurs.class);
        ressources.register(FiltreCorrelation.class);
        if (cles != null) {
            ressources.register(new FiltreSecurite.Authentification(cles));
            ressources.register(FiltreSecurite.Autorisation.class);
        }
        // Pas de WADL : il exige JAXB et n'apporte rien à côté du contrat OpenAPI.
        ressources.property(ServerProperties.WADL_FEATURE_DISABLE, true);
        serveur = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://" + hote + ":" + port + "/"), ressources, false);
        // Swagger UI servi depuis le jar webjars : aucune ressource n'est chargée depuis Internet.
        // Le dossier swagger/ passe en premier : son swagger-initializer.js pointe vers /openapi.yaml.
        ClassLoader chargeur = ServeurRest.class.getClassLoader();
        serveur.getServerConfiguration().addHttpHandler(
                new CLStaticHttpHandler(chargeur, "swagger/", "META-INF/resources/webjars/swagger-ui/" + versionSwaggerUi(chargeur) + "/"), "/docs/");
    }

    private static String versionSwaggerUi(ClassLoader chargeur) throws IOException {
        try (InputStream flux = chargeur.getResourceAsStream("META-INF/maven/org.webjars/swagger-ui/pom.properties")) {
            if (flux == null) {
                throw new IllegalStateException("Swagger UI est absent du classpath (dépendance org.webjars:swagger-ui).");
            }
            Properties p = new Properties();
            p.load(flux);
            return p.getProperty("version");
        }
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
