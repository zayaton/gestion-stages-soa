package ma.inpt.stages.common.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Correlation;
import ma.inpt.stages.common.ErreurMetier;

/**
 * Appels JSON vers un autre service REST : clé d'API, corrélation et délais d'attente.
 * Une panne (service arrêté, délai dépassé, 5xx sans corps d'erreur) devient une indisponibilité ; une réponse d'erreur
 * structurée (4xx, ou 5xx avec {code, message}) est renvoyée telle quelle à l'appelant.
 */
public final class ClientRest {

    /** Réponse brute : l'appelant interprète les codes 4xx selon son métier. */
    public record Reponse(int statut, JsonNode corps) {

        public boolean succes() {
            return statut >= 200 && statut < 300;
        }

        public String code() {
            return corps.path("code").asText("");
        }

        public String message() {
            return corps.path("message").asText("");
        }
    }

    private final String base;
    private final String cle;
    private final String nomService;
    private final Duration delaiLecture;
    // HTTP/1.1 imposé : avec la tentative de passage en h2c, Grizzly répond sans ses en-têtes (voir JOURNAL.md).
    private final HttpClient http;

    public ClientRest(String base, String cle, String nomService, Configuration config) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.cle = cle;
        this.nomService = nomService;
        this.delaiLecture = Duration.ofMillis(config.entier("soa.rest.delai.lecture.ms"));
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(config.entier("soa.rest.delai.connexion.ms"))).build();
    }

    public Reponse get(String chemin) {
        return envoyer("GET", chemin, null);
    }

    public Reponse post(String chemin, Object corps) {
        return envoyer("POST", chemin, corps);
    }

    public Reponse patch(String chemin, Object corps) {
        return envoyer("PATCH", chemin, corps);
    }

    public Reponse delete(String chemin) {
        return envoyer("DELETE", chemin, null);
    }

    /** Appel avec l'auteur de l'opération (en-tête X-Acteur), repris dans l'historique du service appelé. */
    public Reponse appeler(String methode, String chemin, Object corps, String acteur) {
        return envoyer(methode, chemin, corps, acteur);
    }

    private Reponse envoyer(String methode, String chemin, Object corps) {
        return envoyer(methode, chemin, corps, null);
    }

    private Reponse envoyer(String methode, String chemin, Object corps, String acteur) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(base + chemin)).timeout(delaiLecture)
                .header("Accept", "application/json").header(FiltreSecurite.ENTETE, cle);
        if (acteur != null) {
            requete.header(Acteur.ENTETE, acteur);
        }
        String correlation = Correlation.courant();
        if (correlation != null) {
            requete.header(Correlation.ENTETE_HTTP, correlation);
        }
        try {
            if (corps == null) {
                requete.method(methode, HttpRequest.BodyPublishers.noBody());
            } else {
                requete.header("Content-Type", "application/json; charset=utf-8")
                        .method(methode, HttpRequest.BodyPublishers.ofString(Json.MAPPEUR.writeValueAsString(corps), StandardCharsets.UTF_8));
            }
            HttpResponse<String> r = http.send(requete.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = r.body() == null || r.body().isBlank() ? NullNode.getInstance() : Json.MAPPEUR.readTree(r.body());
            // Un 5xx avec notre corps d'erreur garde sa cause réelle (par exemple « S3 indisponible » vue par S4).
            // Sans ce corps, la seule information sûre est que ce service-ci ne répond pas correctement.
            if (r.statusCode() >= 500 && !json.hasNonNull("code")) {
                throw indisponible();
            }
            return new Reponse(r.statusCode(), json);
        } catch (JsonProcessingException e) {
            throw indisponible();
        } catch (IOException e) {
            throw indisponible();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw indisponible();
        }
    }

    private ErreurMetier indisponible() {
        String code = "SERVICE_" + nomService.toUpperCase(java.util.Locale.ROOT) + "_INDISPONIBLE";
        return ErreurMetier.indisponible(code, "Le service " + nomService + " est momentanément indisponible.");
    }
}
