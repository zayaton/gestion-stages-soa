package ma.inpt.stages.common.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Correlation;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrai serveur Grizzly : codes HTTP, corps d'erreur uniforme, clés d'API, corrélation, documentation. */
class SocleRestTest {

    private static final String CLE_PORTAIL = "cle-portail-2026-7Hq2Vd";
    private static final String CLE_BONITA = "cle-bonita-2026-Xp4MzN";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    public record Saisie(String nom, int age) {
    }

    @Path(ServeurRest.API + "/essais")
    @Produces(MediaType.APPLICATION_JSON)
    public static class RessourceEssai {

        @GET
        @Path("{cas}")
        public Map<String, String> cas(@PathParam("cas") String cas) {
            switch (cas) {
                case "introuvable" -> throw ErreurMetier.introuvable("X_INTROUVABLE", "Rien ici.");
                case "invalide" -> throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Invalide.", List.of("Le nom est obligatoire."));
                case "requete" -> throw ErreurMetier.requeteInvalide("Mal formée.", List.of());
                case "conflit" -> throw ErreurMetier.conflit("TRANSITION_INTERDITE", "Conflit.");
                case "interdit" -> throw ErreurMetier.interdit("PAS_LE_DROIT", "Interdit.");
                case "panne" -> throw ErreurMetier.indisponible("BASE_INDISPONIBLE", "Panne.");
                case "bug" -> throw new IllegalStateException("détail technique secret");
                default -> {
                    return Map.of("correlation", String.valueOf(Correlation.courant()));
                }
            }
        }

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public Saisie echo(Saisie saisie) {
            return saisie;
        }

        @GET
        @Path("reserve/bonita")
        @ClientsAutorises("bonita")
        public Map<String, String> reserve() {
            return Map.of("ok", "oui");
        }
    }

