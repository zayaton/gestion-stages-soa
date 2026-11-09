package ma.inpt.stages.candidatures;

import java.time.LocalDate;
import java.util.Map;

/**
 * Ce dont l'orchestration a besoin des autres services. S4 n'accède jamais à leurs schémas : il les appelle.
 * Une panne lève ErreurMetier de type INDISPONIBLE, nommant le service en cause.
 */
public interface Partenaires {

    record EtatEtudiant(boolean existe, boolean eligible, String motif, String nomComplet, String email, String filiere, String niveau) {
    }

    record EtatOffre(boolean existe, String titre, String statut, boolean dateLimiteRespectee, boolean placesDisponibles,
                     String motif, long entrepriseId, int dureeSemaines) {
    }

    record EtatEntreprise(boolean referencee, boolean active, String motif, String raisonSociale, String email, String contact) {
    }

    record Compatibilite(boolean filiereAcceptee, boolean niveauSuffisant, int score, String motif) {
    }

    /** S1 : existence, statut (R1) et profil. */
    EtatEtudiant etudiant(long etudiantId);

    /** S3 : offre et disponibilité (R2 statut, R3 date limite, R4 places). */
    EtatOffre offre(long offreId);

    /** S2 : entreprise référencée et active (R2). */
    EtatEntreprise entreprise(long entrepriseId);

    /** S3 (qui interroge S1) : filière et niveau (R7), score indicatif. */
    Compatibilite compatibilite(long offreId, long etudiantId);

    /** S3 : retire une place (R10). Lève CONFLIT « PLUS_DE_PLACE » s'il n'en reste plus. */
    void retirerPlace(long offreId);

    /** S1 : l'étudiant passe EN_STAGE (R10). Sans effet s'il l'est déjà. */
    void passerEnStage(long etudiantId);

    /** S6 : crée le dossier de stage (R10). Sans effet s'il existe déjà pour cette candidature. */
    void creerStage(long candidatureId, long etudiantId, long offreId, long entrepriseId, LocalDate debut, LocalDate fin, String encadrantEntreprise);

    /** S5 : envoie une notification. */
    void notifier(String type, String destinataire, Map<String, String> variables, Long candidatureId, Long offreId);
}
