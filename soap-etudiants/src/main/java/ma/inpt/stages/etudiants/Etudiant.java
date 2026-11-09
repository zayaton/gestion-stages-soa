package ma.inpt.stages.etudiants;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.inpt.stages.common.Referentiel;

/** Étudiant et ses règles de format. Les règles d'état sont dans {@link ServiceEtudiants}. */
public record Etudiant(
        Long id,
        @NotBlank(message = "Le matricule est obligatoire.")
        @Pattern(regexp = "INPT-[0-9]{4}-[0-9]{4}", message = "Le matricule doit respecter le format INPT-AAAA-NNNN.")
        String matricule,
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 80, message = "Le nom ne doit pas dépasser 80 caractères.")
        String nom,
        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 80, message = "Le prénom ne doit pas dépasser 80 caractères.")
        String prenom,
        @NotBlank(message = "La filière est obligatoire.")
        @Pattern(regexp = Referentiel.FILIERES, message = "La filière doit être AMOA, ASEDS, DATA, SESNum, ICCN, Cloud ou Smart ICT.")
        String filiere,
        @NotBlank(message = "Le niveau est obligatoire.")
        @Pattern(regexp = Referentiel.NIVEAUX, message = "Le niveau doit être 1A, 2A ou 3A.")
        String niveau,
        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail est invalide.")
        @Size(max = 150, message = "L'adresse e-mail ne doit pas dépasser 150 caractères.")
        String email,
        @NotBlank(message = "Le téléphone est obligatoire.")
        @Pattern(regexp = Referentiel.TELEPHONE, message = "Le téléphone doit compter 10 chiffres et commencer par 05, 06 ou 07.")
        String telephone,
        @NotBlank(message = "La ville est obligatoire.")
        @Size(max = 60, message = "La ville ne doit pas dépasser 60 caractères.")
        String ville,
        @Pattern(regexp = "ACTIF|SUSPENDU|EN_STAGE|DIPLOME", message = "Le statut doit être ACTIF, SUSPENDU, EN_STAGE ou DIPLOME.")
        String statut,
        List<@NotBlank(message = "Une compétence ne peut pas être vide.")
             @Size(max = 60, message = "Une compétence ne doit pas dépasser 60 caractères.") String> competences) {

    public Etudiant avec(Long nouvelId, String nouveauStatut) {
        return new Etudiant(nouvelId, matricule, nom, prenom, filiere, niveau, email, telephone, ville, nouveauStatut, competences);
    }
}
