package ma.inpt.stages.candidatures;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Données échangées par le service S4. */
public final class Modeles {

    private Modeles() {
    }

    /** Statuts d'une candidature et transitions permises (règle R9). */
    public static final List<String> STATUTS = List.of("DEPOSEE", "EN_COURS_EVALUATION", "ACCEPTEE", "REFUSEE", "ANNULEE");
    /** Candidatures comptées dans la limite de R6. */
    public static final List<String> ACTIFS = List.of("DEPOSEE", "EN_COURS_EVALUATION");

    public record Candidature(long id, Long soumissionId, long etudiantId, long offreId, long entrepriseId, String lettreMotivation,
                              Integer scoreCompetences, String statut, LocalDateTime creeLe, LocalDateTime modifieLe) {
    }

    public record Soumission(long id, long etudiantId, long offreId, String lettreMotivation, String statut, String regleRejetee,
                             String motifRejet, Long bonitaCaseId, Long candidatureId, LocalDateTime creeLe, List<Etape> etapes) {
    }

    public record Historique(String ancienStatut, String nouveauStatut, LocalDateTime date, String acteur, String commentaire) {
    }

    public record Etape(String etape, String resultat, String detail, LocalDateTime date, String acteur) {
    }

    /** Corps de POST /soumissions : le dépôt de l'étudiant, qui déclenche le processus. */
    public record NouvelleSoumission(
            @NotNull(message = "L'identifiant de l'étudiant est obligatoire.") @Positive(message = "L'identifiant de l'étudiant doit être positif.") Long etudiantId,
            @NotNull(message = "L'identifiant de l'offre est obligatoire.") @Positive(message = "L'identifiant de l'offre doit être positif.") Long offreId,
            @NotBlank(message = "La lettre de motivation est obligatoire.")
            @Size(min = 30, max = 5000, message = "La lettre de motivation doit compter entre 30 et 5000 caractères.") String lettreMotivation) {
    }

    /** Corps de POST /candidatures : l'enregistrement fait par le processus après la passerelle de décision. */
    public record NouvelleCandidature(
            @Positive(message = "L'identifiant de la soumission doit être positif.") Long soumissionId,
            @NotNull(message = "L'identifiant de l'étudiant est obligatoire.") @Positive(message = "L'identifiant de l'étudiant doit être positif.") Long etudiantId,
            @NotNull(message = "L'identifiant de l'offre est obligatoire.") @Positive(message = "L'identifiant de l'offre doit être positif.") Long offreId,
            @NotNull(message = "L'identifiant de l'entreprise est obligatoire.") @Positive(message = "L'identifiant de l'entreprise doit être positif.") Long entrepriseId,
            @NotBlank(message = "La lettre de motivation est obligatoire.") @Size(max = 5000, message = "La lettre de motivation ne doit pas dépasser 5000 caractères.") String lettreMotivation,
            @Min(value = 0, message = "Le score doit être compris entre 0 et 100.") @Max(value = 100, message = "Le score doit être compris entre 0 et 100.") Integer scoreCompetences) {
    }

    public record ChangementStatut(
            @NotBlank(message = "Le statut est obligatoire.") String statut,
            @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères.") String commentaire) {
    }

    /** Décision de l'entreprise (étape 7) : contrat de la tâche humaine Bonita. */
    public record Decision(
            @NotBlank(message = "La décision est obligatoire.") String decision,
            @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères.") String commentaire) {
    }

    /** Rejet constaté par le processus Bonita à la passerelle de décision. */
    public record Rejet(
            @NotBlank(message = "La règle est obligatoire.") String regle,
            @NotBlank(message = "Le motif est obligatoire.") @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.") String motif) {
    }

    /** Étape signalée par le processus Bonita, pour la vue BPMN en direct. */
    public record NouvelleEtape(
            @NotBlank(message = "L'étape est obligatoire.") @Size(max = 40, message = "Le nom d'étape ne doit pas dépasser 40 caractères.") String etape,
            @NotBlank(message = "Le résultat est obligatoire.") String resultat,
            @Size(max = 500, message = "Le détail ne doit pas dépasser 500 caractères.") String detail) {
    }

    public record VerificationDoublon(long etudiantId, long offreId, boolean doublon, Long candidatureId) {
    }

    public record CompteActives(long etudiantId, int actives, int limite, boolean limiteAtteinte) {
    }

    /** Réponse d'une décision : la candidature et le résultat de chaque étape exécutée. */
    public record ResultatDecision(Candidature candidature, List<Etape> etapes) {
    }
}
