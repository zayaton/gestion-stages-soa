package ma.inpt.stages.suivi;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Données échangées par le service S6. */
public final class Modeles {

    private Modeles() {
    }

    public record Stage(long id, long candidatureId, long etudiantId, long offreId, long entrepriseId,
                        LocalDate dateDebut, LocalDate dateFin, String encadrantEntreprise, String encadrantPedagogique,
                        String statut, LocalDateTime creeLe) {
    }

    /** Corps de POST /stages, envoyé par le processus à l'étape 9 (règle R10). */
    public record NouveauStage(
            @NotNull(message = "L'identifiant de la candidature est obligatoire.") @Positive(message = "L'identifiant de la candidature doit être positif.") Long candidatureId,
            @NotNull(message = "L'identifiant de l'étudiant est obligatoire.") @Positive(message = "L'identifiant de l'étudiant doit être positif.") Long etudiantId,
            @NotNull(message = "L'identifiant de l'offre est obligatoire.") @Positive(message = "L'identifiant de l'offre doit être positif.") Long offreId,
            @NotNull(message = "L'identifiant de l'entreprise est obligatoire.") @Positive(message = "L'identifiant de l'entreprise doit être positif.") Long entrepriseId,
            @NotNull(message = "La date de début est obligatoire.") LocalDate dateDebut,
            @NotNull(message = "La date de fin est obligatoire.") LocalDate dateFin,
            @Size(max = 120, message = "Le nom de l'encadrant entreprise ne doit pas dépasser 120 caractères.") String encadrantEntreprise,
            @Size(max = 120, message = "Le nom de l'encadrant pédagogique ne doit pas dépasser 120 caractères.") String encadrantPedagogique) {
    }

    public record ChangementStatut(
            @NotBlank(message = "Le statut est obligatoire.") String statut,
            @Size(max = 300, message = "Le commentaire ne doit pas dépasser 300 caractères.") String commentaire) {
    }

    public record NouveauCompteRendu(
            @NotBlank(message = "La période est obligatoire.") @Size(max = 60, message = "La période ne doit pas dépasser 60 caractères.") String periode,
            @NotBlank(message = "Le contenu du compte rendu est obligatoire.") @Size(max = 10000, message = "Le contenu ne doit pas dépasser 10 000 caractères.") String contenu,
            @NotBlank(message = "L'auteur est obligatoire.") @Size(max = 120, message = "L'auteur ne doit pas dépasser 120 caractères.") String auteur) {
    }

    public record CompteRendu(long id, long stageId, String periode, String contenu, String auteur, LocalDateTime creeLe) {
    }

    public record NouvelleEvaluation(
            @NotNull(message = "La note est obligatoire.")
            @DecimalMin(value = "0", message = "La note doit être comprise entre 0 et 20.")
            @DecimalMax(value = "20", message = "La note doit être comprise entre 0 et 20.")
            @Digits(integer = 2, fraction = 2, message = "La note a au plus deux décimales.") BigDecimal note,
            @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères.") String commentaire,
            @NotBlank(message = "L'évaluateur est obligatoire.") @Size(max = 120, message = "L'évaluateur ne doit pas dépasser 120 caractères.") String evaluateur) {
    }

    public record Evaluation(long id, long stageId, BigDecimal note, String commentaire, String evaluateur, LocalDateTime creeLe) {
    }

    public record Operation(String operation, String detail, LocalDateTime date, String acteur) {
    }

    /** Résultat de la création : le stage, et s'il existait déjà (appel répété par le processus). */
    public record Creation(Stage stage, boolean existait) {
    }
}
