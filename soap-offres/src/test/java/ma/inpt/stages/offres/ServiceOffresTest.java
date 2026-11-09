package ma.inpt.stages.offres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceOffresTest {

    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 8);
    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

    static final class DaoMemoire implements OffreDao {
        final Map<Long, Offre> lignes = new LinkedHashMap<>();
        Filtre dernierFiltre;
        private long suivant = 1;

        Offre ajouter(Offre o) {
            return inserer(o);
        }

        @Override
        public Offre inserer(Offre o) {
            Offre creee = o.avec(suivant++, o.statut());
            lignes.put(creee.id(), creee);
            return creee;
        }

        @Override
        public Optional<Offre> trouver(long id) {
            return Optional.ofNullable(lignes.get(id));
        }

        @Override
        public List<Offre> lister(Filtre f) {
            dernierFiltre = f;
            return new ArrayList<>(lignes.values());
        }

        @Override
        public void modifier(Offre o) {
            lignes.put(o.id(), o);
        }

        @Override
        public void supprimer(long id) {
            lignes.remove(id);
        }

        @Override
        public boolean decrementerPlaces(long id) {
            Offre o = lignes.get(id);
            if (!"ACTIVE".equals(o.statut()) || o.nombrePlaces() == 0) {
                return false;
            }
            int reste = o.nombrePlaces() - 1;
            lignes.put(id, o.avecPlaces(reste, reste == 0 ? "POURVUE" : "ACTIVE"));
            return true;
        }
    }

    static final class Externes implements ClientsExternes {
        ProfilEtudiant profil = new ProfilEtudiant("AMOA", "2A", List.of("SQL", "BPMN"));
        EtatEntreprise entreprise = new EtatEntreprise(true, true, null);

        @Override
        public Optional<ProfilEtudiant> profilEtudiant(long etudiantId) {
            return Optional.ofNullable(profil);
        }

        @Override
        public EtatEntreprise etatEntreprise(long entrepriseId) {
            return entreprise;
        }
    }

    static Offre offre(String statut, LocalDate limite, int places) {
        return new Offre(null, "Stage AMOA", "Description", List.of("SQL", "BPMN", "UML"), 12, limite, places,
                List.of("AMOA", "ASEDS"), "2A", "Rabat", 1, statut);
    }

    private final DaoMemoire dao = new DaoMemoire();
    private final Externes externes = new Externes();
    private final ServiceOffres service = new ServiceOffres(dao, externes, HORLOGE);

    private long ouverte() {
        return dao.ajouter(offre("ACTIVE", AUJOURDHUI.plusDays(10), 2)).id();
    }

    private String detailRefus(Offre o) {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.creer(o));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, e.type());
        return String.join(" ", e.details());
    }

    // Création

    @Test
    void uneNouvelleOffreEstEnBrouillonParDefaut() {
        assertEquals("BROUILLON", service.creer(offre(null, AUJOURDHUI.plusDays(1), 1)).statut());
    }

    @Test
    void uneOffrePeutEtrePublieeDesSaCreation() {
        assertEquals("ACTIVE", service.creer(offre("ACTIVE", AUJOURDHUI, 1)).statut());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POURVUE", "CLOTUREE"})
    void uneNouvelleOffreNePeutPasEtreDejaTerminee(String statut) {
        assertTrue(detailRefus(offre(statut, AUJOURDHUI.plusDays(1), 1)).contains("BROUILLON ou ACTIVE"));
    }

    @Test
    void uneNouvelleOffreDoitAvoirUnePlaceEtUneDateAVenir() {
        assertTrue(detailRefus(offre(null, AUJOURDHUI.plusDays(1), 0)).contains("au moins une place"));
        assertTrue(detailRefus(offre(null, AUJOURDHUI.minusDays(1), 1)).contains("ne peut pas être passée"));
    }

    @Test
    void lesChampsObligatoiresSontControles() {
        Offre vide = new Offre(null, "", "", List.of(), 0, null, -1, List.of(), "9Z", "", 0, null);
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.creer(vide));
        assertEquals(10, e.details().size(), e.details().toString());
    }

    @Test
    void uneFiliereInconnueEstRefusee() {
        Offre o = new Offre(null, "T", "D", List.of("SQL"), 4, AUJOURDHUI.plusDays(1), 1, List.of("Philo"), "1A", "Rabat", 1, null);
        assertTrue(detailRefus(o).contains("Une filière doit être"));
    }

    @Test
    void r2OnNePeutPasCreerUneOffrePourUneEntrepriseInactiveOuInconnue() {
        externes.entreprise = new ClientsExternes.EtatEntreprise(true, false, "L'entreprise n'est pas active.");
        assertTrue(detailRefus(offre(null, AUJOURDHUI.plusDays(1), 1)).contains("pas active"));
        externes.entreprise = new ClientsExternes.EtatEntreprise(false, false, "Aucune entreprise référencée.");
        assertTrue(detailRefus(offre(null, AUJOURDHUI.plusDays(1), 1)).contains("Aucune entreprise"));
        assertTrue(dao.lignes.isEmpty());
    }

    // Consultation et filtres

    @Test
    void uneOffreInconnueEstIntrouvable() {
        assertEquals("OFFRE_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.consulter(99)).code());
    }

    @Test
    void ouvertesSeulementUtiliseLaDateDuJour() {
        service.lister(new ServiceOffres.Criteres(null, null, null, null, null, null, null, true));
        assertEquals(AUJOURDHUI, dao.dernierFiltre.ouvertesAu());
        service.lister(new ServiceOffres.Criteres(null, null, null, null, null, null, null, false));
        assertNull(dao.dernierFiltre.ouvertesAu());
    }

    // Disponibilité : R2, R3, R4

    @Test
    void uneOffreOuverteEstDisponible() {
        ServiceOffres.Disponibilite d = service.verifierDisponibilite(ouverte());
        assertTrue(d.disponible());
        assertNull(d.regle());
        assertEquals(1L, d.entrepriseId());
    }

    @Test
    void r3LeDernierJourEstEncorePermis() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI, 2)).id();
        assertTrue(service.verifierDisponibilite(id).disponible());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BROUILLON", "POURVUE", "CLOTUREE"})
    void r2UneOffreNonActiveNEstPasDisponible(String statut) {
        long id = dao.ajouter(offre(statut, AUJOURDHUI.plusDays(5), 2)).id();
        ServiceOffres.Disponibilite d = service.verifierDisponibilite(id);
        assertFalse(d.disponible());
        assertFalse(d.active());
        assertEquals("R2", d.regle());
    }

    @Test
    void r3UneDateLimitePasseeRendIndisponible() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI.minusDays(1), 2)).id();
        ServiceOffres.Disponibilite d = service.verifierDisponibilite(id);
        assertFalse(d.disponible());
        assertFalse(d.dateLimiteRespectee());
        assertEquals("R3", d.regle());
    }

    @Test
    void r4UneOffreSansPlaceEstIndisponible() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI.plusDays(5), 0)).id();
        ServiceOffres.Disponibilite d = service.verifierDisponibilite(id);
        assertFalse(d.disponible());
        assertFalse(d.placesDisponibles());
        assertEquals("R4", d.regle());
    }

    @Test
    void uneOffreInconnueDonneExisteFauxSansFaute() {
        ServiceOffres.Disponibilite d = service.verifierDisponibilite(404);
        assertFalse(d.existe());
        assertFalse(d.disponible());
        assertEquals("R2", d.regle());
    }

    // Compatibilité : R7

    @Test
    void r7FiliereEtNiveauCorrectsSontCompatibles() {
        ServiceOffres.Compatibilite c = service.verifierCompatibilite(ouverte(), 1);
        assertTrue(c.compatible());
        assertNull(c.regle());
        assertEquals(67, c.scoreCompetences());
    }

    @Test
    void r7UneFiliereNonAccepteeEstIncompatible() {
        externes.profil = new ClientsExternes.ProfilEtudiant("ICCN", "3A", List.of());
        ServiceOffres.Compatibilite c = service.verifierCompatibilite(ouverte(), 1);
        assertFalse(c.compatible());
        assertFalse(c.filiereAcceptee());
        assertEquals("R7", c.regle());
        assertTrue(c.motif().contains("ICCN"));
    }

    @Test
    void r7UnNiveauInsuffisantEstIncompatible() {
        externes.profil = new ClientsExternes.ProfilEtudiant("AMOA", "1A", List.of());
        ServiceOffres.Compatibilite c = service.verifierCompatibilite(ouverte(), 1);
        assertFalse(c.compatible());
        assertFalse(c.niveauSuffisant());
        assertEquals("R7", c.regle());
    }

    @Test
    void r7UnNiveauSuperieurConvient() {
        externes.profil = new ClientsExternes.ProfilEtudiant("AMOA", "3A", List.of());
        assertTrue(service.verifierCompatibilite(ouverte(), 1).compatible());
    }

    @Test
    void r7UnScoreNulNeBloqueJamais() {
        externes.profil = new ClientsExternes.ProfilEtudiant("AMOA", "2A", List.of());
        ServiceOffres.Compatibilite c = service.verifierCompatibilite(ouverte(), 1);
        assertEquals(0, c.scoreCompetences());
        assertTrue(c.compatible());
    }

    @Test
    void r3LaCompatibiliteSignaleAussiLaDatePassee() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI.minusDays(1), 2)).id();
        ServiceOffres.Compatibilite c = service.verifierCompatibilite(id, 1);
        assertFalse(c.compatible());
        assertEquals("R3", c.regle());
    }

    @Test
    void compatibiliteAvecOffreOuEtudiantInconnu() {
        assertEquals("OFFRE_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.verifierCompatibilite(404, 1)).code());
        externes.profil = null;
        assertEquals("ETUDIANT_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.verifierCompatibilite(ouverte(), 1)).code());
    }

    @ParameterizedTest
    @CsvSource({"3,100", "2,67", "1,33", "0,0"})
    void leScoreEstLaPartDesCompetencesRequises(int possedees, int attendu) {
        List<String> requises = List.of("SQL", "BPMN", "UML");
        assertEquals(attendu, ServiceOffres.score(requises, requises.subList(0, possedees)));
    }

    @Test
    void leScoreIgnoreCasseEspacesEtCompetencesEnPlus() {
        assertEquals(100, ServiceOffres.score(List.of("SQL"), List.of(" sql ", "Docker")));
        assertEquals(100, ServiceOffres.score(List.of(), List.of()));
    }

    // Décompte des places : R10

    @Test
    void r10RetirerUnePlaceLaisseActiveTantQuIlEnReste() {
        Offre o = service.decrementerPlaces(ouverte());
        assertEquals(1, o.nombrePlaces());
        assertEquals("ACTIVE", o.statut());
    }

    @Test
    void r10LaDernierePlaceRendPourvue() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI.plusDays(5), 1)).id();
        Offre o = service.decrementerPlaces(id);
        assertEquals(0, o.nombrePlaces());
        assertEquals("POURVUE", o.statut());
    }

    @Test
    void r10SansPlaceLeDecompteDonneUnConflit() {
        long id = dao.ajouter(offre("ACTIVE", AUJOURDHUI.plusDays(5), 1)).id();
        service.decrementerPlaces(id);
        assertEquals("PLUS_DE_PLACE", assertThrows(ErreurMetier.class, () -> service.decrementerPlaces(id)).code());
    }

    // Modification et suppression

    @ParameterizedTest
    @CsvSource({"BROUILLON,ACTIVE", "BROUILLON,CLOTUREE", "ACTIVE,CLOTUREE", "ACTIVE,POURVUE", "POURVUE,ACTIVE", "POURVUE,CLOTUREE"})
    void lesTransitionsPrevuesSontPermises(String de, String vers) {
        long id = dao.ajouter(offre(de, AUJOURDHUI.plusDays(5), 2)).id();
        assertEquals(vers, service.modifier(id, offre(vers, AUJOURDHUI.plusDays(5), 2)).statut());
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,BROUILLON", "POURVUE,BROUILLON", "BROUILLON,POURVUE"})
    void lesAutresTransitionsDonnentUnConflit(String de, String vers) {
        long id = dao.ajouter(offre(de, AUJOURDHUI.plusDays(5), 2)).id();
        assertEquals("TRANSITION_INTERDITE", assertThrows(ErreurMetier.class, () -> service.modifier(id, offre(vers, AUJOURDHUI.plusDays(5), 2))).code());
    }

    @Test
    void uneOffreClotureeNeSeModifiePlus() {
        long id = dao.ajouter(offre("CLOTUREE", AUJOURDHUI.plusDays(5), 2)).id();
        assertEquals("OFFRE_CLOTUREE", assertThrows(ErreurMetier.class, () -> service.modifier(id, offre(null, AUJOURDHUI.plusDays(5), 2))).code());
    }

    @Test
    void lEntrepriseDUneOffreNeChangePas() {
        long id = ouverte();
        Offre autre = new Offre(null, "T", "D", List.of("SQL"), 4, AUJOURDHUI.plusDays(1), 1, List.of("AMOA"), "1A", "Rabat", 2, null);
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class, () -> service.modifier(id, autre)).type());
    }

    @Test
    void seuleUneOffreEnBrouillonSeSupprime() {
        long brouillon = dao.ajouter(offre("BROUILLON", AUJOURDHUI.plusDays(5), 2)).id();
        service.supprimer(brouillon);
        assertTrue(dao.trouver(brouillon).isEmpty());
        long active = ouverte();
        assertEquals("OFFRE_PUBLIEE", assertThrows(ErreurMetier.class, () -> service.supprimer(active)).code());
        assertTrue(dao.trouver(active).isPresent());
    }
}
