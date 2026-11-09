package ma.inpt.stages.common.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import ma.inpt.stages.common.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reprend X-Correlation-Id (ou en crée un), le place dans les journaux et le renvoie dans la réponse.
 * Journalise aussi chaque requête : méthode, chemin, statut, durée et client.
 */
@PreMatching
@Priority(100)
public final class FiltreCorrelation implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger("acces");
    private static final String DEBUT = "soa.debut";

    @Override
    public void filter(ContainerRequestContext requete) {
        Correlation.definir(requete.getHeaderString(Correlation.ENTETE_HTTP));
        requete.setProperty(DEBUT, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requete, ContainerResponseContext reponse) {
        String id = Correlation.courant();
        if (id != null) {
            reponse.getHeaders().putSingle(Correlation.ENTETE_HTTP, id);
        }
        String chemin = requete.getUriInfo().getPath();
        if (!chemin.equals("health") && !chemin.startsWith("docs")) {
            Object debut = requete.getProperty(DEBUT);
            long ms = debut == null ? -1 : (System.nanoTime() - (Long) debut) / 1_000_000;
            Object client = requete.getProperty(FiltreSecurite.PROPRIETE_CLIENT);
            LOG.info("{} /{} -> {} ({} ms, client {})", requete.getMethod(), chemin, reponse.getStatus(), ms, client == null ? "-" : client);
        }
        // Les fils de Grizzly sont réutilisés : l'identifiant ne doit pas passer à la requête suivante.
        Correlation.effacer();
    }
}
