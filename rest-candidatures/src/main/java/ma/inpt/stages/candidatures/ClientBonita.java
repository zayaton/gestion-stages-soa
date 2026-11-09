package ma.inpt.stages.candidatures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client de l'API REST de Bonita : connexion (loginservice, jeton X-Bonita-API-Token), démarrage d'un cas avec le contrat
 * du processus, exécution de la tâche humaine « Décision de l'entreprise », suppression d'un cas.
 */
public class ClientBonita {

    private static final Logger LOG = LoggerFactory.getLogger(ClientBonita.class);
    private static final String JETON = "X-Bonita-API-Token";

    private final String base;
    private final String utilisateur;
    private final String motDePasse;
    private final String processus;
    private final String version;
    private final String tacheDecision;
    private final Duration delai;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    private String jeton;

    public ClientBonita(Configuration config) {
        this(config.valeur("bonita.url"), config.valeur("bonita.utilisateur"), config.valeur("bonita.motdepasse"),
                config.valeur("bonita.processus.nom"), config.valeur("bonita.processus.version"), config.valeur("bonita.tache.decision"),
                Duration.ofMillis(config.entier("soa.rest.delai.lecture.ms")));
    }

    public ClientBonita(String base, String utilisateur, String motDePasse, String processus, String version, String tacheDecision, Duration delai) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.utilisateur = utilisateur;
        this.motDePasse = motDePasse;
        this.processus = processus;
        this.version = version;
        this.tacheDecision = tacheDecision;
        this.delai = delai;
        // HTTP/1.1 : Tomcat (Bonita) comme Grizzly, sans tentative de passage en h2c.
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    static ErreurMetier indisponible(String detail) {
        return ErreurMetier.indisponible("BONITA_INDISPONIBLE", "Le moteur de processus Bonita est indisponible : " + detail);
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** Ouvre une session Bonita et retient le jeton anti-CSRF exigé par l'API. */
    synchronized void connecter() {
        String formulaire = "username=" + encode(utilisateur) + "&password=" + encode(motDePasse) + "&redirect=false";
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/loginservice")).timeout(delai)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulaire)).build();
        try {
            HttpResponse<String> reponse = http.send(r, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() >= 300) {
                throw indisponible("connexion refusée pour " + utilisateur + " (HTTP " + reponse.statusCode() + ").");
            }
        } catch (IOException e) {
            throw indisponible("pas de réponse à " + base + ". Bonita Studio est-il démarré ?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw indisponible("connexion interrompue.");
        }
        jeton = cookies.getCookieStore().getCookies().stream().filter(c -> JETON.equals(c.getName())).map(HttpCookie::getValue)
                .findFirst().orElse(null);
        if (jeton == null) {
            throw indisponible("la connexion n'a pas fourni de jeton " + JETON + ".");
        }
        LOG.info("Session Bonita ouverte pour {}", utilisateur);
    }

    /** Appel de l'API ; une session expirée (401) est rouverte une fois. */
    JsonNode api(String methode, String chemin, Object corps) {
        if (jeton == null) {
            connecter();
        }
        HttpResponse<String> r = envoyer(methode, chemin, corps);
        if (r.statusCode() == 401) {
            connecter();
            r = envoyer(methode, chemin, corps);
        }
        if (r.statusCode() >= 300) {
            throw indisponible(methode + " " + chemin + " a répondu HTTP " + r.statusCode() + " " + abrege(r.body()));
        }
        try {
            return r.body() == null || r.body().isBlank() ? NullNode.getInstance() : Json.MAPPEUR.readTree(r.body());
        } catch (IOException e) {
            throw indisponible("réponse illisible de " + chemin + ".");
        }
    }

    private static String abrege(String texte) {
        if (texte == null) {
            return "";
        }
        return texte.length() > 200 ? texte.substring(0, 200) + "…" : texte;
    }

    private HttpResponse<String> envoyer(String methode, String chemin, Object corps) {
        try {
            HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin)).timeout(delai).header(JETON, jeton)
                    .header("Accept", "application/json");
            if (corps == null) {
                r.method(methode, HttpRequest.BodyPublishers.noBody());
            } else {
                r.header("Content-Type", "application/json; charset=utf-8")
                        .method(methode, HttpRequest.BodyPublishers.ofString(Json.MAPPEUR.writeValueAsString(corps), StandardCharsets.UTF_8));
            }
            return http.send(r.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw indisponible("pas de réponse à " + base + ". Bonita Studio est-il démarré ?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw indisponible("appel interrompu.");
        }
    }

    /** Identifiant de la définition de processus déployée (nom et version configurés). */
    long processusId() {
        JsonNode liste = api("GET", "/API/bpm/process?p=0&c=1&f=" + encode("name=" + processus) + "&f=" + encode("version=" + version), null);
        if (!liste.isArray() || liste.isEmpty()) {
            throw ErreurMetier.indisponible("BONITA_PROCESSUS_ABSENT", "Le processus « " + processus + " » version " + version
                    + " n'est pas déployé dans Bonita : déployez-le depuis Bonita Studio (docs/bonita/GUIDE_BONITA.md).");
        }
        return liste.get(0).path("id").asLong();
    }

    /** Démarre un cas : le contrat d'instanciation du processus reçoit ces valeurs. Renvoie le numéro du cas. */
    public long demarrer(Map<String, Object> contrat) {
        JsonNode r = api("POST", "/API/bpm/process/" + processusId() + "/instantiation", contrat);
        long cas = r.path("caseId").asLong(0);
        if (cas == 0) {
            throw indisponible("le démarrage du cas n'a pas renvoyé de numéro.");
        }
        return cas;
    }

    /** Tâche « Décision de l'entreprise » prête pour ce cas, ou null s'il n'y en a pas (le processus n'y est pas encore). */
    public Long tacheDecision(long cas) {
        JsonNode taches = api("GET", "/API/bpm/humanTask?p=0&c=20&f=" + encode("caseId=" + cas) + "&f=" + encode("state=ready"), null);
        for (JsonNode t : taches) {
            if (tacheDecision.equals(t.path("name").asText()) || tacheDecision.equals(t.path("displayName").asText())) {
                return t.path("id").asLong();
            }
        }
        return null;
    }

    /** Assigne la tâche au compte technique puis l'exécute avec le contrat {decision, commentaire}. */
    public void executer(long tache, Map<String, Object> contrat) {
        String moi = api("GET", "/API/system/session/unusedid", null).path("user_id").asText();
        api("PUT", "/API/bpm/humanTask/" + tache, Map.of("assigned_id", moi));
        api("POST", "/API/bpm/userTask/" + tache + "/execution", contrat);
    }

    /** Supprime le cas (annulation de la candidature par l'étudiant). */
    public void supprimerCas(long cas) {
        api("DELETE", "/API/bpm/case/" + cas, null);
    }
}
