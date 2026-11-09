package ma.inpt.stages.candidatures;

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
import java.time.Clock;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrais échanges HTTP avec S4 (base et partenaires simulés) et cohérence avec le contrat OpenAPI. */
class RessourceCandidaturesTest {

    private static final String PORTAIL = "cle-portail-2026-7Hq2Vd";
    private static final String BONITA = "cle-bonita-2026-Xp4MzN";
    private static final String POSTMAN = "cle-postman-2026-Rb3WsE";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private final Doublures.Dao dao = new Doublures.Dao();
    private final Doublures.Monde monde = new Doublures.Monde();
    private ServeurRest serveur;
    private String base;

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        ServiceCandidatures service = new ServiceCandidatures(dao, 5);
        Orchestrateur orchestrateur = new OrchestrateurDirect(service, dao, monde, "responsable@inpt.ac.ma", 30, Clock.systemDefaultZone());
        serveur = new ServeurRest("127.0.0.1", port, RessourceCandidatures.configuration(service, orchestrateur),
                new VerificateurSante("S4", () -> true), new ClesApi(Configuration.charger().valeur("soa.api.cles")));
        serveur.demarrer();
        base = "http://127.0.0.1:" + port + "/api/v1";
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> appel(String methode, String chemin, String cle, String corps) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin)).header("Content-Type", "application/json")
                .header("X-Acteur", "test");
        if (cle != null) {
            r.header("X-API-Key", cle);
        }
        r.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> r) throws IOException {
        return Json.MAPPEUR.readTree(r.body());
    }

    private static final String DEPOT = "{\"etudiantId\":1,\"offreId\":1,\"lettreMotivation\":\"Je souhaite rejoindre votre équipe pour ce stage.\"}";

    @Test
    void parcoursCompletParHttp() throws Exception {
        HttpResponse<String> depot = appel("POST", "/soumissions", PORTAIL, DEPOT);
        assertEquals(202, depot.statusCode(), depot.body());
        assertEquals("ENREGISTREE", json(depot).path("statut").asText());
        long id = json(depot).path("candidatureId").asLong();
        assertEquals(8, json(depot).path("etapes").size());

        assertEquals(409, appel("POST", "/candidatures/" + id + "/decision", PORTAIL, "{\"decision\":\"ACCEPTER\"}").statusCode(),
                "décision avant l'évaluation");
        assertEquals("EN_COURS_EVALUATION", json(appel("POST", "/candidatures/" + id + "/evaluation", PORTAIL, null)).path("statut").asText());
        assertEquals(409, appel("DELETE", "/candidatures/" + id, PORTAIL, null).statusCode(), "R8 : plus d'annulation");
        HttpResponse<String> decision = appel("POST", "/candidatures/" + id + "/decision", PORTAIL, "{\"decision\":\"ACCEPTER\",\"commentaire\":\"Bienvenue\"}");
        assertEquals(200, decision.statusCode(), decision.body());
        assertEquals("ACCEPTEE", json(decision).path("candidature").path("statut").asText());

        assertEquals(3, json(appel("GET", "/candidatures/" + id + "/historique", PORTAIL, null)).size());
        assertTrue(json(appel("GET", "/candidatures/" + id + "/etapes", PORTAIL, null)).size() >= 13);
        assertEquals(1, json(appel("GET", "/etudiants/1/candidatures", PORTAIL, null)).size());
        assertEquals(1, json(appel("GET", "/etudiants/1/soumissions", PORTAIL, null)).size());
        assertEquals(1, json(appel("GET", "/entreprises/1/candidatures?statut=ACCEPTEE", PORTAIL, null)).size());
        assertEquals(1, json(appel("GET", "/offres/1/candidatures", PORTAIL, null)).size());
        assertEquals(0, json(appel("GET", "/etudiants/1/candidatures/actives/compte", PORTAIL, null)).path("actives").asInt());
        assertTrue(json(appel("GET", "/candidatures/verification-doublon?etudiantId=1&offreId=1", PORTAIL, null)).path("doublon").asBoolean());
    }

    @Test
    void unRejetEstUneReponse202AvecLaRegle() throws Exception {
        monde.etudiants.put(17L, new Partenaires.EtatEtudiant(true, false, "Le compte de l'étudiant est suspendu : il ne peut pas candidater.",
                "Sara Bouzidi", "sara@inpt.ac.ma", "ASEDS", "2A"));
        HttpResponse<String> r = appel("POST", "/soumissions", PORTAIL, DEPOT.replace("\"etudiantId\":1", "\"etudiantId\":17"));
        assertEquals(202, r.statusCode());
        assertEquals("REJETEE", json(r).path("statut").asText());
        assertEquals("R1", json(r).path("regleRejetee").asText());
    }

    @Test
    void unServiceArreteDonne503() throws Exception {
        monde.enPanne.add("offres");
        HttpResponse<String> r = appel("POST", "/soumissions", PORTAIL, DEPOT);
        assertEquals(503, r.statusCode());
        assertEquals("SERVICE_OFFRES_INDISPONIBLE", json(r).path("code").asText());
    }

    @Test
    void annulationEn204() throws Exception {
        long id = json(appel("POST", "/soumissions", PORTAIL, DEPOT)).path("candidatureId").asLong();
        assertEquals(204, appel("DELETE", "/candidatures/" + id, PORTAIL, null).statusCode());
        assertEquals("ANNULEE", json(appel("GET", "/candidatures/" + id, PORTAIL, null)).path("statut").asText());
        assertEquals("ANNULATION_IMPOSSIBLE", json(appel("DELETE", "/candidatures/" + id, PORTAIL, null)).path("code").asText());
    }

    @Test
    void clientsAutorises() throws Exception {
        assertEquals(401, appel("POST", "/soumissions", null, DEPOT).statusCode());
        assertEquals(403, appel("POST", "/soumissions", BONITA, DEPOT).statusCode(), "Bonita ne dépose pas pour l'étudiant");
        String enregistrement = "{\"etudiantId\":2,\"offreId\":1,\"entrepriseId\":1,\"lettreMotivation\":\"Lettre\",\"scoreCompetences\":50}";
        assertEquals(403, appel("POST", "/candidatures", PORTAIL, enregistrement).statusCode(), "seul le processus enregistre");
        assertEquals(201, appel("POST", "/candidatures", BONITA, enregistrement).statusCode());
        assertEquals(409, appel("POST", "/candidatures", BONITA, enregistrement).statusCode(), "R5");
        assertEquals(403, appel("PATCH", "/candidatures/1/statut", PORTAIL, "{\"statut\":\"ACCEPTEE\"}").statusCode());
        HttpResponse<String> r9 = appel("PATCH", "/candidatures/1/statut", POSTMAN, "{\"statut\":\"ACCEPTEE\"}");
        assertEquals(409, r9.statusCode());
        assertEquals("TRANSITION_INTERDITE", json(r9).path("code").asText());
    }

    @Test
    void validation() throws Exception {
        HttpResponse<String> vide = appel("POST", "/soumissions", PORTAIL, "{\"etudiantId\":1}");
        assertEquals(422, vide.statusCode());
        assertEquals(2, json(vide).path("details").size(), vide.body());
        assertEquals(400, appel("POST", "/soumissions", PORTAIL, "{\"etudiantId\":\"un\"}").statusCode());
        assertEquals(400, appel("GET", "/candidatures/verification-doublon?etudiantId=1", PORTAIL, null).statusCode());
        assertEquals(400, appel("GET", "/candidatures?statut=FINIE", PORTAIL, null).statusCode());
        assertEquals(404, appel("GET", "/candidatures/999", PORTAIL, null).statusCode());
        assertEquals(404, appel("GET", "/soumissions/999", PORTAIL, null).statusCode());
        assertEquals(422, appel("GET", "/etudiants/0/candidatures", PORTAIL, null).statusCode());
    }

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
        assertEquals(VerificationContrat.operations(RessourceCandidatures.class), documentees);
    }
}
