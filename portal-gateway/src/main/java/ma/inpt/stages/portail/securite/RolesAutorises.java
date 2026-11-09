package ma.inpt.stages.portail.securite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rôles autorisés sur une ressource du portail. Le contrôle est fait par le serveur, jamais par le navigateur. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RolesAutorises {

    String[] value();
}
