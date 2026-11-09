package ma.inpt.stages.common.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restreint une ressource à certains clients. Une clé valide d'un autre client reçoit 403.
 * Exemple : seuls le processus (bonita) et les services enregistrent une candidature.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ClientsAutorises {

    String[] value();
}
