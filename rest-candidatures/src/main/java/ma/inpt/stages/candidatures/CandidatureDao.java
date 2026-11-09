package ma.inpt.stages.candidatures;

import java.util.List;
import java.util.Optional;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.Historique;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.Soumission;

/** Accès aux données du service S4. */
public interface CandidatureDao {

    record Filtre(String statut, Long etudiantId, Long offreId, Long entrepriseId) {
    }

    /** Résultat de l'enregistrement : la candidature, ou la règle qui l'a empêché (R5 ou R6). */
    record Enregistrement(Candidature candidature, String regleViolee, Long candidatureExistante) {
    }

    // Soumissions

    Soumission creerSoumission(long etudiantId, long offreId, String lettre);

    Optional<Soumission> trouverSoumission(long id);

    List<Soumission> soumissionsEtudiant(long etudiantId);

    void terminerSoumission(long id, String statut, String regle, String motif);

    /** Numéro du cas Bonita qui traite la soumission (mode bonita). */
    void definirCasBonita(long soumissionId, long casId);

    void ajouterEtape(long soumissionId, String etape, String resultat, String detail, String acteur);

    List<Etape> etapes(long soumissionId);

    // Candidatures

    /**
     * Enregistre la candidature si R5 et R6 sont respectées, en une transaction protégée par un verrou par étudiant :
     * deux dépôts simultanés ne peuvent pas dépasser la limite. Ajoute la première ligne d'historique.
     */
    Enregistrement enregistrer(NouvelleCandidature c, int limiteActives, String acteur);

    Optional<Candidature> trouver(long id);

    List<Candidature> lister(Filtre filtre);

    Optional<Candidature> candidatureNonAnnulee(long etudiantId, long offreId);

    int compterActives(long etudiantId);

    /** Change le statut seulement s'il vaut encore {@code attendu} ; faux si un autre traitement l'a changé entre-temps. */
    boolean changerStatut(long id, String attendu, String nouveau, String acteur, String commentaire);

    void ajouterHistorique(long id, String ancien, String nouveau, String acteur, String commentaire);

    List<Historique> historique(long id);
}
