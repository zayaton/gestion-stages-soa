package ma.inpt.stages.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/** Bean Validation : les messages sont écrits en français directement sur les annotations. */
public final class Validateur {

    private static final jakarta.validation.Validator VALIDATEUR = creer();

    private Validateur() {
    }

    private static jakarta.validation.Validator creer() {
        // Sans EL : les messages n'ont pas d'expression, inutile d'embarquer un moteur.
        ValidatorFactory fabrique = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        return fabrique.getValidator();
    }

    /** Un identifiant absent ou non numérique arrive à 0 depuis le XML : il ne doit pas passer pour « introuvable ». */
    public static void verifierIdentifiant(long id) {
        if (id <= 0) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("L'identifiant doit être un nombre entier positif."));
        }
    }

    /** Lève une erreur DONNEES_INVALIDES listant chaque champ incorrect. */
    public static void verifier(Object objet) {
        List<String> messages = VALIDATEUR.validate(objet).stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .sorted()
                .toList();
        if (!messages.isEmpty()) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", messages);
        }
    }
}
