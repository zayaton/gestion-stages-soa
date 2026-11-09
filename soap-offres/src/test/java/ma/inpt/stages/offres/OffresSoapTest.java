package ma.inpt.stages.offres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.ComptesService;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.offres.Compatibilite;
import ma.inpt.stages.contrats.offres.ConflitFault;
import ma.inpt.stages.contrats.offres.Disponibilite;
import ma.inpt.stages.contrats.offres.DonneesInvalidesFault;
import ma.inpt.stages.contrats.offres.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.contrats.offres.Offre;
import ma.inpt.stages.contrats.offres.OffreIntrouvableFault;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.OffresService;
import ma.inpt.stages.contrats.offres.ServiceIndisponibleFault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrai échange SOAP sur HTTP, avec le DAO en mémoire et S1, S2 simulés. */
class OffresSoapTest {

    private static int portLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private final ServiceOffresTest.Externes externes = new ServiceOffresTest.Externes();
    private ServeurSoap serveur;
    private OffresPortType client;

    @BeforeEach
    void demarrer() throws IOException {
        int port = portLibre();
        Configuration config = Configuration.charger();
        ServiceOffres service = new ServiceOffres(new ServiceOffresTest.DaoMemoire(), externes, ServiceOffresTest.HORLOGE);
        serveur = new ServeurSoap("127.0.0.1", port, new VerificateurSante("S3", () -> true));
        serveur.publier("/ws/offres", new OffresEndpoint(service), new ComptesService(config.valeur("soa.ws.comptes")));
        serveur.demarrer();
        client = ClientSoap.configurer(new OffresService().getOffresPort(), "http://127.0.0.1:" + port + "/ws/offres", config);
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private static Offre saisie(String statut, int places) {
        Offre x = new Offre();
        x.setTitre("Stage réseau");
        x.setDescription("Mission détaillée");
        x.getCompetence().addAll(List.of("SQL", "BPMN"));
        x.setDureeSemaines(12);
        x.setDateLimite(Dates.versXml(ServiceOffresTest.AUJOURDHUI.plusDays(10)));
        x.setNombrePlaces(places);
        x.getFiliere().addAll(List.of("AMOA", "ICCN"));
        x.setNiveauMinimum("2A");
        x.setVille("Fès");
        x.setEntrepriseId(1L);
        x.setStatut(statut);
        return x;
    }

    @Test
    void cycleDeVieComplet() throws Exception {
        Offre creee = client.creerOffre(saisie(null, 2));
        assertEquals("BROUILLON", creee.getStatut());
        long id = creee.getId();

        Offre lue = client.consulterOffre(id);
        assertEquals("Stage réseau", lue.getTitre());
        assertEquals(List.of("SQL", "BPMN"), lue.getCompetence());
        assertEquals(ServiceOffresTest.AUJOURDHUI.plusDays(10), Dates.versLocalDate(lue.getDateLimite()));

        assertEquals("ACTIVE", client.modifierOffre(id, saisie("ACTIVE", 2)).getStatut());
        FiltreOffres filtre = new FiltreOffres();
        filtre.setOuvertesSeulement(true);
        assertEquals(1, client.listerOffres(filtre).size());
        assertEquals(1, client.listerOffres(null).size());
    }

    @Test
    void verifierDisponibiliteEtCompatibilite() throws Exception {
        long id = client.creerOffre(saisie("ACTIVE", 2)).getId();
        Disponibilite d = client.verifierDisponibilite(id);
        assertTrue(d.isDisponible());
        assertEquals(2, d.getNombrePlaces());

        Compatibilite c = client.verifierCompatibilite(id, 1);
        assertTrue(c.isCompatible());
        assertEquals(100, c.getScoreCompetences());
    }

    @Test
    void decrementerJusquAPourvuePuisConflit() throws Exception {
        long id = client.creerOffre(saisie("ACTIVE", 1)).getId();
        Offre o = client.decrementerPlaces(id);
        assertEquals(0, o.getNombrePlaces());
        assertEquals("POURVUE", o.getStatut());
        assertEquals("PLUS_DE_PLACE", assertThrows(ConflitFault.class, () -> client.decrementerPlaces(id)).getFaultInfo().getCode());
    }

    @Test
    void offreIntrouvableFault() {
        assertEquals("OFFRE_INTROUVABLE", assertThrows(OffreIntrouvableFault.class, () -> client.consulterOffre(404)).getFaultInfo().getCode());
    }

    @Test
    void etudiantIntrouvableFault() throws Exception {
        long id = client.creerOffre(saisie("ACTIVE", 1)).getId();
        externes.profil = null;
        assertEquals("ETUDIANT_INTROUVABLE", assertThrows(EtudiantIntrouvableFault.class, () -> client.verifierCompatibilite(id, 404)).getFaultInfo().getCode());
    }

    @Test
    void donneesInvalidesFault() {
        Offre vide = new Offre();
        DonneesInvalidesFault f = assertThrows(DonneesInvalidesFault.class, () -> client.creerOffre(vide));
        assertFalse(f.getFaultInfo().getDetail().isEmpty());
    }

    @Test
    void conflitFaultSurSuppressionDUneOffrePubliee() throws Exception {
        long id = client.creerOffre(saisie("ACTIVE", 1)).getId();
        assertEquals("OFFRE_PUBLIEE", assertThrows(ConflitFault.class, () -> client.supprimerOffre(id)).getFaultInfo().getCode());
    }

    @Test
    void uneOffreEnBrouillonSeSupprime() throws Exception {
        long id = client.creerOffre(saisie(null, 1)).getId();
        client.supprimerOffre(id);
        assertThrows(OffreIntrouvableFault.class, () -> client.consulterOffre(id));
    }

    @Test
    void serviceIndisponibleFaultQuandS1NeRepondPas() throws Exception {
        ClientsExternes panne = new ClientsExternes() {
            @Override
            public Optional<ProfilEtudiant> profilEtudiant(long etudiantId) {
                throw ErreurMetier.indisponible("SERVICE_ETUDIANTS_INDISPONIBLE", "Le service des étudiants est momentanément indisponible.");
            }

            @Override
            public EtatEntreprise etatEntreprise(long entrepriseId) {
                return new EtatEntreprise(true, true, null);
            }
        };
        int port = portLibre();
        Configuration config = Configuration.charger();
        ServiceOffresTest.DaoMemoire dao = new ServiceOffresTest.DaoMemoire();
        dao.ajouter(ServiceOffresTest.offre("ACTIVE", ServiceOffresTest.AUJOURDHUI.plusDays(1), 1));
        try (ServeurSoap autre = new ServeurSoap("127.0.0.1", port, new VerificateurSante("S3", () -> true))) {
            autre.publier("/ws/offres", new OffresEndpoint(new ServiceOffres(dao, panne, ServiceOffresTest.HORLOGE)), new ComptesService(config.valeur("soa.ws.comptes")));
            autre.demarrer();
            OffresPortType c = ClientSoap.configurer(new OffresService().getOffresPort(), "http://127.0.0.1:" + port + "/ws/offres", config);
            assertEquals("SERVICE_ETUDIANTS_INDISPONIBLE",
                    assertThrows(ServiceIndisponibleFault.class, () -> c.verifierCompatibilite(1, 1)).getFaultInfo().getCode());
        }
    }

    @Test
    void lesVraisClientsTraduisentUnePanneEnIndisponibilite() throws Exception {
        System.setProperty("soa.ws.url.etudiants", "http://127.0.0.1:" + portLibre() + "/ws/etudiants");
        System.setProperty("soa.ws.url.entreprises", "http://127.0.0.1:" + portLibre() + "/ws/entreprises");
        try {
            ClientsExternesSoap clients = new ClientsExternesSoap(Configuration.charger());
            ErreurMetier e1 = assertTimeout(Duration.ofSeconds(10), () -> assertThrows(ErreurMetier.class, () -> clients.profilEtudiant(1)));
            assertEquals("SERVICE_ETUDIANTS_INDISPONIBLE", e1.code());
            ErreurMetier e2 = assertTimeout(Duration.ofSeconds(10), () -> assertThrows(ErreurMetier.class, () -> clients.etatEntreprise(1)));
            assertEquals("SERVICE_ENTREPRISES_INDISPONIBLE", e2.code());
        } finally {
            System.clearProperty("soa.ws.url.etudiants");
            System.clearProperty("soa.ws.url.entreprises");
        }
    }

    @Test
    void erreurInterneFaultNeRevelePasLeDetailTechnique() {
        OffreDao enPanne = (OffreDao) java.lang.reflect.Proxy.newProxyInstance(OffreDao.class.getClassLoader(), new Class<?>[] {OffreDao.class},
                (proxy, methode, args) -> {
                    throw new IllegalStateException("détail technique secret");
                });
        OffresEndpoint endpoint = new OffresEndpoint(new ServiceOffres(enPanne, externes, ServiceOffresTest.HORLOGE));
        ma.inpt.stages.contrats.offres.ErreurInterneFault f =
                assertThrows(ma.inpt.stages.contrats.offres.ErreurInterneFault.class, () -> endpoint.consulterOffre(1));
        assertEquals("ERREUR_INTERNE", f.getFaultInfo().getCode());
        assertFalse(f.getMessage().contains("secret"));
    }
}
