package ma.inpt.stages.portail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.rest.ClientRest;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.contrats.etudiants.Erreur;
import ma.inpt.stages.contrats.etudiants.EtudiantIntrouvableFault;
import ma.inpt.stages.portail.securite.CompteDao;
import ma.inpt.stages.portail.securite.ServiceAuthentification;
import ma.inpt.stages.portail.securite.Utilisateur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortailTest {

    /** Comptes et sessions en mémoire ; l'heure est réglable pour tester l'expiration. */
    static final class Comptes implements CompteDao {
        final Map<String, Compte> comptes = new HashMap<>();
        final Map<String, Object[]> sessions = new HashMap<>();

        Comptes() {
            ajouter(1, "responsable@inpt.ac.ma", "Responsable@2026", "RESPONSABLE", null, true);
            ajouter(2, "salma.elidrissi@inpt.ac.ma", "Etudiant@2026", "ETUDIANT", 1L, true);
            ajouter(3, "ancien@inpt.ac.ma", "Etudiant@2026", "ETUDIANT", 9L, false);
        }

        private void ajouter(long id, String identifiant, String mot, String role, Long ref, boolean actif) {
            comptes.put(identifiant, new Compte(id, identifiant, BCrypt.withDefaults().hashToString(4, mot.toCharArray()), role, ref, actif));
        }

        @Override
        public Optional<Compte> parIdentifiant(String identifiant) {
            return Optional.ofNullable(comptes.get(identifiant));
        }

        @Override
        public void creerSession(String jetonHash, long compteId, LocalDateTime expiration) {
            sessions.put(jetonHash, new Object[] {compteId, expiration});
        }

        @Override
        public Optional<Utilisateur> session(String jetonHash, LocalDateTime maintenant) {
            Object[] s = sessions.get(jetonHash);
            if (s == null || !((LocalDateTime) s[1]).isAfter(maintenant)) {
                return Optional.empty();
            }
            return comptes.values().stream().filter(c -> c.id() == (long) s[0] && c.actif())
                    .map(c -> new Utilisateur(c.id(), c.identifiant(), c.role(), c.referenceId())).findFirst();
        }

        @Override
        public void supprimerSession(String jetonHash) {
            sessions.remove(jetonHash);
        }

        @Override
        public int purgerSessions(LocalDateTime maintenant) {
            int avant = sessions.size();
            sessions.values().removeIf(s -> !((LocalDateTime) s[1]).isAfter(maintenant));
            return avant - sessions.size();
        }
    }

    /** Horloge qu'on peut avancer. */
    static final class Horloge extends Clock {
        Instant maintenant = Instant.parse("2026-11-09T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    // Masquage

    @Test
    void leTelephoneGardeDeuxChiffresDeChaqueCote() {
        assertEquals("06******12", Masquage.telephone("0661234512"));
        assertEquals("****", Masquage.telephone("0612"));
    }

    @Test
    void leCourrielGardeLaPremiereLettreEtLeDomaine() {
        assertEquals("h***@inpt.ac.ma", Masquage.email("hiba.elamdaoui@inpt.ac.ma"));
        assertEquals("***", Masquage.email("sans-arobase"));
    }

    @Test
    void laVueEntrepriseNeContientJamaisLesCoordonneesCompletes() {
        Vues.EtudiantVue e = new Vues.EtudiantVue(1L, "INPT-2024-0001", "El Idrissi", "Salma", "AMOA", "2A", "salma.elidrissi@inpt.ac.ma",
                "0661234512", "Rabat", "ACTIF", List.of());
        String json = Json.MAPPEUR.valueToTree(e.masquee()).toString();
        assertFalse(json.contains("salma.elidrissi@"));
        assertFalse(json.contains("0661234512"));
        assertTrue(json.contains("s***@inpt.ac.ma"));
        assertTrue(json.contains("06******12"));
    }

    // Authentification

    private final Comptes comptes = new Comptes();
    private final Horloge horloge = new Horloge();
    private final ServiceAuthentification auth = new ServiceAuthentification(comptes, Duration.ofHours(8), horloge);

    @Test
    void connexionPuisVerificationDuJeton() {
        ServiceAuthentification.Session s = auth.connecter(" Salma.ElIdrissi@inpt.ac.ma ", "Etudiant@2026");
        assertEquals("ETUDIANT", s.utilisateur().role());
        assertEquals(1L, s.utilisateur().referenceId());
        assertEquals("etudiant:1", s.utilisateur().acteur());
        assertTrue(s.jeton().length() >= 40);
        assertEquals(1L, auth.verifier(s.jeton()).referenceId());
        assertFalse(comptes.sessions.containsKey(s.jeton()), "seule l'empreinte du jeton est stockée");
    }

    @Test
    void lesRefusOntTousLeMemeMessage() {
        String mauvaisMot = assertThrows(ErreurMetier.class, () -> auth.connecter("salma.elidrissi@inpt.ac.ma", "faux")).getMessage();
        String inconnu = assertThrows(ErreurMetier.class, () -> auth.connecter("personne@inpt.ac.ma", "Etudiant@2026")).getMessage();
        String inactif = assertThrows(ErreurMetier.class, () -> auth.connecter("ancien@inpt.ac.ma", "Etudiant@2026")).getMessage();
        assertEquals(mauvaisMot, inconnu, "on ne révèle pas quels identifiants existent");
        assertEquals(mauvaisMot, inactif);
        assertEquals(ErreurMetier.Type.NON_AUTHENTIFIE, assertThrows(ErreurMetier.class, () -> auth.connecter("x", "y")).type());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> auth.connecter("", "")).type());
    }

    @Test
    void uneSessionExpireEtUneDeconnexionLaSupprime() {
        String jeton = auth.connecter("responsable@inpt.ac.ma", "Responsable@2026").jeton();
        horloge.maintenant = horloge.maintenant.plus(Duration.ofHours(7));
        auth.verifier(jeton);
        horloge.maintenant = horloge.maintenant.plus(Duration.ofHours(2));
        assertEquals(ErreurMetier.Type.NON_AUTHENTIFIE, assertThrows(ErreurMetier.class, () -> auth.verifier(jeton)).type());
        assertEquals(1, auth.purger());

        String autre = auth.connecter("responsable@inpt.ac.ma", "Responsable@2026").jeton();
        auth.deconnecter(autre);
        assertThrows(ErreurMetier.class, () -> auth.verifier(autre));
    }

    @Test
    void deuxConnexionsDonnentDeuxJetonsDifferents() {
        assertNotEquals(auth.connecter("responsable@inpt.ac.ma", "Responsable@2026").jeton(),
                auth.connecter("responsable@inpt.ac.ma", "Responsable@2026").jeton());
    }

    // Traduction des erreurs des services

    @Test
    void uneFauteSoapTypeeGardeSonCodeEtSonMessage() {
        Erreur info = new Erreur();
        info.setCode("ETUDIANT_INTROUVABLE");
        info.setMessage("Aucun étudiant ne correspond à l'identifiant 9.");
        ErreurMetier e = Services.traduire("etudiants", new EtudiantIntrouvableFault("x", info));
        assertEquals(ErreurMetier.Type.INTROUVABLE, e.type());
        assertEquals("ETUDIANT_INTROUVABLE", e.code());
        assertEquals("Aucun étudiant ne correspond à l'identifiant 9.", e.getMessage());
        ErreurMetier panne = Services.traduire("offres", new jakarta.xml.ws.WebServiceException("connexion refusée"));
        assertEquals("SERVICE_OFFRES_INDISPONIBLE", panne.code());
        assertEquals(ErreurMetier.Type.INDISPONIBLE, panne.type());
    }

    @Test
    void uneErreurRestEstRepriseAvecSonStatut() throws Exception {
        JsonNode corps = Json.MAPPEUR.readTree("{\"code\":\"ANNULATION_IMPOSSIBLE\",\"message\":\"Règle R8 : ...\",\"details\":[\"a\"]}");
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> Services.rest(new ClientRest.Reponse(409, corps)));
        assertEquals(ErreurMetier.Type.CONFLIT, e.type());
        assertEquals("ANNULATION_IMPOSSIBLE", e.code());
        assertEquals(List.of("a"), e.details());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> Services.rest(new ClientRest.Reponse(422, corps))).type());
        assertEquals(corps, Services.rest(new ClientRest.Reponse(200, corps)));
    }

    // Serveur : sessions, rôles, fichiers

    private ServeurRest serveur;
    private String base;
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        Services aucun = new Services(null, null, null, null, null, null) {
        };
        serveur = new ServeurRest("127.0.0.1", port, Portail.configuration(auth, aucun, new RessourceResponsable.AdressesSante(Map.of())),
                new VerificateurSante("Portail", () -> true), null);
        serveur.demarrer();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> appel(String methode, String chemin, String jeton, String corps) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + chemin)).header("Content-Type", "application/json");
        if (jeton != null) {
            r.header("Authorization", "Bearer " + jeton);
        }
        r.method(methode, corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String code(HttpResponse<String> r) throws IOException {
        return Json.MAPPEUR.readTree(r.body()).path("code").asText();
    }

    @Test
    void connexionParHttpEtControleDesRoles() throws Exception {
        HttpResponse<String> refus = appel("POST", "/api/auth/connexion", null, "{\"identifiant\":\"responsable@inpt.ac.ma\",\"motDePasse\":\"faux\"}");
        assertEquals(401, refus.statusCode());
        assertEquals("NON_AUTHENTIFIE", code(refus));

        HttpResponse<String> ok = appel("POST", "/api/auth/connexion", null, "{\"identifiant\":\"responsable@inpt.ac.ma\",\"motDePasse\":\"Responsable@2026\"}");
        assertEquals(200, ok.statusCode(), ok.body());
        JsonNode reponse = Json.MAPPEUR.readTree(ok.body());
        String jeton = reponse.path("jeton").asText();
        assertEquals("RESPONSABLE", reponse.path("utilisateur").path("role").asText());
        assertEquals("Responsable des stages", reponse.path("utilisateur").path("nom").asText());

        assertEquals(200, appel("GET", "/api/auth/moi", jeton, null).statusCode());
        HttpResponse<String> sansJeton = appel("GET", "/api/etudiant/profil", null, null);
        assertEquals(401, sansJeton.statusCode());
        assertEquals("NON_AUTHENTIFIE", code(sansJeton));
        HttpResponse<String> mauvaisRole = appel("GET", "/api/etudiant/profil", jeton, null);
        assertEquals(403, mauvaisRole.statusCode(), "un responsable n'entre pas dans l'espace étudiant");
        assertEquals("ACCES_INTERDIT", code(mauvaisRole));
        assertEquals(403, appel("GET", "/api/entreprise/candidatures", jeton, null).statusCode());
        assertEquals(401, appel("GET", "/api/responsable/tableau-de-bord", "jeton-invente", null).statusCode());

        assertEquals(204, appel("POST", "/api/auth/deconnexion", jeton, null).statusCode());
        assertEquals(401, appel("GET", "/api/auth/moi", jeton, null).statusCode(), "le jeton ne sert plus après déconnexion");
    }

    @Test
    void lInterfaceEstServieSansFuiteHorsDuDossierPublic() throws Exception {
        HttpResponse<String> accueil = appel("GET", "/", null, null);
        assertEquals(200, accueil.statusCode());
        assertTrue(accueil.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(accueil.body().contains("INPT"));
        assertEquals(200, appel("GET", "/espace/etudiant", null, null).statusCode(), "une adresse de l'interface renvoie la page");
        assertEquals(404, appel("GET", "/inexistant.css", null, null).statusCode());
        // Grizzly refuse lui-même un chemin encodé qui remonte au-dessus de la racine (500 sans corps) ; l'essentiel : rien ne fuit.
        for (String piege : List.of("/..%2Fapplication.properties", "/%2e%2e/application.properties", "/public/../application.properties",
                "/application.properties", "/logback.xml")) {
            HttpResponse<String> r = appel("GET", piege, null, null);
            assertNotEquals(200, r.statusCode(), piege);
            assertFalse(r.body().contains("portail.port") || r.body().contains("<configuration>"), piege);
        }
        HttpResponse<String> api = appel("GET", "/api/inconnue", null, null);
        assertEquals(404, api.statusCode());
        assertEquals("RESSOURCE_INTROUVABLE", code(api));
    }
}
