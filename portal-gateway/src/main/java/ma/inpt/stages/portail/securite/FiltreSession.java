package ma.inpt.stages.portail.securite;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import java.util.Set;
import ma.inpt.stages.common.ErreurMetier;

/**
 * Sécurité du portail : jeton de session (Authorization: Bearer) puis rôle. Ne s'applique qu'aux ressources annotées
 * {@link RolesAutorises} ; la connexion et les fichiers de l'interface restent publics.
 * Sans jeton valide : 401. Rôle non autorisé : 403.
 */
public final class FiltreSession implements DynamicFeature {

    public static final String PROPRIETE_UTILISATEUR = "portail.utilisateur";

    private final ServiceAuthentification auth;

    public FiltreSession(ServiceAuthentification auth) {
        this.auth = auth;
    }

    @Override
    public void configure(ResourceInfo info, FeatureContext contexte) {
        RolesAutorises roles = info.getResourceMethod().getAnnotation(RolesAutorises.class);
        if (roles == null) {
            roles = info.getResourceClass().getAnnotation(RolesAutorises.class);
        }
        if (roles != null) {
            contexte.register(new Controle(auth, Set.of(roles.value())));
        }
    }

    /** Jeton extrait de l'en-tête Authorization, ou null. */
    public static String jeton(ContainerRequestContext requete) {
        String entete = requete.getHeaderString("Authorization");
        if (entete == null || !entete.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return entete.substring(7).trim();
    }

    @Priority(Priorities.AUTHENTICATION)
    static final class Controle implements ContainerRequestFilter {

        private final ServiceAuthentification auth;
        private final Set<String> roles;

        Controle(ServiceAuthentification auth, Set<String> roles) {
            this.auth = auth;
            this.roles = roles;
        }

        @Override
        public void filter(ContainerRequestContext requete) {
            // Les exceptions levées ici passent par le MappeurErreurs : même corps JSON que partout ailleurs.
            Utilisateur u = auth.verifier(jeton(requete));
            if (!roles.contains(u.role())) {
                throw ErreurMetier.interdit("ACCES_INTERDIT", "Cet espace est réservé : votre rôle (" + u.role() + ") n'y donne pas accès.");
            }
            requete.setProperty(PROPRIETE_UTILISATEUR, u);
        }
    }
}
