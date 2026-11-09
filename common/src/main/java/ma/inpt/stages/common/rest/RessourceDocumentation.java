package ma.inpt.stages.common.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

/** Sert le contrat OpenAPI du service (fichier openapi.yaml du module) ; Swagger UI est servi à /docs par Grizzly. */
@Path("/")
public final class RessourceDocumentation {

    @GET
    @Path("openapi.yaml")
    @Produces("application/yaml; charset=utf-8")
    public Response contrat() throws IOException {
        try (InputStream flux = RessourceDocumentation.class.getClassLoader().getResourceAsStream("openapi.yaml")) {
            if (flux == null) {
                return Response.status(404).build();
            }
            return Response.ok(flux.readAllBytes()).build();
        }
    }
}
