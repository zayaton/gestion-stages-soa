package ma.inpt.stages.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import ma.inpt.stages.common.BaseDeDonnees;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ConnexionAnnulable;
import ma.inpt.stages.common.Correlation;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.notifications.Modeles.NouvelleNotification;
import ma.inpt.stages.notifications.Modeles.Notification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Sur la vraie infrastructure : MySQL pour la table notification, Mailpit pour l'envoi SMTP réel. */
class NotificationsMailpitIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static BaseDeDonnees base;

    @BeforeAll
    static void ouvrir() {
        base = BaseDeDonnees.depuis(Configuration.charger(), "notifications");
    }

    @AfterAll
    static void fermer() {
        base.close();
    }

    private static JsonNode mailpit(String chemin) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("http://localhost:8025/api/v1" + chemin)).build();
        return Json.MAPPEUR.readTree(HTTP.send(r, HttpResponse.BodyHandlers.ofString()).body());
    }

    @Test
    void unCourrielReelArriveDansMailpitAvecSesAccents() throws Exception {
        String marque = UUID.randomUUID().toString();
        String destinataire = "test-" + marque.substring(0, 8) + "@inpt.ac.ma";
        Correlation.definir(marque);
        try (ConnexionAnnulable connexion = new ConnexionAnnulable(base.connexion())) {
            ServiceNotifications service = new ServiceNotifications(new NotificationDaoJdbc(connexion),
                    new Expediteur.Smtp(Configuration.charger()), new Doublures.Sources(), Clock.systemDefaultZone());
            Notification n = service.envoyer(new NouvelleNotification("DEMANDE_COMPLEMENT", destinataire,
                    Map.of("nomEtudiant", "Aïcha Tahiri", "titreOffre", "Stage réseau", "entreprise", "Ifrane Réseaux Télécom",
                            "commentaire", "Pouvez-vous préciser vos disponibilités ?"), 4L, 4L));
            assertEquals("ENVOYEE", n.statut());
            assertEquals(marque, n.correlationId());
            assertTrue(n.dateEnvoi().isAfter(LocalDateTime.now().minusMinutes(5)));
        } finally {
            Correlation.effacer();
        }

        JsonNode recherche = mailpit("/search?query=" + URLEncoder.encode("to:" + destinataire, StandardCharsets.UTF_8));
        assertEquals(1, recherche.path("messages_count").asInt(), recherche.toString());
        JsonNode resume = recherche.path("messages").get(0);
        assertEquals("Complément d'information demandé : Stage réseau", resume.path("Subject").asText());
        JsonNode message = mailpit("/message/" + resume.path("ID").asText());
        assertTrue(message.path("Text").asText().contains("Aïcha Tahiri"));
        assertTrue(message.path("HTML").asText().contains("Pouvez-vous préciser vos disponibilités ?"));
        assertEquals("ne-pas-repondre@stages.inpt.ac.ma", message.path("From").path("Address").asText());
        JsonNode entetes = mailpit("/message/" + resume.path("ID").asText() + "/headers");
        assertEquals(marque, entetes.path("X-Correlation-Id").get(0).asText());
    }

    @Test
    void unServeurSmtpInjoignableDonneUnEchecEnregistre() throws Exception {
        System.setProperty("notifications.smtp.port", "1");
        System.setProperty("notifications.smtp.delai.ms", "1500");
        try (ConnexionAnnulable connexion = new ConnexionAnnulable(base.connexion())) {
            NotificationDaoJdbc dao = new NotificationDaoJdbc(connexion);
            ServiceNotifications service = new ServiceNotifications(dao, new Expediteur.Smtp(Configuration.charger()),
                    new Doublures.Sources(), Clock.systemDefaultZone());
            Notification n = service.envoyer(new NouvelleNotification("ALERTE_RESPONSABLE", "responsable@inpt.ac.ma",
                    Map.of("etape", "Vérification de l'étudiant", "erreur", "S1 indisponible"), null, null));
            assertEquals("ECHEC", n.statut());
            assertFalse(n.erreur().isBlank());
            assertEquals("ECHEC", dao.trouver(n.id()).orElseThrow().statut());
            assertTrue(dao.lister(new NotificationDao.Filtre("responsable@inpt.ac.ma", null, "ALERTE_RESPONSABLE", 10)).size() >= 1);
        } finally {
            System.clearProperty("notifications.smtp.port");
            System.clearProperty("notifications.smtp.delai.ms");
        }
    }

    @Test
    void laBaseRetrouveUnRappelDejaEnvoye() throws Exception {
        try (ConnexionAnnulable connexion = new ConnexionAnnulable(base.connexion())) {
            NotificationDaoJdbc dao = new NotificationDaoJdbc(connexion);
            dao.inserer(new Notification(0, "RAPPEL_DATE_LIMITE", "rappel@inpt.ac.ma", "s", "c", LocalDateTime.now(), "ENVOYEE", null, null, 5L, null));
            dao.inserer(new Notification(0, "RAPPEL_DATE_LIMITE", "echec@inpt.ac.ma", "s", "c", LocalDateTime.now(), "ECHEC", "x", null, 5L, null));
            assertTrue(dao.rappelDejaEnvoye(5, "rappel@inpt.ac.ma"));
            assertFalse(dao.rappelDejaEnvoye(5, "echec@inpt.ac.ma"), "un rappel en échec doit pouvoir être renvoyé");
            assertFalse(dao.rappelDejaEnvoye(6, "rappel@inpt.ac.ma"));
        }
    }
}
