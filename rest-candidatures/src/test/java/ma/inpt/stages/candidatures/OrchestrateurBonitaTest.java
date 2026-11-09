package ma.inpt.stages.candidatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Le pont S4 vers l'API REST de Bonita, face à un Bonita SIMULÉ (petit serveur HTTP qui reproduit les appels utilisés).
 * Ces tests prouvent que S4 parle correctement à l'API ; seule la liste de contrôle manuelle prouve le processus réel.
 */
class OrchestrateurBonitaTest {

    /** Bonita simulé : enregistre chaque appel ; on règle ses réponses par test. */
    static final class BonitaSimule {
        final List<String> appels = Collections.synchronizedList(new ArrayList<>());
        final List<String> corps = Collections.synchronizedList(new ArrayList<>());
        String processus = "[{\"id\":\"777\",\"name\":\"Candidature\",\"version\":\"1.0\"}]";
        String taches = "[{\"id\":\"55\",\"name\":\"Décision de l'entreprise\",\"state\":\"ready\"}]";
        int sessionsExpireesARepondre;
        final HttpServer serveur;

        BonitaSimule(int port) throws IOException {
            serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            serveur.createContext("/bonita", this::traiter);
            serveur.start();
        }

        private void traiter(HttpExchange e) throws IOException {
            String chemin = URLDecoder.decode(e.getRequestURI().toString(), StandardCharsets.UTF_8).replace("/bonita", "");
            String corpsRecu = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            appels.add(e.getRequestMethod() + " " + chemin);
            corps.add(corpsRecu);
            if (chemin.equals("/loginservice")) {
                boolean ok = corpsRecu.contains("username=walter.bates") && corpsRecu.contains("password=bpm");
                if (ok) {
                    e.getResponseHeaders().add("Set-Cookie", "JSESSIONID=s1; Path=/bonita");
                    e.getResponseHeaders().add("Set-Cookie", "X-Bonita-API-Token=jeton-123; Path=/bonita");
                }
                repondre(e, ok ? 204 : 401, "");
                return;
            }
            if (!"jeton-123".equals(e.getRequestHeaders().getFirst("X-Bonita-API-Token")) || sessionsExpireesARepondre > 0) {
                if (sessionsExpireesARepondre > 0) {
                    sessionsExpireesARepondre--;
                }
                repondre(e, 401, "");
                return;
            }
            if (chemin.startsWith("/API/bpm/process?")) {
                repondre(e, 200, processus);
            } else if (chemin.equals("/API/bpm/process/777/instantiation")) {
                repondre(e, 200, "{\"caseId\":\"1001\"}");
            } else if (chemin.startsWith("/API/bpm/humanTask?")) {
                repondre(e, 200, taches);
            } else if (chemin.equals("/API/system/session/unusedid")) {
                repondre(e, 200, "{\"user_id\":\"4\",\"user_name\":\"walter.bates\"}");
            } else if (chemin.equals("/API/bpm/humanTask/55") || chemin.equals("/API/bpm/userTask/55/execution") || chemin.equals("/API/bpm/case/1001")) {
                repondre(e, chemin.endsWith("execution") ? 204 : 200, "");
            } else {
                repondre(e, 404, "{\"message\":\"inconnu\"}");
            }
        }

        private static void repondre(HttpExchange e, int code, String json) throws IOException {
            byte[] octets = json.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(code, octets.length == 0 ? -1 : octets.length);
            if (octets.length > 0) {
                try (OutputStream s = e.getResponseBody()) {
                    s.write(octets);
                }
            }
            e.close();
        }

        String corpsDe(String appel) {
            synchronized (appels) {
                return corps.get(appels.lastIndexOf(appel));
            }
        }
    }

    private static final String LETTRE = "Je souhaite rejoindre votre équipe pour ce stage passionnant.";

    private final Doublures.Dao dao = new Doublures.Dao();
    private final Doublures.Monde monde = new Doublures.Monde();
    private final ServiceCandidatures service = new ServiceCandidatures(dao, 5);
    private BonitaSimule bonita;
    private OrchestrateurBonita orchestrateur;

    private static int portLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private OrchestrateurBonita orchestrateur(int port) {
        OrchestrateurDirect direct = new OrchestrateurDirect(service, dao, monde, "responsable@inpt.ac.ma", 30, Clock.systemDefaultZone());
        ClientBonita client = new ClientBonita("http://127.0.0.1:" + port + "/bonita", "walter.bates", "bpm", "Candidature", "1.0",
                "Décision de l'entreprise", Duration.ofSeconds(5));
        return new OrchestrateurBonita(service, dao, monde, direct, client);
    }

