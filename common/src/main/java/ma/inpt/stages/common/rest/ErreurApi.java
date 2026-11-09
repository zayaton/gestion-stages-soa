package ma.inpt.stages.common.rest;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Corps d'erreur commun à tous les services REST. */
public record ErreurApi(String code, String message, List<String> details, String timestamp, String path) {

    public static ErreurApi de(String code, String message, List<String> details, String chemin) {
        return new ErreurApi(code, message, details == null ? List.of() : details,
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), chemin);
    }
}
