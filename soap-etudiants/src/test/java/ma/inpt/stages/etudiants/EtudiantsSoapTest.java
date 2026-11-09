package ma.inpt.stages.etudiants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import ma.inpt.stages.common.ComptesService;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.etudiants.ConflitFault;
import ma.inpt.stages.contrats.etudiants.DonneesInvalidesFault;
import ma.inpt.stages.contrats.etudiants.Etudiant;
import ma.inpt.stages.contrats.etudiants.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.EtudiantsService;
import ma.inpt.stages.contrats.etudiants.VerificationEtudiant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrai échange SOAP sur HTTP, client généré compris, avec le DAO en mémoire. */
class EtudiantsSoapTest {

    private static final String NS = "http://stages.inpt.ma/etudiants";
    private static final String WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private EtudiantDaoMemoire dao;
    private ServeurSoap serveur;
    private String url;
    private EtudiantsPortType client;

    @BeforeEach
    void demarrer() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        Configuration config = Configuration.charger();
        dao = new EtudiantDaoMemoire();
        serveur = new ServeurSoap("127.0.0.1", port, new VerificateurSante("S1", () -> true));
        serveur.publier("/ws/etudiants", new EtudiantsEndpoint(new ServiceEtudiants(dao)), new ComptesService(config.valeur("soa.ws.comptes")));
        serveur.demarrer();
        url = "http://127.0.0.1:" + port + "/ws/etudiants";
        client = ClientSoap.configurer(new EtudiantsService().getEtudiantsPort(), url, config);
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private HttpResponse<String> envoyer(String entete, String corps) throws Exception {
        String enveloppe = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:t=\"" + NS + "\">"
                + "<soapenv:Header>" + entete + "</soapenv:Header><soapenv:Body>" + corps + "</soapenv:Body></soapenv:Envelope>";
        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(enveloppe)).build();
        return HTTP.send(requete, HttpResponse.BodyHandlers.ofString());
    }

    private static String jeton(String utilisateur, String motDePasse) {
        return "<wsse:Security xmlns:wsse=\"" + WSSE + "\"><wsse:UsernameToken><wsse:Username>" + utilisateur + "</wsse:Username>"
                + "<wsse:Password>" + motDePasse + "</wsse:Password></wsse:UsernameToken></wsse:Security>";
    }

    private static Etudiant saisie() {
        Etudiant x = new Etudiant();
        x.setMatricule("INPT-2024-0500");
        x.setNom("Tazi");
        x.setPrenom("Rania");
        x.setFiliere("DATA");
        x.setNiveau("2A");
        x.setEmail("rania.tazi@inpt.ac.ma");
        x.setTelephone("0612345678");
        x.setVille("Fès");
        x.getCompetence().addAll(List.of("Python", "SQL"));
        return x;
    }

    // Contrat

    @Test
    void leWsdlEstServiAvecSaDocumentationSansAuthentification() throws Exception {
        HttpResponse<String> reponse = HTTP.send(HttpRequest.newBuilder(URI.create(url + "?wsdl")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reponse.statusCode());
        assertTrue(reponse.body().contains("targetNamespace=\"" + NS + "\""));
        assertTrue(reponse.body().contains("EtudiantIntrouvableFault"));
        assertTrue(reponse.body().contains("règle R1"), "la documentation du WSDL du dépôt doit être servie");
        assertTrue(reponse.body().contains(url), "l'adresse du service doit être l'adresse réelle");
    }

    // Opérations

    @Test
    void cycleDeVieComplet() throws Exception {
        Etudiant cree = client.ajouterEtudiant(saisie());
        assertEquals("ACTIF", cree.getStatut());
        long id = cree.getId();

        Etudiant lu = client.consulterEtudiant(id);
        assertEquals("Fès", lu.getVille());
        assertEquals(List.of("Python", "SQL"), lu.getCompetence());

        Etudiant modification = saisie();
        modification.setVille("Agadir");
        assertEquals("Agadir", client.modifierEtudiant(id, modification).getVille());

        assertEquals("SUSPENDU", client.changerStatutEtudiant(id, "SUSPENDU").getStatut());
        assertEquals(1, client.listerEtudiants(null).size());

        client.supprimerEtudiant(id);
        assertTrue(client.listerEtudiants(null).isEmpty());
    }

    @Test
    void verifierEtudiantInconnuRepondSansFaute() throws Exception {
        VerificationEtudiant v = client.verifierEtudiant(404);
        assertFalse(v.isExiste());
        assertFalse(v.isEligible());
        assertEquals("R1", v.getRegle());
    }

    @Test
    void verifierEtudiantSuspenduDonneLeMotif() throws Exception {
        long id = dao.ajouter("SUSPENDU").id();
        VerificationEtudiant v = client.verifierEtudiant(id);
        assertTrue(v.isExiste());
        assertFalse(v.isEligible());
        assertTrue(v.getMotif().contains("suspendu"));
        assertEquals("AMOA", v.getFiliere(), "filière transmise pour l'étape 6 du processus");
        assertEquals("2A", v.getNiveau());
    }

    // Fautes typées

    @Test
    void etudiantIntrouvableFault() {
        EtudiantIntrouvableFault f = assertThrows(EtudiantIntrouvableFault.class, () -> client.consulterEtudiant(404));
        assertEquals("ETUDIANT_INTROUVABLE", f.getFaultInfo().getCode());
        assertTrue(f.getFaultInfo().getMessage().contains("Aucun étudiant"));
    }

    @Test
    void donneesInvalidesFaultListeChaqueChamp() {
        Etudiant x = saisie();
        x.setMatricule("X");
        x.setNiveau("9Z");
        DonneesInvalidesFault f = assertThrows(DonneesInvalidesFault.class, () -> client.ajouterEtudiant(x));
        assertEquals("DONNEES_INVALIDES", f.getFaultInfo().getCode());
        assertEquals(2, f.getFaultInfo().getDetail().size());
    }

    @Test
    void unStatutInventeNEstPasIgnore() {
        Etudiant x = saisie();
        x.setStatut("INVENTE");
        DonneesInvalidesFault f = assertThrows(DonneesInvalidesFault.class, () -> client.ajouterEtudiant(x));
        assertTrue(f.getFaultInfo().getDetail().get(0).contains("statut"));
    }

    @Test
    void conflitFaultPourUnDoublon() throws Exception {
        client.ajouterEtudiant(saisie());
        ConflitFault f = assertThrows(ConflitFault.class, () -> client.ajouterEtudiant(saisie()));
        assertEquals("CONFLIT_DONNEES", f.getFaultInfo().getCode());
    }

    @Test
    void unIdentifiantNonNumeriqueDonneDonneesInvalides() throws Exception {
        HttpResponse<String> r = envoyer(jeton("soapui", "SoapUI#Ws2026"), "<t:consulterEtudiant><t:id>abc</t:id></t:consulterEtudiant>");
        assertEquals(500, r.statusCode());
        assertTrue(r.body().contains("donneesInvalidesFault"), r.body());
    }

    @Test
    void uneFauteNeContientJamaisDeTraceDePile() throws Exception {
        HttpResponse<String> r = envoyer(jeton("soapui", "SoapUI#Ws2026"), "<t:consulterEtudiant><t:id>404</t:id></t:consulterEtudiant>");
        assertFalse(r.body().contains("stackTrace"), r.body());
        assertFalse(r.body().contains("ma.inpt.stages"), r.body());
    }

    // WS-Security

    @Test
    void sansEnTeteDeSecuriteLAppelEstRefuse() throws Exception {
        HttpResponse<String> r = envoyer("", "<t:consulterEtudiant><t:id>1</t:id></t:consulterEtudiant>");
        assertEquals(500, r.statusCode());
        assertTrue(r.body().contains("wsse:InvalidSecurity"), r.body());
        assertTrue(r.body().contains("En-tête de sécurité"), r.body());
    }

    @Test
    void unMauvaisMotDePasseEstRefuse() throws Exception {
        HttpResponse<String> r = envoyer(jeton("soapui", "faux"), "<t:consulterEtudiant><t:id>1</t:id></t:consulterEtudiant>");
        assertTrue(r.body().contains("wsse:FailedAuthentication"), r.body());
    }

    @Test
    void unCompteInconnuEstRefuse() throws Exception {
        HttpResponse<String> r = envoyer(jeton("pirate", "SoapUI#Ws2026"), "<t:consulterEtudiant><t:id>1</t:id></t:consulterEtudiant>");
        assertTrue(r.body().contains("wsse:FailedAuthentication"), r.body());
    }

    @Test
    void unJetonIncompletEstRefuse() throws Exception {
        String sansMotDePasse = "<wsse:Security xmlns:wsse=\"" + WSSE + "\"><wsse:UsernameToken><wsse:Username>soapui</wsse:Username></wsse:UsernameToken></wsse:Security>";
        HttpResponse<String> r = envoyer(sansMotDePasse, "<t:consulterEtudiant><t:id>1</t:id></t:consulterEtudiant>");
        assertTrue(r.body().contains("wsse:InvalidSecurity"), r.body());
    }

    @Test
    void unMotDePasseDigestEstRefuse() throws Exception {
        String digest = "<wsse:Security xmlns:wsse=\"" + WSSE + "\"><wsse:UsernameToken><wsse:Username>soapui</wsse:Username>"
                + "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">x</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security>";
        HttpResponse<String> r = envoyer(digest, "<t:consulterEtudiant><t:id>1</t:id></t:consulterEtudiant>");
        assertTrue(r.body().contains("wsse:UnsupportedSecurityToken"), r.body());
    }

    @Test
    void lEnTeteMustUnderstandEstAccepte() throws Exception {
        String jeton = "<wsse:Security xmlns:wsse=\"" + WSSE + "\" soapenv:mustUnderstand=\"1\"><wsse:UsernameToken><wsse:Username>soapui</wsse:Username>"
                + "<wsse:Password>SoapUI#Ws2026</wsse:Password></wsse:UsernameToken></wsse:Security>";
        HttpResponse<String> r = envoyer(jeton, "<t:verifierEtudiant><t:id>404</t:id></t:verifierEtudiant>");
        assertEquals(200, r.statusCode(), r.body());
    }

    @Test
    void uneOperationInconnueDonneUneFauteEnFrancais() throws Exception {
        HttpResponse<String> r = envoyer(jeton("soapui", "SoapUI#Ws2026"), "<t:inconnue/>");
        assertEquals(500, r.statusCode());
        assertTrue(r.body().contains("n'existe pas"), r.body());
    }

    @Test
    void lesAccentsVoyagentEnUtf8() throws Exception {
        long id = dao.ajouter("SUSPENDU").id();
        HttpResponse<String> r = envoyer(jeton("soapui", "SoapUI#Ws2026"), "<t:verifierEtudiant><t:id>" + id + "</t:id></t:verifierEtudiant>");
        assertTrue(r.body().contains("l'étudiant"), r.body());
    }

    // Erreurs inattendues : un bug interne ne doit rien révéler au client.

    @Test
    void erreurInterneFaultNeRevelePasLeDetailTechnique() {
        EtudiantDao enPanne = (EtudiantDao) java.lang.reflect.Proxy.newProxyInstance(EtudiantDao.class.getClassLoader(), new Class<?>[] {EtudiantDao.class},
                (proxy, methode, args) -> {
                    throw new IllegalStateException("détail technique secret");
                });
        EtudiantsEndpoint endpoint = new EtudiantsEndpoint(new ServiceEtudiants(enPanne));
        ma.inpt.stages.contrats.etudiants.ErreurInterneFault f =
                assertThrows(ma.inpt.stages.contrats.etudiants.ErreurInterneFault.class, () -> endpoint.consulterEtudiant(1));
        assertEquals("ERREUR_INTERNE", f.getFaultInfo().getCode());
        assertFalse(f.getMessage().contains("secret"));
        assertFalse(f.getFaultInfo().getMessage().contains("secret"));
    }

    @Test
    void uneBaseEnPanneDonneServiceIndisponibleFault() {
        EtudiantDao enPanne = (EtudiantDao) java.lang.reflect.Proxy.newProxyInstance(EtudiantDao.class.getClassLoader(), new Class<?>[] {EtudiantDao.class},
                (proxy, methode, args) -> {
                    throw ma.inpt.stages.common.ErreurMetier.indisponible("BASE_INDISPONIBLE", "La base de données est momentanément indisponible.");
                });
        EtudiantsEndpoint endpoint = new EtudiantsEndpoint(new ServiceEtudiants(enPanne));
        assertEquals("BASE_INDISPONIBLE",
                assertThrows(ma.inpt.stages.contrats.etudiants.ServiceIndisponibleFault.class, () -> endpoint.consulterEtudiant(1)).getFaultInfo().getCode());
    }

    @Test
    void unXmlMalFormeNeProvoqueNiCrashNiTraceDePile() throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString("<soapenv:Envelope pas du xml")).build();
        HttpResponse<String> r = HTTP.send(requete, HttpResponse.BodyHandlers.ofString());
        assertTrue(r.statusCode() >= 400, "code " + r.statusCode());
        assertFalse(r.body().contains("stackTrace"), r.body());
        assertFalse(r.body().contains("\tat "), r.body());
        assertEquals(200, HTTP.send(HttpRequest.newBuilder(URI.create(url + "?wsdl")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