    @BeforeEach
    void demarrer() throws IOException {
        int port = portLibre();
        bonita = new BonitaSimule(port);
        orchestrateur = orchestrateur(port);
    }

    @AfterEach
    void arreter() {
        bonita.serveur.stop(0);
    }

    private Soumission deposer() {
        return orchestrateur.deposer(new NouvelleSoumission(1L, 1L, LETTRE), "etudiant:1");
    }

    /** Candidature liée au cas 1001, en cours d'évaluation, comme après les étapes 1 à 6 du processus. */
    private Candidature enEvaluation() {
        Soumission s = deposer();
        Candidature c = service.enregistrer(new Modeles.NouvelleCandidature(s.id(), 1L, 1L, 1L, LETTRE, 67), "client:bonita");
        return service.demarrerEvaluation(c.id(), "entreprise:1");
    }

    // Démarrage du processus

    @Test
    void leDepotDemarreUnCasAvecLeContratDuProcessus() throws Exception {
        Soumission s = deposer();
        assertEquals("EN_TRAITEMENT", s.statut(), "le processus fera avancer le statut");
        assertEquals(1001L, s.bonitaCaseId());
        assertEquals(List.of("RECEPTION"), s.etapes().stream().map(Modeles.Etape::etape).toList());
        assertTrue(bonita.appels.contains("POST /loginservice"));
        JsonNode contrat = Json.MAPPEUR.readTree(bonita.corpsDe("POST /API/bpm/process/777/instantiation"));
        assertEquals(s.id(), contrat.path("soumissionId").asLong());
        assertEquals(1, contrat.path("etudiantId").asLong());
        assertEquals(1, contrat.path("offreId").asLong());
        assertEquals(LETTRE, contrat.path("lettreMotivation").asText());
        assertTrue(bonita.appels.stream().anyMatch(a -> a.contains("f=name=Candidature") && a.contains("f=version=1.0")));
        assertTrue(monde.notifications.isEmpty(), "en mode bonita, vérifier et notifier sont le rôle du processus, pas de S4");
    }

    @Test
    void uneSessionBonitaExpireeEstRouverteUneFois() {
        deposer();
        bonita.sessionsExpireesARepondre = 1;
        deposer();
        assertEquals(2, bonita.appels.stream().filter("POST /loginservice"::equals).count());
    }

    @Test
    void bonitaArreteDonne503SoumissionEnErreurEtAlerte() throws IOException {
        OrchestrateurBonita sansBonita = orchestrateur(portLibre());
        ErreurMetier e = assertTimeout(Duration.ofSeconds(10),
                () -> assertThrows(ErreurMetier.class, () -> sansBonita.deposer(new NouvelleSoumission(1L, 1L, LETTRE), "x")));
        assertEquals("BONITA_INDISPONIBLE", e.code());
        assertEquals(ErreurMetier.Type.INDISPONIBLE, e.type());
        Soumission s = service.soumission(1);
        assertEquals("ERREUR", s.statut());
        assertTrue(s.etapes().stream().anyMatch(x -> "ERREUR".equals(x.etape())));
        assertEquals(List.of("ALERTE_RESPONSABLE"), monde.types());
    }

    @Test
    void unProcessusNonDeployeEstSignaleClairement() {
        bonita.processus = "[]";
        ErreurMetier e = assertThrows(ErreurMetier.class, this::deposer);
        assertEquals("BONITA_PROCESSUS_ABSENT", e.code());
        assertTrue(e.getMessage().contains("n'est pas déployé"));
    }

    // Décision : exécution de la tâche humaine

    @Test
    void laDecisionExecuteLaTacheHumaineAvecSonContrat() throws Exception {
        Candidature c = enEvaluation();
        ResultatDecision r = orchestrateur.decider(c.id(), new Decision("ACCEPTER", "Bienvenue"), "entreprise:1");
        assertTrue(bonita.appels.stream().anyMatch(a -> a.startsWith("GET /API/bpm/humanTask?") && a.contains("caseId=1001") && a.contains("state=ready")));
        assertEquals("{\"assigned_id\":\"4\"}", bonita.corpsDe("PUT /API/bpm/humanTask/55"));
        JsonNode contrat = Json.MAPPEUR.readTree(bonita.corpsDe("POST /API/bpm/userTask/55/execution"));
        assertEquals("ACCEPTER", contrat.path("decision").asText());
        assertEquals("Bienvenue", contrat.path("commentaire").asText());
        assertEquals("EN_COURS_EVALUATION", r.candidature().statut(), "c'est le processus qui fera passer la candidature ACCEPTEE");
        assertEquals("DECISION_ENTREPRISE", r.etapes().get(0).etape());
        assertTrue(r.etapes().get(0).detail().contains("cas 1001"));
        assertTrue(monde.stages.isEmpty(), "S4 ne crée pas le stage lui-même en mode bonita");
    }

