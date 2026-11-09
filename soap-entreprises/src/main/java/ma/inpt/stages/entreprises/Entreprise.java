package ma.inpt.stages.entreprises;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.inpt.stages.common.Referentiel;

/** Entreprise partenaire et ses règles de format. */
public record Entreprise(
        Long id,
        @NotBlank(message = "La raison sociale est obligatoire.")
        @Size(max = 150, message = "La raison sociale ne doit pas dépasser 150 caractères.")
        String raisonSociale,
        @NotBlank(message = "L'ICE est obligatoire.")
        @Pattern(regexp = "[0-9]{15}", message = "L'ICE doit compter exactement 15 chiffres.")
        String ice,
        @NotBlank(message = "Le secteur d'activité est obligatoire.")
        @Size(max = 80, message = "Le secteur ne doit pas dépasser 80 caractères.")
        String secteur,
        @NotBlank(message = "L'adresse est obligatoire.")
        @Size(max = 200, message = "L'adresse ne doit pas dépasser 200 caractères.")
        String adresse,
        @NotBlank(message = "La ville est obligatoire.")
        @Size(max = 60, message = "La ville ne doit pas dépasser 60 caractères.")
        String ville,
        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail de l'entreprise est invalide.")
        String email,
        @NotBlank(message = "Le téléphone est obligatoire.")
        @Pattern(regexp = Referentiel.TELEPHONE, message = "Le téléphone doit compter 10 chiffres et commencer par 05, 06 ou 07.")
        String telephone,
        @NotBlank(message = "Le nom du responsable de contact est obligatoire.")
        @Size(max = 120, message = "Le nom du responsable ne doit pas dépasser 120 caractères.")
        String contactNom,
        @NotBlank(message = "La fonction du responsable de contact est obligatoire.")
        @Size(max = 100, message = "La fonction du responsable ne doit pas dépasser 100 caractères.")
        String contactFonction,
        @NotBlank(message = "L'adresse e-mail du responsable est obligatoire.")
        @Email(message = "L'adresse e-mail du responsable est invalide.")
        String contactEmail,
        @NotBlank(message = "Le téléphone du responsable est obligatoire.")
        @Pattern(regexp = Referentiel.TELEPHONE, message = "Le téléphone du responsable doit compter 10 chiffres et commencer par 05, 06 ou 07.")
        String contactTelephone,
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "Le statut doit être ACTIVE ou INACTIVE.")
        String statut) {

    public Entreprise avec(Long nouvelId, String nouveauStatut) {
        return new Entreprise(nouvelId, raisonSociale, ice, secteur, adresse, ville, email, telephone,
                contactNom, contactFonction, contactEmail, contactTelephone, nouveauStatut);
    }
}
