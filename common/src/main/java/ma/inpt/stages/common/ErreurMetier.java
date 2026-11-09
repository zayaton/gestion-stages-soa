package ma.inpt.stages.common;

import java.util.List;

/** Erreur prévue par le métier. Chaque service la traduit en faute SOAP typée ou en réponse HTTP. */
public final class ErreurMetier extends RuntimeException {

    /**
     * Correspondance HTTP : INTROUVABLE 404, REQUETE_INVALIDE 400 (requête mal formée), DONNEES_INVALIDES 422
     * (requête lisible mais contraire aux règles de format), NON_AUTHENTIFIE 401, INTERDIT 403, CONFLIT 409, INDISPONIBLE 503.
     */
    public enum Type { INTROUVABLE, REQUETE_INVALIDE, DONNEES_INVALIDES, NON_AUTHENTIFIE, INTERDIT, CONFLIT, INDISPONIBLE }

    private final Type type;
    private final String code;
    private final List<String> details;

    private ErreurMetier(Type type, String code, String message, List<String> details) {
        super(message);
        this.type = type;
        this.code = code;
        this.details = details;
    }

    public static ErreurMetier introuvable(String code, String message) {
        return new ErreurMetier(Type.INTROUVABLE, code, message, List.of());
    }

    public static ErreurMetier donneesInvalides(String code, String message, List<String> details) {
        return new ErreurMetier(Type.DONNEES_INVALIDES, code, message, details);
    }

    public static ErreurMetier requeteInvalide(String message, List<String> details) {
        return new ErreurMetier(Type.REQUETE_INVALIDE, "REQUETE_INVALIDE", message, details);
    }

    public static ErreurMetier nonAuthentifie(String message) {
        return new ErreurMetier(Type.NON_AUTHENTIFIE, "NON_AUTHENTIFIE", message, List.of());
    }

    public static ErreurMetier interdit(String code, String message) {
        return new ErreurMetier(Type.INTERDIT, code, message, List.of());
    }

    public static ErreurMetier conflit(String code, String message) {
        return new ErreurMetier(Type.CONFLIT, code, message, List.of());
    }

    /** Reprend telle quelle une erreur reçue d'un autre service (même type, code, message et détails). */
    public static ErreurMetier de(Type type, String code, String message, List<String> details) {
        return new ErreurMetier(type, code, message, details == null ? List.of() : List.copyOf(details));
    }

    public static ErreurMetier indisponible(String code, String message) {
        return new ErreurMetier(Type.INDISPONIBLE, code, message, List.of());
    }

    public Type type() {
        return type;
    }

    public String code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}
