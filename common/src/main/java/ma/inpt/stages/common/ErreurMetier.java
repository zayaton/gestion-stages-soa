package ma.inpt.stages.common;

import java.util.List;

/** Erreur prévue par le métier. Chaque service la traduit en faute SOAP typée ou en réponse HTTP. */
public final class ErreurMetier extends RuntimeException {

    public enum Type { INTROUVABLE, DONNEES_INVALIDES, CONFLIT, INDISPONIBLE }

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

    public static ErreurMetier conflit(String code, String message) {
        return new ErreurMetier(Type.CONFLIT, code, message, List.of());
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
