package ma.inpt.stages.common.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Contrôle de l'en-tête X-API-Key (401) puis, si la ressource le demande, du client autorisé (403). */
public final class FiltreSecurite {

    public static final String ENTETE = "X-API-Key";
    public static final String PROPRIETE_CLIENT = "soa.client";

    private static final Logger LOG = LoggerFactory.getLogger(FiltreSecurite.class);
    /** Accessibles sans clé : contrôle de santé et documentation. */
    private static final List<String> PUBLICS = List.of("health", "openapi.yaml", "docs");

    private FiltreSecurite() {
    }

    @Priority(Priorities.AUTHENTICATION)
    public static final class Authentification implements ContainerRequestFilter {

        private final ClesApi cles;

        public Authentification(ClesApi cles) {
            this.cles = cles;
        }

        @Override
        public void filter(ContainerRequestContext requete) {
            String chemin = requete.getUriInfo().getPath();
            if (PUBLICS.stream().anyMatch(p -> chemin.equals(p) || chemin.startsWith(p + "/"))) {
                return;
            }
            String cle = requete.getHeaderString(ENTETE);
            Optional<String> client = cles.client(cle);
            if (client.isEmpty()) {
                String message = cle == null || cle.isBlank() ? "En-tête X-API-Key absent." : "Clé d'API invalide.";
                LOG.warn("Requête refusée ({}) : {} {}", message, requete.getMethod(), chemin);
                requete.abortWith(reponse401(message, chemin));
                return;
            }
            requete.setProperty(PROPRIETE_CLIENT, client.get());
        }

        private static jakarta.ws.rs.core.Response reponse401(String message, String chemin) {
            jakarta.ws.rs.core.Response base = MappeurErreurs.reponse(401, ErreurApi.de("NON_AUTHENTIFIE", message,
                    List.of("Chaque appel doit porter l'en-tête X-API-Key avec la clé du client."), "/" + chemin));
            return jakarta.ws.rs.core.Response.fromResponse(base).header("WWW-Authenticate", "ApiKey header=\"X-API-Key\"").build();
        }
    }

    /** Branche le contrôle de client sur les seules méthodes annotées @ClientsAutorises. */
    public static final class Autorisation implements DynamicFeature {

        @Override
        public void configure(ResourceInfo info, FeatureContext contexte) {
            ClientsAutorises annotation = info.getResourceMethod().getAnnotation(ClientsAutorises.class);
            if (annotation == null) {
                annotation = info.getResourceClass().getAnnotation(ClientsAutorises.class);
            }
            if (annotation != null) {
                contexte.register(new ControleClient(Set.of(annotation.value())));
            }
        }
    }

    @Priority(Priorities.AUTHORIZATION)
    static final class ControleClient implements ContainerRequestFilter {

        private final Set<String> autorises;

        ControleClient(Set<String> autorises) {
            this.autorises = autorises;
        }

        @Override
        public void filter(ContainerRequestContext requete) {
            Object client = requete.getProperty(PROPRIETE_CLIENT);
            if (client == null || !autorises.contains(client.toString())) {
                String chemin = requete.getUriInfo().getPath();
                LOG.warn("Requête refusée au client {} : {} {}", client, requete.getMethod(), chemin);
                requete.abortWith(MappeurErreurs.reponse(403, ErreurApi.de("ACCES_INTERDIT",
                        "Ce client n'est pas autorisé à effectuer cette opération.",
                        List.of("Clients autorisés : " + String.join(", ", autorises.stream().sorted().toList()) + "."), "/" + chemin)));
            }
        }
    }
}
