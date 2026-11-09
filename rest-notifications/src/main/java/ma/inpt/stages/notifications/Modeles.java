package ma.inpt.stages.notifications;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Données échangées par le service S5. */
public final class Modeles {

    private Modeles() {
    }

    /** Corps de POST /notifications : le type choisit le gabarit, les variables le remplissent. */
    public record NouvelleNotification(
            @NotBlank(message = "Le type de notification est obligatoire.") String type,
            @NotBlank(message = "Le destinataire est obligatoire.")
            @Email(message = "L'adresse du destinataire est invalide.")
            @Size(max = 150, message = "L'adresse du destinataire ne doit pas dépasser 150 caractères.") String destinataire,
            Map<String, String> variables,
            @Positive(message = "L'identifiant de la candidature doit être positif.") Long candidatureId,
            @Positive(message = "L'identifiant de l'offre doit être positif.") Long offreId) {
    }

    public record Notification(long id, String type, String destinataire, String sujet, String corps, LocalDateTime dateEnvoi,
                               String statut, String erreur, Long candidatureId, Long offreId, String correlationId) {
    }

    /** Message prêt à envoyer. */
    public record Message(String sujet, String html, String texte) {
    }

    /** Bilan d'une exécution des rappels de date limite. */
    public record BilanRappels(int joursAvantLimite, int offresConcernees, int rappelsEnvoyes, int dejaEnvoyes, int echecs,
                               List<LigneRappel> details) {
    }

    public record LigneRappel(long offreId, String titreOffre, String destinataire, String resultat) {
    }

    /** Offre ouverte vue par S5 (données venant de S3). */
    public record OffreOuverte(long id, String titre, java.time.LocalDate dateLimite, List<String> filieres, String niveauMinimum,
                               long entrepriseId) {
    }

    /** Étudiant vu par S5 (données venant de S1). */
    public record EtudiantActif(long id, String nomComplet, String email, String filiere, String niveau) {
    }

    static Map<String, String> sansNull(Map<String, String> variables) {
        return variables == null ? Map.of() : variables;
    }
}
