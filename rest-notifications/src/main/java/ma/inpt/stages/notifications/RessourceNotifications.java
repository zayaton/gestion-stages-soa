package ma.inpt.stages.notifications;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.rest.ClientsAutorises;
import ma.inpt.stages.notifications.Modeles.BilanRappels;
import ma.inpt.stages.notifications.Modeles.NouvelleNotification;
import ma.inpt.stages.notifications.Modeles.Notification;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/** API REST du service S5. Une instance par requête ; les règles sont dans {@link ServiceNotifications}. */
@Path(ServeurRest.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class RessourceNotifications {

    private final ServiceNotifications service;

    @Context
    private UriInfo uri;

    @Inject
    public RessourceNotifications(ServiceNotifications service) {
        this.service = service;
    }

    /** Réservé au processus et aux services : le portail ne fait pas envoyer de courriel arbitraire. */
    @POST
    @Path("notifications")
    @ClientsAutorises({"bonita", "services", "postman"})
    public Response envoyer(NouvelleNotification demande) {
        Notification n = service.envoyer(demande);
        return Response.created(uri.getBaseUriBuilder().path("api/v1/notifications/{id}").build(n.id())).entity(n).build();
    }

    @GET
    @Path("notifications")
    public List<Notification> lister(@QueryParam("destinataire") String destinataire, @QueryParam("candidatureId") Long candidatureId,
                                     @QueryParam("type") String type, @QueryParam("limite") Integer limite) {
        return service.lister(destinataire, candidatureId, type, limite);
    }

    @GET
    @Path("notifications/{id}")
    public Notification consulter(@PathParam("id") long id) {
        return service.consulter(id);
    }

    @POST
    @Path("rappels/executer")
    @ClientsAutorises({"portail", "services", "postman"})
    public BilanRappels executerRappels(@QueryParam("jours") @DefaultValue("3") int jours) {
        return service.executerRappels(jours);
    }

    public static ResourceConfig configuration(ServiceNotifications service) {
        return new ResourceConfig().register(RessourceNotifications.class).register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(service).to(ServiceNotifications.class);
            }
        });
    }
}
