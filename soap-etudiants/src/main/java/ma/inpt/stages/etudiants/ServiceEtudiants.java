package ma.inpt.stages.etudiants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;

/** Règles de gestion du service S1. */
public final class ServiceEtudiants {

    /** Résultat de la vérification utilisée à l'étape 2 du processus (règle R1). */
    public record Verification(boolean existe, String statut, boolean eligible, String regle, String motif, String nomComplet, String email,
                               String filiere, String niveau) {
    }

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ACTIF", Set.of("SUSPENDU", "EN_STAGE", "DIPLOME"),
            "SUSPENDU", Set.of("ACTIF"),
            "EN_STAGE", Set.of("ACTIF", "DIPLOME"),
            "DIPLOME", Set.of());

    private final EtudiantDao dao;

    public ServiceEtudiants(EtudiantDao dao) {
        this.dao = dao;
    }

    public Etudiant ajouter(Etudiant saisie) {
        Etudiant etudiant = saisie.avec(null, saisie.statut() == null ? "ACTIF" : saisie.statut());
        Validateur.verifier(etudiant);
        return dao.inserer(etudiant);
    }

    public Etudiant consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable(
                "ETUDIANT_INTROUVABLE", "Aucun étudiant ne correspond à l'identifiant " + id + "."));
    }

    public List<Etudiant> lister(EtudiantDao.Filtre filtre) {
        return dao.lister(filtre);
    }

    public Etudiant modifier(long id, Etudiant saisie) {
        Etudiant existant = consulter(id);
        Etudiant etudiant = saisie.avec(id, saisie.statut() == null ? existant.statut() : saisie.statut());
        Validateur.verifier(etudiant);
        verifierTransition(existant.statut(), etudiant.statut());
        dao.modifier(etudiant);
        return etudiant;
    }

    public void supprimer(long id) {
        if ("EN_STAGE".equals(consulter(id).statut())) {
            throw ErreurMetier.conflit("ETUDIANT_EN_STAGE", "Un étudiant en stage ne peut pas être supprimé.");
        }
        dao.supprimer(id);
    }

    public Verification verifier(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).map(ServiceEtudiants::evaluer).orElseGet(() ->
                new Verification(false, null, false, "R1", "Aucun étudiant ne correspond à l'identifiant " + id + ".", null, null, null, null));
    }

    private static Verification evaluer(Etudiant e) {
        String motif = switch (e.statut()) {
            case "ACTIF" -> null;
            case "SUSPENDU" -> "Le compte de l'étudiant est suspendu : il ne peut pas candidater.";
            case "EN_STAGE" -> "L'étudiant est déjà en stage : il ne peut pas candidater.";
            default -> "L'étudiant est diplômé : il ne peut pas candidater.";
        };
        return new Verification(true, e.statut(), motif == null, motif == null ? null : "R1", motif, e.prenom() + " " + e.nom(), e.email(),
                e.filiere(), e.niveau());
    }

    public Etudiant changerStatut(long id, String statut) {
        if (statut == null || !TRANSITIONS.containsKey(statut)) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("Le statut doit être ACTIF, SUSPENDU, EN_STAGE ou DIPLOME."));
        }
        Etudiant existant = consulter(id);
        verifierTransition(existant.statut(), statut);
        if (!statut.equals(existant.statut())) {
            dao.changerStatut(id, statut);
        }
        return existant.avec(id, statut);
    }

    /** Conserver le statut courant est toujours permis : relancer une étape du processus reste sans effet. */
    private static void verifierTransition(String actuel, String nouveau) {
        if (!actuel.equals(nouveau) && !TRANSITIONS.get(actuel).contains(nouveau)) {
            throw ErreurMetier.conflit("TRANSITION_INTERDITE", "Le passage du statut " + actuel + " au statut " + nouveau + " n'est pas autorisé.");
        }
    }
}
