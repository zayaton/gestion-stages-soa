package ma.inpt.stages.portail.securite;

/** Personne connectée. referenceId : identifiant S1 (étudiant) ou S2 (entreprise) ; null pour le responsable. */
public record Utilisateur(long compteId, String identifiant, String role, Long referenceId) {

    public static final String ETUDIANT = "ETUDIANT";
    public static final String ENTREPRISE = "ENTREPRISE";
    public static final String RESPONSABLE = "RESPONSABLE";

    /** Valeur transmise aux services dans X-Acteur, pour leurs historiques. */
    public String acteur() {
        return referenceId == null ? role.toLowerCase() : role.toLowerCase() + ":" + referenceId;
    }
}
