package ma.inpt.stages.portail;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;

/**
 * Sert l'interface (dossier public/ du jar). Les routes /api/... plus précises passent toujours avant celle-ci.
 * Une adresse inconnue de l'interface renvoie index.html : la navigation reste gérée par la page.
 */
@Path("/")
public final class RessourceFichiers {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "woff2", "font/woff2", "bpmn", "application/xml; charset=utf-8",
            "json", "application/json; charset=utf-8", "ico", "image/x-icon");

    @GET
    public Response accueil() throws IOException {
        return fichier("index.html");
    }

    @GET
    @Path("{chemin: .+}")
    public Response fichier(@PathParam("chemin") String chemin) throws IOException {
        if (chemin.startsWith("api/") || chemin.equals("api")) {
            throw ErreurMetier.introuvable("RESSOURCE_INTROUVABLE", "Aucune ressource ne correspond à cette adresse.");
        }
        // Pas de remontée hors du dossier public/.
        if (chemin.contains("..") || chemin.contains("\\") || chemin.startsWith("/")) {
            throw ErreurMetier.introuvable("RESSOURCE_INTROUVABLE", "Aucune ressource ne correspond à cette adresse.");
        }
        int point = chemin.lastIndexOf('.');
        String extension = point < 0 ? "" : chemin.substring(point + 1).toLowerCase();
        InputStream flux = RessourceFichiers.class.getClassLoader().getResourceAsStream("public/" + chemin);
        if (flux == null) {
            if (!extension.isEmpty()) {
                throw ErreurMetier.introuvable("RESSOURCE_INTROUVABLE", "Fichier introuvable : " + chemin);
            }
            flux = RessourceFichiers.class.getClassLoader().getResourceAsStream("public/index.html");
            extension = "html";
        }
        try (InputStream f = flux) {
            return Response.ok(f.readAllBytes()).type(TYPES.getOrDefault(extension, "application/octet-stream"))
                    .header("Cache-Control", "no-cache").header("X-Content-Type-Options", "nosniff").build();
        }
    }
}
