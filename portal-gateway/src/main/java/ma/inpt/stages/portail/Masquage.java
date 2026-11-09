package ma.inpt.stages.portail;

/**
 * Masquage des coordonnées d'un étudiant quand une entreprise consulte son dossier. Appliqué par le serveur :
 * la valeur complète n'est jamais envoyée au navigateur de l'entreprise.
 */
public final class Masquage {

    private Masquage() {
    }

    /** 0612345612 devient 06******12. */
    public static String telephone(String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return telephone;
        }
        String t = telephone.trim();
        if (t.length() <= 4) {
            return "*".repeat(t.length());
        }
        return t.substring(0, 2) + "*".repeat(t.length() - 4) + t.substring(t.length() - 2);
    }

    /** hiba.elamdaoui@inpt.ac.ma devient h***@inpt.ac.ma. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String e = email.trim();
        int arobase = e.indexOf('@');
        if (arobase < 1) {
            return "***";
        }
        return e.charAt(0) + "***" + e.substring(arobase);
    }
}
