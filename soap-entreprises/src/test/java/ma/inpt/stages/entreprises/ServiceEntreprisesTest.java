package ma.inpt.stages.entreprises;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.Test;

class ServiceEntreprisesTest {

    static final class DaoMemoire implements EntrepriseDao {
        final Map<Long, Entreprise> lignes = new LinkedHashMap<>();
        private long suivant = 1;

        @Override
        public Entreprise inserer(Entreprise e) {
            if (lignes.values().stream().anyMatch(x -> x.ice().equals(e.ice()) || x.email().equals(e.email()))) {
                throw ErreurMetier.conflit("CONFLIT_DONNEES", "Cet ICE ou cette adresse e-mail est déjà utilisé.");
            }
            Entreprise cree = e.avec(suivant++, e.statut());
            lignes.put(cree.id(), cree);
            return cree;
        }

        @Override
        public Optional<Entreprise> trouver(long id) {
            return Optional.ofNullable(lignes.get(id));
        }

        @Override
        public List<Entreprise> lister(Filtre f) {
            return new ArrayList<>(lignes.values());
        }

        @Override
        public void modifier(Entreprise e) {
            lignes.put(e.id(), e);
        }

        @Override
        public void supprimer(long id) {
            lignes.remove(id);
        }
    }

    static Entreprise saisie(String ice, String email, String statut) {
        return new Entreprise(null, "Atlas Test", ice, "Conseil", "1 rue Test", "Rabat", email, "0537000000",
                "Hind Test", "RH", "hind@test.ma", "0661000000", statut);
    }

    private final DaoMemoire dao = new DaoMemoire();
    private final List<ClientOffres.OffreResume> offres = new ArrayList<>();
    private final ServiceEntreprises service = new ServiceEntreprises(dao, id -> offres);

    private Entreprise valide() {
        return saisie("001234567000011", "contact@test.ma", null);
    }

    private void refuseAvec(Entreprise e, String message) {
        ErreurMetier erreur = assertThrows(ErreurMetier.class, () -> service.ajouter(e));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, erreur.type());
        assertTrue(erreur.details().contains(message), erreur.details().toString());
    }

    @Test
    void uneNouvelleEntrepriseEstActiveParDefaut() {
        assertEquals("ACTIVE", service.ajouter(valide()).statut());
    }

    @Test
    void lIceDoitCompter15Chiffres() {
        refuseAvec(saisie("123", "c@test.ma", null), "L'ICE doit compter exactement 15 chiffres.");
    }

    @Test
    void leResponsableDeContactEstObligatoire() {
        Entreprise sans = new Entreprise(null, "X", "001234567000011", "S", "A", "V", "c@test.ma", "0537000000", null, null, null, null, null);
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.ajouter(sans));
        assertEquals(4, e.details().size(), e.details().toString());
    }

    @Test
    void unStatutInconnuEstRefuse() {
        refuseAvec(saisie("001234567000011", "c@test.ma", "FERMEE"), "Le statut doit être ACTIVE ou INACTIVE.");
    }

    @Test
    void unIceEnDoubleDonneUnConflit() {
        service.ajouter(valide());
        assertEquals(ErreurMetier.Type.CONFLIT, assertThrows(ErreurMetier.class, () -> service.ajouter(valide())).type());
    }

    @Test
    void uneEntrepriseInconnueEstIntrouvable() {
        assertEquals("ENTREPRISE_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.consulter(99)).code());
    }

    @Test
    void modifierConserveLeStatutSiAbsentEtPeutDesactiver() {
        long id = service.ajouter(valide()).id();
        assertEquals("ACTIVE", service.modifier(id, valide()).statut());
        assertEquals("INACTIVE", service.modifier(id, saisie("001234567000011", "contact@test.ma", "INACTIVE")).statut());
    }

    // Règle R2

    @Test
    void r2UneEntrepriseActiveConvient() {
        long id = service.ajouter(valide()).id();
        ServiceEntreprises.Verification v = service.verifier(id);
        assertTrue(v.referencee());
        assertTrue(v.active());
        assertEquals(null, v.regle());
    }

    @Test
    void r2UneEntrepriseInactiveEstSignalee() {
        long id = service.ajouter(saisie("001234567000011", "c@test.ma", "INACTIVE")).id();
        ServiceEntreprises.Verification v = service.verifier(id);
        assertTrue(v.referencee());
        assertFalse(v.active());
        assertEquals("R2", v.regle());
    }

    @Test
    void r2UneEntrepriseInconnueNEstPasReferenceeSansFaute() {
        ServiceEntreprises.Verification v = service.verifier(404);
        assertFalse(v.referencee());
        assertFalse(v.active());
        assertEquals("R2", v.regle());
    }

    // Offres : S2 passe par S3

    private static ClientOffres.OffreResume offre() {
        return new ClientOffres.OffreResume(1, "Stage", "ACTIVE", LocalDate.of(2026, 12, 1), 2, "Rabat");
    }

    @Test
    void lesOffresViennentDuServiceS3() {
        long id = service.ajouter(valide()).id();
        offres.add(offre());
        assertEquals(1, service.offres(id).size());
    }

    @Test
    void lesOffresDUneEntrepriseInconnueSontRefusees() {
        assertEquals(ErreurMetier.Type.INTROUVABLE, assertThrows(ErreurMetier.class, () -> service.offres(404)).type());
    }

    @Test
    void uneEntrepriseAvecOffresNePeutPasEtreSupprimee() {
        long id = service.ajouter(valide()).id();
        offres.add(offre());
        assertEquals("ENTREPRISE_AVEC_OFFRES", assertThrows(ErreurMetier.class, () -> service.supprimer(id)).code());
        assertTrue(dao.trouver(id).isPresent());
    }

    @Test
    void uneEntrepriseSansOffrePeutEtreSupprimee() {
        long id = service.ajouter(valide()).id();
        service.supprimer(id);
        assertTrue(dao.trouver(id).isEmpty());
    }

    @Test
    void siS3EstEnPanneLaSuppressionEstRefuseeSansRienEffacer() {
        ServiceEntreprises enPanne = new ServiceEntreprises(dao, id -> {
            throw ErreurMetier.indisponible("SERVICE_OFFRES_INDISPONIBLE", "Le service des offres est momentanément indisponible.");
        });
        long id = enPanne.ajouter(valide()).id();
        assertEquals(ErreurMetier.Type.INDISPONIBLE, assertThrows(ErreurMetier.class, () -> enPanne.supprimer(id)).type());
        assertTrue(dao.trouver(id).isPresent());
    }
}
