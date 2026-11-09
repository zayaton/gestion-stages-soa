package ma.inpt.stages.suivi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.rest.ClesApi;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.common.rest.VerificationContrat;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrais échanges HTTP avec le service S6 (DAO en mémoire) et cohérence avec le contrat OpenAPI. */
class RessourceStagesTest {

    private static final String CLE = "cle-postman-2026-Rb3WsE";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private final StageDaoMemoire dao = new StageDaoMemoire();
    private ServeurRest serveur;
    private String base;

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        ResourceConfig config = RessourceStages.configuration(new ServiceSuivi(dao));
        serveur = new ServeurRest("127.0.0.1", port, config, new VerificateurSante("S6", () -> true),
                new ClesApi(Configuration.charger().valeur("soa.api.cles")));
        serveur.demarrer();
        base = "http://127.0.0.1:" + port + "/api/v1";
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> appel(String methode, String chemin, String corps, Map<String, String> entetes) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin)).header("Content-Type", "application/json");
        entetes.forEach(r::header);
        r.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> appel(String methode, String chemin, String corps) throws Exception {
        return appel(methode, chemin, corps, Map.of("X-API-Key", CLE));
    }

    private static JsonNode json(HttpResponse<String> r) throws IOException {
        return Json.MAPPEUR.readTree(r.body());
    }

    private static final String NOUVEAU = "{\"candidatureId\":21,\"etudiantId\":1,\"offreId\":1,\"entrepriseId\":1,"
            + "\"dateDebut\":\"2027-02-01\",\"dateFin\":\"2027-05-01\",\"encadrantPedagogique\":\"Pr. Essaadi\"}";

    @Test
    void parcoursCompletDUnStage() throws Exception {
        HttpResponse<String> cree = appel("POST", "/stages", NOUVEAU, Map.of("X-API-Key", CLE, "X-Acteur", "processus"));
        assertEquals(201, cree.statusCode(), cree.body());
        long id = json(cree).path("id").asLong();
        assertTrue(cree.headers().firstValue("Location").orElse("").endsWith("/api/v1/stages/" + id));
        assertEquals("PLANIFIE", json(cree).path("statut").asText());
        assertEquals("2027-02-01", json(cree).path("dateDebut").asText());

        HttpResponse<String> rejoue = appel("POST", "/stages", NOUVEAU);
        assertEquals(200, rejoue.statusCode(), "rejouer l'étape 9 renvoie le stage existant");
        assertEquals(id, json(rejoue).path("id").asLong());

        assertEquals(409, appel("POST", "/stages/" + id + "/comptes-rendus", "{\"periode\":\"S1\",\"contenu\":\"x\",\"auteur\":\"y\"}").statusCode());
        assertEquals("EN_COURS", json(appel("PATCH", "/stages/" + id + "/statut", "{\"statut\":\"EN_COURS\"}")).path("statut").asText());
        assertEquals(201, appel("POST", "/stages/" + id + "/comptes-rendus", "{\"periode\":\"Semaines 1 à 4\",\"contenu\":\"Ateliers.\",\"auteur\":\"Pr. Essaadi\"}").statusCode());
        HttpResponse<String> note = appel("POST", "/stages/" + id + "/evaluations", "{\"note\":15.5,\"commentaire\":\"Sérieux\",\"evaluateur\":\"Pr. Essaadi\"}");
        assertEquals(201, note.statusCode(), note.body());
        assertEquals(15.5, json(note).path("note").asDouble());

        assertEquals("Semaines 1 à 4", json(appel("GET", "/stages/" + id + "/comptes-rendus", null)).get(0).path("periode").asText());
        assertEquals(1, json(appel("GET", "/stages/" + id + "/evaluations", null)).size());
        JsonNode historique = json(appel("GET", "/stages/" + id + "/historique", null));
        assertEquals(4, historique.size(), historique.toString());
        assertEquals("processus", historique.get(0).path("acteur").asText());
        assertEquals("client:postman", historique.get(1).path("acteur").asText());

        assertEquals(1, json(appel("GET", "/etudiants/1/stages", null)).size());
        assertEquals(id, json(appel("GET", "/candidatures/21/stage", null)).path("id").asLong());
        assertEquals(1, json(appel("GET", "/stages?statut=EN_COURS", null)).size());
        assertEquals(0, json(appel("GET", "/stages?statut=TERMINE", null)).size());
    }

    @Test
    void codesDErreur() throws Exception {
        assertEquals(401, appel("GET", "/stages", null, Map.of()).statusCode());
        assertEquals(404, appel("GET", "/stages/99", null).statusCode());
        assertEquals("STAGE_INTROUVABLE", json(appel("GET", "/stages/99", null)).path("code").asText());
        assertEquals(400, appel("GET", "/stages?statut=FINI", null).statusCode());
        assertEquals(400, appel("POST", "/stages", "{\"candidatureId\":\"abc\"}").statusCode());
        assertEquals(400, appel("POST", "/stages", "").statusCode(), "corps absent");
        HttpResponse<String> invalide = appel("POST", "/stages", "{\"candidatureId\":21}");
        assertEquals(422, invalide.statusCode());
        assertEquals(5, json(invalide).path("details").size(), invalide.body());
        long id = dao.avecStatut("TERMINE").id();
        HttpResponse<String> conflit = appel("PATCH", "/stages/" + id + "/statut", "{\"statut\":\"EN_COURS\"}");
        assertEquals(409, conflit.statusCode());
        assertEquals("TRANSITION_INTERDITE", json(conflit).path("code").asText());
        assertEquals(422, appel("POST", "/stages/" + id + "/evaluations", "{\"note\":25,\"evaluateur\":\"x\"}").statusCode());
        // JAX-RS : un paramètre de chemin non convertible désigne une ressource inexistante.
        assertEquals("RESSOURCE_INTROUVABLE", json(appel("GET", "/stages/abc", null)).path("code").asText());
    }

    /** Toute route exposée doit être documentée, et toute route documentée doit exister. */
    @Test
    void leContratOpenApiCorrespondAuCode() throws Exception {
        Set<String> documentees = new TreeSet<>();
        try (InputStream flux = getClass().getClassLoader().getResourceAsStream("openapi.yaml")) {
            JsonNode contrat = new ObjectMapper(new YAMLFactory()).readTree(flux);
            Iterator<Map.Entry<String, JsonNode>> chemins = contrat.path("paths").fields();
            while (chemins.hasNext()) {
                Map.Entry<String, JsonNode> chemin = chemins.next();
                chemin.getValue().fieldNames().forEachRemaining(m -> {
                    if (Set.of("get", "post", "put", "patch", "delete").contains(m)) {
                        documentees.add(m.toUpperCase() + " " + chemin.getKey());
                    }
                });
            }
        }
        assertEquals(VerificationContrat.operations(RessourceStages.class), documentees);
    }
}
