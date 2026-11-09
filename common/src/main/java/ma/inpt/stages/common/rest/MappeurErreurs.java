package ma.inpt.stages.common.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.List;
import java.util.stream.Collectors;
import ma.inpt.stages.common.ErreurMetier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforme toute exception en réponse JSON {code, message, details, timestamp, path}.
 * Aucune trace de pile ni message technique ne sort du service : ils restent dans les journaux.
 */
public final class MappeurErreurs implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(MappeurErreurs.class);

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(Throwable e) {
        String chemin = uri == null ? "" : "/" + uri.getPath();
        if (e instanceof ErreurMetier m) {
            return reponse(statut(m.type()), ErreurApi.de(m.code(), m.getMessage(), m.details(), chemin));
        }
        if (e instanceof JsonProcessingException j) {
            return reponse(400, ErreurApi.de("REQUETE_INVALIDE", "Le corps de la requête n'est pas un JSON valide.", List.of(detailJson(j)), chemin));
        }
        if (e instanceof NotFoundException) {
            return reponse(404, ErreurApi.de("RESSOURCE_INTROUVABLE", "Aucune ressource ne correspond à cette adresse.", List.of(), chemin));
        }
        if (e instanceof NotAllowedException n) {
            String permis = String.join(", ", n.getResponse().getAllowedMethods());
            return reponse(405, ErreurApi.de("METHODE_NON_AUTORISEE", "Cette méthode HTTP n'est pas acceptée ici.", List.of("Méthodes acceptées : " + permis), chemin));
        }
        if (e instanceof NotSupportedException) {
            return reponse(415, ErreurApi.de("FORMAT_NON_SUPPORTE", "Le corps de la requête doit être en JSON (Content-Type: application/json).", List.of(), chemin));
        }
        if (e instanceof NotAcceptableException) {
            return reponse(406, ErreurApi.de("FORMAT_NON_ACCEPTABLE", "Ce service répond uniquement en JSON.", List.of(), chemin));
        }
        if (e instanceof WebApplicationException w && w.getResponse().getStatus() < 500) {
            // Exemple : /stages/abc, dont l'identifiant n'est pas un nombre, donne 404 selon JAX-RS.
            int code = w.getResponse().getStatus();
            if (code == 404) {
                return reponse(404, ErreurApi.de("RESSOURCE_INTROUVABLE", "Aucune ressource ne correspond à cette adresse.", List.of(), chemin));
            }
            return reponse(code, ErreurApi.de("REQUETE_INVALIDE", "La requête est invalide.", List.of(), chemin));
        }
        LOG.error("Erreur inattendue sur {}", chemin, e);
        return reponse(500, ErreurApi.de("ERREUR_INTERNE", "Une erreur interne est survenue.", List.of(), chemin));
    }

    static int statut(ErreurMetier.Type type) {
        return switch (type) {
            case INTROUVABLE -> 404;
            case REQUETE_INVALIDE -> 400;
            case DONNEES_INVALIDES -> 422;
            case NON_AUTHENTIFIE -> 401;
            case INTERDIT -> 403;
            case CONFLIT -> 409;
            case INDISPONIBLE -> 503;
        };
    }

    /** Message utile au client, sans exposer de nom de classe Java. */
    private static String detailJson(JsonProcessingException e) {
        if (e instanceof UnrecognizedPropertyException u) {
            return "Champ inconnu : " + u.getPropertyName() + ".";
        }
        if (e instanceof InvalidFormatException f) {
            return "Valeur invalide pour le champ " + champ(f) + " : " + f.getValue() + ".";
        }
        if (e instanceof JsonMappingException m && !m.getPath().isEmpty()) {
            return "Valeur absente ou invalide pour le champ " + champ(m) + ".";
        }
        return "Syntaxe JSON incorrecte.";
    }

    private static String champ(JsonMappingException e) {
        return e.getPath().stream().map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    static Response reponse(int statut, ErreurApi erreur) {
        return Response.status(statut).type(MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8")).entity(erreur).build();
    }
}
