package ma.inpt.stages.entreprises;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ma.inpt.stages.common.ComptesService;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.ServeurSoap;
import ma.inpt.stages.common.VerificateurSante;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.entreprises.ConflitFault;
import ma.inpt.stages.contrats.entreprises.DonneesInvalidesFault;
import ma.inpt.stages.contrats.entreprises.Entreprise;
import ma.inpt.stages.contrats.entreprises.EntrepriseIntrouvableFault;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.EntreprisesService;
import ma.inpt.stages.contrats.entreprises.OffreResume;
import ma.inpt.stages.contrats.entreprises.ResponsableContact;
import ma.inpt.stages.contrats.entreprises.ServiceIndisponibleFault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Vrai échange SOAP sur HTTP, avec le DAO en mémoire et un S3 simulé. */
class EntreprisesSoapTest {

    private static int portLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private final List<ClientOffres.OffreResume> offres = new ArrayList<>();
    private boolean s3EnPanne;
    private ServeurSoap serveur;
    private EntreprisesPortType client;

    @BeforeEach
    void demarrer() throws IOException {
        int port = portLibre();
        Configuration config = Configuration.charger();
        ClientOffres s3 = id -> {
            if (s3EnPanne) {
                throw ErreurMetier.indisponible("SERVICE_OFFRES_INDISPONIBLE", "Le service des offres est momentanément indisponible.");
            }
            return offres;
        };
        serveur = new ServeurSoap("127.0.0.1", port, new VerificateurSante("S2", () -> true));
        serveur.publier("/ws/entreprises", new EntreprisesEndpoint(new ServiceEntreprises(new ServiceEntreprisesTest.DaoMemoire(), s3)),
                new ComptesService(config.valeur("soa.ws.comptes")));
        serveur.demarrer();
        client = ClientSoap.configurer(new EntreprisesService().getEntreprisesPort(), "http://127.0.0.1:" + port + "/ws/entreprises", config);
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    private static Entreprise saisie() {
        Entreprise x = new Entreprise();
        x.setRaisonSociale("Énergies du Sud");
        x.setIce("001234567000011");
        x.setSecteur("Énergie");
        x.setAdresse("1 rue Test");
        x.setVille("Agadir");
        x.setEmail("contact@sud.ma");
        x.setTelephone("0528000000");
        ResponsableContact r = new ResponsableContact();
        r.setNom("Hind Test");
        r.setFonction("RH");
        r.setEmail("hind@sud.ma");
        r.setTelephone("0661000000");
        x.setResponsable(r);
        return x;
    }

    @Test
    void cycleDeVieComplet() throws Exception {
        Entreprise creee = client.ajouterEntreprise(saisie());
        assertEquals("ACTIVE", creee.getStatut());
        long id = creee.getId();

        Entreprise lue = client.consulterEntreprise(id);
        assertEquals("Énergies du Sud", lue.getRaisonSociale());
        assertEquals("Hind Test", lue.getResponsable().getNom());

        assertTrue(client.verifierEntreprise(id).isActive());

        Entreprise modification = saisie();
        modification.setStatut("INACTIVE");
        assertEquals("INACTIVE", client.modifierEntreprise(id, modification).getStatut());
        assertFalse(client.verifierEntreprise(id).isActive());

        assertEquals(1, client.listerEntreprises(null).size());
        client.supprimerEntreprise(id);
        assertTrue(client.listerEntreprises(null).isEmpty());
    }

    @Test
    void consulterOffresEntrepriseRestitueLaReponseDeS3() throws Exception {
        long id = client.ajouterEntreprise(saisie()).getId();
        offres.add(new ClientOffres.OffreResume(7, "Stage IoT", "ACTIVE", LocalDate.of(2026, 12, 1), 2, "Agadir"));
        List<OffreResume> liste = client.consulterOffresEntreprise(id);
        assertEquals(1, liste.size());
        assertEquals("Stage IoT", liste.get(0).getTitre());
        assertEquals("2026-12-01", liste.get(0).getDateLimite().toXMLFormat());
    }

    @Test
    void entrepriseIntrouvableFault() {
        assertEquals("ENTREPRISE_INTROUVABLE",
                assertThrows(EntrepriseIntrouvableFault.class, () -> client.consulterEntreprise(404)).getFaultInfo().getCode());
    }

    @Test
    void donneesInvalidesFault() {
        Entreprise x = saisie();
        x.setIce("123");
        assertEquals("DONNEES_INVALIDES", assertThrows(DonneesInvalidesFault.class, () -> client.ajouterEntreprise(x)).getFaultInfo().getCode());
    }

    @Test
    void entrepriseSansResponsableDonneDonneesInvalides() {
        Entreprise x = saisie();
        x.setResponsable(null);
        assertEquals(4, assertThrows(DonneesInvalidesFault.class, () -> client.ajouterEntreprise(x)).getFaultInfo().getDetail().size());
    }

    @Test
    void conflitFault() throws Exception {
        client.ajouterEntreprise(saisie());
        assertEquals("CONFLIT_DONNEES", assertThrows(ConflitFault.class, () -> client.ajouterEntreprise(saisie())).getFaultInfo().getCode());
    }

    @Test
    void s3EnPanneDonneServiceIndisponibleFault() throws Exception {
        long id = client.ajouterEntreprise(saisie()).getId();
        s3EnPanne = true;
        assertEquals("SERVICE_OFFRES_INDISPONIBLE",
                assertThrows(ServiceIndisponibleFault.class, () -> client.consulterOffresEntreprise(id)).getFaultInfo().getCode());
    }

    @Test
    void leVraiClientS3TraduitUnePanneEnIndisponibilite() throws Exception {
        System.setProperty("soa.ws.url.offres", "http://127.0.0.1:" + portLibre() + "/ws/offres");
        try {
            ClientOffresSoap vraiClient = new ClientOffresSoap(Configuration.charger());
            ErreurMetier e = assertTimeout(Duration.ofSeconds(10), () -> assertThrows(ErreurMetier.class, () -> vraiClient.offresDe(1)));
            assertEquals(ErreurMetier.Type.INDISPONIBLE, e.type());
        } finally {
            System.clearProperty("soa.ws.url.offres");
        }
    }

    @Test
    void erreurInterneFaultNeRevelePasLeDetailTechnique() {
        EntrepriseDao enPanne = (EntrepriseDao) java.lang.reflect.Proxy.newProxyInstance(EntrepriseDao.class.getClassLoader(), new Class<?>[] {EntrepriseDao.class},
                (proxy, methode, args) -> {
                    throw new IllegalStateException("détail technique secret");
                });
        EntreprisesEndpoint endpoint = new EntreprisesEndpoint(new ServiceEntreprises(enPanne, id -> List.of()));
        ma.inpt.stages.contrats.entreprises.ErreurInterneFault f =
                assertThrows(ma.inpt.stages.contrats.entreprises.ErreurInterneFault.class, () -> endpoint.consulterEntreprise(1));
        assertEquals("ERREUR_INTERNE", f.getFaultInfo().getCode());
        assertFalse(f.getMessage().contains("secret"));
    }
}
