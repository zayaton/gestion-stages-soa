package ma.inpt.stages.common.rest;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Liste les opérations réellement exposées par des classes de ressources, sous la forme « GET /api/v1/stages/{id} ».
 * Les tests comparent cette liste au contrat openapi.yaml : une route oubliée dans la documentation fait échouer la construction.
 */
public final class VerificationContrat {

    private static final Map<Class<?>, String> METHODES = Map.of(
            GET.class, "GET", POST.class, "POST", PUT.class, "PUT", PATCH.class, "PATCH", DELETE.class, "DELETE");

    private VerificationContrat() {
    }

    public static Set<String> operations(Class<?>... ressources) {
        Set<String> resultat = new TreeSet<>();
        for (Class<?> classe : ressources) {
            Path base = classe.getAnnotation(Path.class);
            for (Method m : classe.getMethods()) {
                for (Map.Entry<Class<?>, String> verbe : METHODES.entrySet()) {
                    if (m.isAnnotationPresent(verbe.getKey().asSubclass(java.lang.annotation.Annotation.class))) {
                        Path sous = m.getAnnotation(Path.class);
                        resultat.add(verbe.getValue() + " " + joindre(base == null ? "" : base.value(), sous == null ? "" : sous.value()));
                    }
                }
            }
        }
        return resultat;
    }

    private static String joindre(String a, String b) {
        String chemin = ("/" + a + "/" + b).replaceAll("/+", "/");
        // Une expression régulière dans un paramètre ({id: [0-9]+}) se documente comme {id}.
        chemin = chemin.replaceAll("\\{\\s*([A-Za-z0-9_]+)\\s*:[^}]*}", "{$1}");
        return chemin.length() > 1 && chemin.endsWith("/") ? chemin.substring(0, chemin.length() - 1) : chemin;
    }
}
