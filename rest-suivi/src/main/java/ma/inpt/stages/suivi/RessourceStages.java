package ma.inpt.stages.suivi;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.rest.Acteur;
import ma.inpt.stages.suivi.Modeles.ChangementStatut;
import ma.inpt.stages.suivi.Modeles.CompteRendu;
import ma.inpt.stages.suivi.Modeles.Creation;
import ma.inpt.stages.suivi.Modeles.Evaluation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/** API REST du service S6. Une instance par requête ; les règles sont dans {@link ServiceSuivi}, partagé. */
@Path(ServeurRest.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class RessourceStages {

    private final ServiceSuivi service;

    @Context
    private ContainerRequestContext requete;

    @Context
    private UriInfo uri;

    @Inject
    public RessourceStages(ServiceSuivi service) {
        this.service = service;
    }

    @POST
    @Path("stages")
    public Response creer(NouveauStage saisie) {
        Creation c = service.creer(saisie, Acteur.de(requete));
        if (c.existait()) {
            return Response.ok(c.stage()).build();
        }
        return Response.created(uri.getBaseUriBuilder().path("api/v1/stages/{id}").build(c.stage().id())).entity(c.stage()).build();
    }

    @GET
    @Path("stages")
    public List<Stage> lister(@QueryParam("statut") String statut, @QueryParam("etudiantId") Long etudiantId,
                              @QueryParam("entrepriseId") Long entrepriseId) {
        return service.lister(statut, etudiantId, entrepriseId);
    }

    @GET
    @Path("stages/{id}")
    public Stage consulter(@PathParam("id") long id) {
        return service.consulter(id);
    }

    @PATCH
    @Path("stages/{id}/statut")
    public Stage changerStatut(@PathParam("id") long id, ChangementStatut demande) {
        return service.changerStatut(id, demande, Acteur.de(requete));
    }

    @POST
    @Path("stages/{id}/comptes-rendus")
    public Response ajouterCompteRendu(@PathParam("id") long id, NouveauCompteRendu saisie) {
        CompteRendu cr = service.ajouterCompteRendu(id, saisie, Acteur.de(requete));
        return Response.created(uri.getAbsolutePathBuilder().path(String.valueOf(cr.id())).build()).entity(cr).build();
    }

    @GET
    @Path("stages/{id}/comptes-rendus")
    public List<CompteRendu> comptesRendus(@PathParam("id") long id) {
        return service.comptesRendus(id);
    }

    @POST
    @Path("stages/{id}/evaluations")
    public Response ajouterEvaluation(@PathParam("id") long id, NouvelleEvaluation saisie) {
        Evaluation ev = service.ajouterEvaluation(id, saisie, Acteur.de(requete));
        return Response.created(uri.getAbsolutePathBuilder().path(String.valueOf(ev.id())).build()).entity(ev).build();
    }

    @GET
    @Path("stages/{id}/evaluations")
    public List<Evaluation> evaluations(@PathParam("id") long id) {
        return service.evaluations(id);
    }

    @GET
    @Path("stages/{id}/historique")
    public List<Operation> historique(@PathParam("id") long id) {
        return service.historique(id);
    }

    @GET
    @Path("etudiants/{id}/stages")
    public List<Stage> stagesEtudiant(@PathParam("id") long id) {
        return service.historiqueEtudiant(id);
    }

    @GET
    @Path("candidatures/{id}/stage")
    public Stage stageCandidature(@PathParam("id") long id) {
        return service.parCandidature(id);
    }

    /** Enregistre la ressource et le service qu'elle utilise. */
    public static ResourceConfig configuration(ServiceSuivi service) {
        return new ResourceConfig().register(RessourceStages.class).register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(service).to(ServiceSuivi.class);
            }
        });
    }
}
