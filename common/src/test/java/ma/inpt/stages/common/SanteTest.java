package ma.inpt.stages.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

class SanteTest {

    private static int portLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static HttpResponse<String> get(int port) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).build();
        // HTTP/1.1 imposé : sur une tentative de passage en h2c, Grizzly répond sans ses en-têtes.
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(requete, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void baseDisponibleDonne200() {
        VerificateurSante.Etat etat = new VerificateurSante("S1 Étudiants", () -> true).verifier();
        assertEquals(200, etat.codeHttp());
        assertTrue(etat.json().contains("\"statut\":\"OK\""));
        assertTrue(etat.json().contains("S1 Étudiants"));
    }

    @Test
    void baseIndisponibleDonne503() {
        VerificateurSante.Etat etat = new VerificateurSante("S1", () -> false).verifier();
        assertEquals(503, etat.codeHttp());
        assertTrue(etat.json().contains("\"baseDeDonnees\":\"INDISPONIBLE\""));
    }

    @Test
    void leServeurSoapExposeHealth() throws Exception {
        int port = portLibre();
        try (ServeurSoap serveur = new ServeurSoap("127.0.0.1", port, new VerificateurSante("S1 Étudiants", () -> true))) {
            serveur.demarrer();
            HttpResponse<String> reponse = get(port);
            assertEquals(200, reponse.statusCode());
            assertTrue(reponse.body().contains("S1 Étudiants"));
        }
    }

    @Test
    void leServeurRestExposeHealthEt503() throws Exception {
        int port = portLibre();
        try (ServeurRest serveur = new ServeurRest("127.0.0.1", port, new ResourceConfig(), new VerificateurSante("S4 Évaluation", () -> false))) {
            serveur.demarrer();
            HttpResponse<String> reponse = get(port);
            assertEquals(503, reponse.statusCode());
            assertTrue(reponse.body().contains("S4 Évaluation"), reponse.body());
            String type = reponse.headers().firstValue("Content-Type").orElse("");
            assertTrue(type.startsWith("application/json"), type);
        }
    }

    @Test
    void uneBaseInjoignableEchoueVite() throws Exception {
        int port = portLibre();
        System.setProperty("soa.db.port", String.valueOf(port));
        System.setProperty("test.db.schema", "inexistant");
        System.setProperty("test.db.utilisateur", "personne");
        System.setProperty("soa.db.pool.delai.ms", "500");
        try (BaseDeDonnees base = BaseDeDonnees.depuis(Configuration.charger(), "test")) {
            assertTimeout(Duration.ofSeconds(5), () -> assertFalse(base.estDisponible()));
        } finally {
            System.clearProperty("soa.db.port");
            System.clearProperty("test.db.schema");
            System.clearProperty("test.db.utilisateur");
            System.clearProperty("soa.db.pool.delai.ms");
        }
    }
}