    @Test
    void unComplementEstHistoriseParS4EtTransmisAuProcessus() throws Exception {
        Candidature c = enEvaluation();
        orchestrateur.decider(c.id(), new Decision("DEMANDER_COMPLEMENT", "Précisez vos disponibilités."), "entreprise:1");
        assertEquals("DEMANDER_COMPLEMENT", Json.MAPPEUR.readTree(bonita.corpsDe("POST /API/bpm/userTask/55/execution")).path("decision").asText());
        var dernier = dao.historique(c.id()).get(dao.historique(c.id()).size() - 1);
        assertTrue(dernier.commentaire().contains("Précisez vos disponibilités."));
    }

    @Test
    void sansTachePreteLaDecisionEstRefuseeSansRienEnvoyer() {
        Candidature c = enEvaluation();
        bonita.taches = "[]";
        assertEquals("DECISION_EN_COURS", assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("REFUSER", null), "x")).code());
        assertFalse(bonita.appels.contains("POST /API/bpm/userTask/55/execution"));
    }

    @Test
    void r10SansPlaceLAcceptationEstRefuseeAvantLeProcessus() {
        Candidature c = enEvaluation();
        monde.offres.put(1L, new Partenaires.EtatOffre(true, "Stage AMOA", "ACTIVE", true, false, null, 1, 12));
        assertEquals("PLUS_DE_PLACE", assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("ACCEPTER", null), "x")).code());
        assertFalse(bonita.appels.contains("POST /API/bpm/userTask/55/execution"));
    }

    @Test
    void lesReglesDeDecisionSontLesMemesQuEnModeDirect() {
        Candidature c = enEvaluation();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("DEMANDER_COMPLEMENT", " "), "x")).type());
        Candidature deposee = service.enregistrer(new Modeles.NouvelleCandidature(null, 9L, 1L, 1L, LETTRE, 0), "x");
        assertEquals("DECISION_IMPOSSIBLE", assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(deposee.id(), new Decision("REFUSER", null), "x")).code());
    }

    @Test
    void uneCandidatureSansCasBonitaEstDecideeEnModeDirect() {
        Candidature ancienne = service.enregistrer(new Modeles.NouvelleCandidature(null, 1L, 1L, 1L, LETTRE, 50), "x");
        service.demarrerEvaluation(ancienne.id(), "x");
        ResultatDecision r = orchestrateur.decider(ancienne.id(), new Decision("ACCEPTER", null), "entreprise:1");
        assertEquals("ACCEPTEE", r.candidature().statut());
        assertEquals(List.of(ancienne.id()), monde.stages);
        assertFalse(bonita.appels.stream().anyMatch(a -> a.contains("userTask")));
    }

    // Annulation (R8)

    @Test
    void lAnnulationSupprimeLeCasBonita() {
        Soumission s = deposer();
        Candidature c = service.enregistrer(new Modeles.NouvelleCandidature(s.id(), 1L, 1L, 1L, LETTRE, 67), "x");
        assertEquals("ANNULEE", orchestrateur.annuler(c.id(), "etudiant:1").statut());
        assertTrue(bonita.appels.contains("DELETE /API/bpm/case/1001"));
    }

    @Test
    void siBonitaNeRepondPasLAnnulationResteAcquise() {
        Soumission s = deposer();
        Candidature c = service.enregistrer(new Modeles.NouvelleCandidature(s.id(), 1L, 1L, 1L, LETTRE, 67), "x");
        bonita.serveur.stop(0);
        assertEquals("ANNULEE", orchestrateur.annuler(c.id(), "etudiant:1").statut());
        assertTrue(dao.etapes(s.id()).stream().anyMatch(e -> "ANNULATION".equals(e.etape()) && "ECHEC".equals(e.resultat())));
    }
}