    private ServeurRest serveur;
    private String base;

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        ResourceConfig config = new ResourceConfig().register(RessourceEssai.class);
        serveur = new ServeurRest("127.0.0.1", port, config, new VerificateurSante("Essai", () -> true),
                new ClesApi(Configuration.charger().valeur("soa.api.cles")));
        serveur.demarrer();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> appel(String methode, String chemin, String cle, String corps, String type) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin));
        if (cle != null) {
            r.header("X-API-Key", cle);
        }
        if (type != null) {
            r.header("Content-Type", type);
        }
        r.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String chemin) throws Exception {
        return appel("GET", chemin, CLE_PORTAIL, null, null);
    }

    private static JsonNode json(HttpResponse<String> r) throws IOException {
        return Json.MAPPEUR.readTree(r.body());
    }

    private void erreurUniforme(HttpResponse<String> r, int statut, String code) throws IOException {
        assertEquals(statut, r.statusCode(), r.body());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), r.headers().toString());
        JsonNode e = json(r);
        assertEquals(code, e.path("code").asText(), r.body());
        assertFalse(e.path("message").asText().isBlank());
        assertTrue(e.path("details").isArray());
        assertTrue(e.path("timestamp").asText().matches("\\d{4}-\\d{2}-\\d{2}T.*"), r.body());
        assertTrue(e.path("path").asText().startsWith("/"), r.body());
    }

    // Sécurité

    @Test
    void sansCleLaRequeteEstRefuseeEn401() throws Exception {
        HttpResponse<String> r = appel("GET", "/api/v1/essais/ok", null, null, null);
        erreurUniforme(r, 401, "NON_AUTHENTIFIE");
        assertTrue(json(r).path("message").asText().contains("absent"));
        assertTrue(r.headers().firstValue("WWW-Authenticate").isPresent());
    }

    @Test
    void uneCleInconnueEstRefuseeEn401() throws Exception {
        HttpResponse<String> r = appel("GET", "/api/v1/essais/ok", "cle-inventee", null, null);
        erreurUniforme(r, 401, "NON_AUTHENTIFIE");
        assertTrue(json(r).path("message").asText().contains("invalide"));
    }

    @Test
    void unClientNonAutoriseEstRefuseEn403() throws Exception {
        erreurUniforme(get("/api/v1/essais/reserve/bonita"), 403, "ACCES_INTERDIT");
        assertEquals(200, appel("GET", "/api/v1/essais/reserve/bonita", CLE_BONITA, null, null).statusCode());
    }

    @Test
    void santeEtDocumentationSontPubliques() throws Exception {
        assertEquals(200, appel("GET", "/health", null, null, null).statusCode());
        HttpResponse<String> contrat = appel("GET", "/openapi.yaml", null, null, null);
        assertEquals(200, contrat.statusCode());
        assertTrue(contrat.body().contains("openapi: 3.0.3"));
        HttpResponse<String> ui = appel("GET", "/docs/", null, null, null);
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("swagger-ui"), "Swagger UI doit être servi depuis le jar");
        HttpResponse<String> init = appel("GET", "/docs/swagger-initializer.js", null, null, null);
        assertTrue(init.body().contains("/openapi.yaml"), "notre initialiseur doit remplacer celui de Swagger UI");
        assertTrue(appel("GET", "/docs", null, null, null).body().contains("swagger-ui"));
    }

    @Test
    void lesClesLivreesCorrespondentAuxClients() {
        ClesApi cles = new ClesApi(Configuration.charger().valeur("soa.api.cles"));
        assertEquals("portail", cles.client(CLE_PORTAIL).orElseThrow());
        assertEquals("bonita", cles.client(CLE_BONITA).orElseThrow());
        assertEquals("services", cles.client(Configuration.charger().valeur("soa.api.cle.client")).orElseThrow());
        assertEquals("postman", cles.client("cle-postman-2026-Rb3WsE").orElseThrow());
        assertTrue(cles.client(null).isEmpty());
        assertTrue(cles.client(" ").isEmpty());
    }

    // Codes HTTP et corps d'erreur

    @Test
    void chaqueErreurMetierALeBonCodeHttp() throws Exception {
        erreurUniforme(get("/api/v1/essais/introuvable"), 404, "X_INTROUVABLE");
        erreurUniforme(get("/api/v1/essais/requete"), 400, "REQUETE_INVALIDE");
        erreurUniforme(get("/api/v1/essais/invalide"), 422, "DONNEES_INVALIDES");
        erreurUniforme(get("/api/v1/essais/interdit"), 403, "PAS_LE_DROIT");
        erreurUniforme(get("/api/v1/essais/conflit"), 409, "TRANSITION_INTERDITE");
        erreurUniforme(get("/api/v1/essais/panne"), 503, "BASE_INDISPONIBLE");
        assertEquals("Le nom est obligatoire.", json(get("/api/v1/essais/invalide")).path("details").get(0).asText());
    }

    @Test
    void uneErreurInattendueDonne500SansDetailTechnique() throws Exception {
        HttpResponse<String> r = get("/api/v1/essais/bug");
        erreurUniforme(r, 500, "ERREUR_INTERNE");
        assertFalse(r.body().contains("secret"));
        assertFalse(r.body().contains("IllegalStateException"));
    }

    @Test
    void erreursDeRoutageEnJson() throws Exception {
        erreurUniforme(get("/api/v1/inconnu"), 404, "RESSOURCE_INTROUVABLE");
        erreurUniforme(appel("DELETE", "/api/v1/essais", CLE_PORTAIL, null, null), 405, "METHODE_NON_AUTORISEE");
        erreurUniforme(appel("POST", "/api/v1/essais", CLE_PORTAIL, "nom=x", "text/plain"), 415, "FORMAT_NON_SUPPORTE");
    }

    @Test
    void unJsonIncorrectDonne400AvecUnDetailLisible() throws Exception {
        HttpResponse<String> syntaxe = appel("POST", "/api/v1/essais", CLE_PORTAIL, "{\"nom\":", "application/json");
        erreurUniforme(syntaxe, 400, "REQUETE_INVALIDE");
        HttpResponse<String> inconnu = appel("POST", "/api/v1/essais", CLE_PORTAIL, "{\"nom\":\"a\",\"age\":3,\"agee\":3}", "application/json");
        erreurUniforme(inconnu, 400, "REQUETE_INVALIDE");
        assertTrue(json(inconnu).path("details").get(0).asText().contains("agee"), inconnu.body());
        HttpResponse<String> type = appel("POST", "/api/v1/essais", CLE_PORTAIL, "{\"nom\":\"a\",\"age\":\"vingt\"}", "application/json");
        erreurUniforme(type, 400, "REQUETE_INVALIDE");
        assertTrue(json(type).path("details").get(0).asText().contains("age"), type.body());
        assertFalse(type.body().contains("java."), type.body());
        HttpResponse<String> manquant = appel("POST", "/api/v1/essais", CLE_PORTAIL, "{\"nom\":\"a\"}", "application/json");
        erreurUniforme(manquant, 400, "REQUETE_INVALIDE");
        assertTrue(json(manquant).path("details").get(0).asText().contains("absente ou invalide pour le champ age"), manquant.body());
    }

    @Test
    void unJsonCorrectEstAccepteAvecSesAccents() throws Exception {
        HttpResponse<String> r = appel("POST", "/api/v1/essais", CLE_PORTAIL, "{\"nom\":\"Aïcha\",\"age\":21}", "application/json; charset=utf-8");
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("Aïcha", json(r).path("nom").asText());
    }

    // Corrélation

    @Test
    void laCorrelationRecueEstUtiliseeEtRenvoyee() throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/v1/essais/ok"))
                .header("X-API-Key", CLE_PORTAIL).header("X-Correlation-Id", "dossier-42").build();
        HttpResponse<String> reponse = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        assertEquals("dossier-42", json(reponse).path("correlation").asText());
        assertEquals("dossier-42", reponse.headers().firstValue("X-Correlation-Id").orElse(""));
    }

    @Test
    void sansCorrelationUnIdentifiantEstCreeMemeEnErreur() throws Exception {
        assertFalse(get("/api/v1/essais/ok").headers().firstValue("X-Correlation-Id").orElse("").isBlank());
        assertFalse(appel("GET", "/api/v1/essais/ok", null, null, null).headers().firstValue("X-Correlation-Id").orElse("").isBlank());
    }

    // Client REST

    @Test
    void leClientRestTransmetCleEtCorrelation() {
        ClientRest client = new ClientRest(base + "/api/v1", CLE_PORTAIL, "essais", Configuration.charger());
        Correlation.definir("trace-7");
        try {
            ClientRest.Reponse r = client.get("/essais/ok");
            assertTrue(r.succes());
            assertEquals("trace-7", r.corps().path("correlation").asText());
            ClientRest.Reponse conflit = client.get("/essais/conflit");
            assertEquals(409, conflit.statut());
            assertEquals("TRANSITION_INTERDITE", conflit.code());
        } finally {
            Correlation.effacer();
        }
    }

    @Test
    void leClientRestTraduitUnePanneEnIndisponibiliteEtGardeLaCauseDUn503() throws Exception {
        ClientRest client = new ClientRest(base + "/api/v1", CLE_PORTAIL, "essais", Configuration.charger());
        ClientRest.Reponse cause = client.get("/essais/panne");
        assertEquals(503, cause.statut(), "un 503 structuré garde sa cause réelle");
        assertEquals("BASE_INDISPONIBLE", cause.code());
        int libre;
        try (ServerSocket s = new ServerSocket(0)) {
            libre = s.getLocalPort();
        }
        ClientRest arrete = new ClientRest("http://127.0.0.1:" + libre + "/api/v1", CLE_PORTAIL, "suivi", Configuration.charger());
        ErreurMetier e = assertTimeout(Duration.ofSeconds(10), () -> assertThrows(ErreurMetier.class, () -> arrete.get("/stages/1")));
        assertEquals(ErreurMetier.Type.INDISPONIBLE, e.type());
        assertEquals("SERVICE_SUIVI_INDISPONIBLE", e.code());
    }
}
