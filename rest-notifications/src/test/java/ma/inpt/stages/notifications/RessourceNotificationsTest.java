package ma.inpt.stages.notifications;

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
import java.time.Instant;
import java.time.ZoneId;
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

/** Vrais échanges HTTP avec S5 (base, SMTP et sources simulés) et cohérence avec le contrat OpenAPI. */
class RessourceNotificationsTest {

    private static final String POSTMAN = "cle-postman-2026-Rb3WsE";
    private static final String PORTAIL = "cle-portail-2026-7Hq2Vd";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private final Doublures.Boite boite = new Doublures.Boite();
    private ServeurRest serveur;
    private String base;

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        ServiceNotifications service = new ServiceNotifications(new Doublures.Dao(), boite, new Doublures.Sources(),
                Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneId.of("Africa/Casablanca")));
        serveur = new ServeurRest("127.0.0.1", port, RessourceNotifications.configuration(service), new VerificateurSante("S5", () -> true),
                new ClesApi(Configuration.charger().valeur("soa.api.cles")));
        serveur.demarrer();
        base = "http://127.0.0.1:" + port + "/api/v1";
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> appel(String methode, String chemin, String cle, String corps) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin)).header("Content-Type", "application/json");
        if (cle != null) {
            r.header("X-API-Key", cle);
        }
        r.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> r) throws IOException {
        return Json.MAPPEUR.readTree(r.body());
    }

    private static final String CONFIRMATION = "{\"type\":\"CONFIRMATION_DEPOT\",\"destinataire\":\"salma.elidrissi@inpt.ac.ma\","
            + "\"variables\":{\"nomEtudiant\":\"Salma El Idrissi\",\"titreOffre\":\"Stage AMOA – Paiement mobile\",\"entreprise\":\"NovaPay Maghreb\"},"
            + "\"candidatureId\":13,\"offreId\":1}";

    @Test
    void envoyerPuisRetrouver() throws Exception {
        HttpResponse<String> cree = appel("POST", "/notifications", POSTMAN, CONFIRMATION);
        assertEquals(201, cree.statusCode(), cree.body());
        assertEquals("ENVOYEE", json(cree).path("statut").asText());
        assertEquals("Candidature enregistrée : Stage AMOA – Paiement mobile", json(cree).path("sujet").asText());
        long id = json(cree).path("id").asLong();
        assertTrue(cree.headers().firstValue("Location").orElse("").endsWith("/api/v1/notifications/" + id));
        assertEquals(id, json(appel("GET", "/notifications/" + id, PORTAIL, null)).path("id").asLong());
        assertEquals(1, json(appel("GET", "/notifications?destinataire=salma.elidrissi@inpt.ac.ma", PORTAIL, null)).size());
        assertEquals(0, json(appel("GET", "/notifications?destinataire=autre@inpt.ac.ma", PORTAIL, null)).size());
    }

    @Test
    void echecSmtpDonne201AvecStatutEchec() throws Exception {
        boite.enPanne = true;
        HttpResponse<String> r = appel("POST", "/notifications", POSTMAN, CONFIRMATION);
        assertEquals(201, r.statusCode());
        assertEquals("ECHEC", json(r).path("statut").asText());
    }

    @Test
    void codesDErreur() throws Exception {
        assertEquals(401, appel("POST", "/notifications", null, CONFIRMATION).statusCode());
        HttpResponse<String> interdit = appel("POST", "/notifications", PORTAIL, CONFIRMATION);
        assertEquals(403, interdit.statusCode(), "le portail ne fait pas envoyer de courriel arbitraire");
        assertEquals("ACCES_INTERDIT", json(interdit).path("code").asText());
        assertTrue(boite.messages.isEmpty());
        HttpResponse<String> manque = appel("POST", "/notifications", POSTMAN, "{\"type\":\"REFUS\",\"destinataire\":\"a@inpt.ac.ma\",\"variables\":{}}");
        assertEquals(422, manque.statusCode());
        assertEquals(3, json(manque).path("details").size(), manque.body());
        assertEquals(400, appel("POST", "/notifications", POSTMAN, "{\"type\":\"REFUS\",\"destinataire\":3,\"variables\":[]}").statusCode());
        assertEquals(404, appel("GET", "/notifications/77", PORTAIL, null).statusCode());
        assertEquals(400, appel("GET", "/notifications?type=SMS", PORTAIL, null).statusCode());
        assertEquals(400, appel("POST", "/rappels/executer?jours=99", PORTAIL, null).statusCode());
        assertEquals(403, appel("POST", "/rappels/executer", "cle-bonita-2026-Xp4MzN", null).statusCode());
    }

    @Test
    void lesRappelsSontDeclenchablesParLePortail() throws Exception {
        HttpResponse<String> r = appel("POST", "/rappels/executer", PORTAIL, null);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(3, json(r).path("joursAvantLimite").asInt());
        assertEquals(0, json(r).path("rappelsEnvoyes").asInt());
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
        assertEquals(VerificationContrat.operations(RessourceNotifications.class), documentees);
    }
}
