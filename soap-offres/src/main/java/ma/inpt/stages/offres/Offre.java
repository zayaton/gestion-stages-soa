package ma.inpt.stages.offres;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import ma.inpt.stages.common.Referentiel;

/** Offre de stage et ses règles de format. Les règles d'état sont dans {@link ServiceOffres}. */
public record Offre(
        Long id,
        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères.")
        String titre,
        @NotBlank(message = "La description est obligatoire.")
        String description,
        @NotEmpty(message = "Indiquez au moins une compétence requise.")
        List<@NotBlank(message = "Une compétence ne peut pas être vide.")
             @Size(max = 60, message = "Une compétence ne doit pas dépasser 60 caractères.") String> competences,
        @Min(value = 1, message = "La durée doit être d'au moins une semaine.")
        int dureeSemaines,
        @NotNull(message = "La date limite de candidature est obligatoire.")
        LocalDate dateLimite,
        @Min(value = 0, message = "Le nombre de places ne peut pas être négatif.")
        int nombrePlaces,
        @NotEmpty(message = "Indiquez au moins une filière acceptée.")
        List<@Pattern(regexp = Referentiel.FILIERES, message = "Une filière doit être AMOA, ASEDS, DATA, SESNum, ICCN, Cloud ou Smart ICT.") String> filieres,
        @NotBlank(message = "Le niveau minimum est obligatoire.")
        @Pattern(regexp = Referentiel.NIVEAUX, message = "Le niveau minimum doit être 1A, 2A ou 3A.")
        String niveauMinimum,
        @NotBlank(message = "La ville est obligatoire.")
        @Size(max = 60, message = "La ville ne doit pas dépasser 60 caractères.")
        String ville,
        @Min(value = 1, message = "L'entreprise est obligatoire.")
        long entrepriseId,
        @Pattern(regexp = "BROUILLON|ACTIVE|POURVUE|CLOTUREE", message = "Le statut doit être BROUILLON, ACTIVE, POURVUE ou CLOTUREE.")
        String statut) {

    public Offre avec(Long nouvelId, String nouveauStatut) {
        return new Offre(nouvelId, titre, description, competences, dureeSemaines, dateLimite, nombrePlaces, filieres,
                niveauMinimum, ville, entrepriseId, nouveauStatut);
    }

    public Offre avecPlaces(int places, String nouveauStatut) {
        return new Offre(id, titre, description, competences, dureeSemaines, dateLimite, places, filieres,
                niveauMinimum, ville, entrepriseId, nouveauStatut);
    }
}
