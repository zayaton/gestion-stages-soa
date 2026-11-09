package ma.inpt.stages.entreprises;

import java.util.List;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;

/** Règles de gestion du service S2. */
public final class ServiceEntreprises {

    /** Résultat de la vérification utilisée à l'étape 3 du processus (règle R2). */
    public record Verification(boolean referencee, boolean active, String regle, String motif, String raisonSociale, String email) {
    }

    private final EntrepriseDao dao;
    private final ClientOffres offres;

    public ServiceEntreprises(EntrepriseDao dao, ClientOffres offres) {
        this.dao = dao;
        this.offres = offres;
    }

    public Entreprise ajouter(Entreprise saisie) {
        Entreprise entreprise = saisie.avec(null, saisie.statut() == null ? "ACTIVE" : saisie.statut());
        Validateur.verifier(entreprise);
        return dao.inserer(entreprise);
    }

    public Entreprise consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable(
                "ENTREPRISE_INTROUVABLE", "Aucune entreprise ne correspond à l'identifiant " + id + "."));
    }

    public List<Entreprise> lister(EntrepriseDao.Filtre filtre) {
        return dao.lister(filtre);
    }

    public Entreprise modifier(long id, Entreprise saisie) {
        Entreprise existante = consulter(id);
        Entreprise entreprise = saisie.avec(id, saisie.statut() == null ? existante.statut() : saisie.statut());
        Validateur.verifier(entreprise);
        dao.modifier(entreprise);
        return entreprise;
    }

    public void supprimer(long id) {
        consulter(id);
        if (!offres.offresDe(id).isEmpty()) {
            throw ErreurMetier.conflit("ENTREPRISE_AVEC_OFFRES", "Une entreprise qui a des offres ne peut pas être supprimée : passez-la en INACTIVE.");
        }
        dao.supprimer(id);
    }

    public Verification verifier(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).map(e -> "ACTIVE".equals(e.statut())
                ? new Verification(true, true, null, null, e.raisonSociale(), e.email())
                : new Verification(true, false, "R2", "L'entreprise " + e.raisonSociale() + " n'est pas active.", e.raisonSociale(), e.email()))
                .orElseGet(() -> new Verification(false, false, "R2", "Aucune entreprise référencée ne correspond à l'identifiant " + id + ".", null, null));
    }

    public List<ClientOffres.OffreResume> offres(long id) {
        consulter(id);
        return offres.offresDe(id);
    }
}
