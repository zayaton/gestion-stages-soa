package ma.inpt.stages.candidatures;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.ChangementStatut;
import ma.inpt.stages.candidatures.Modeles.CompteActives;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.Historique;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.NouvelleEtape;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.Rejet;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.candidatures.Modeles.VerificationDoublon;
import ma.inpt.stages.common.ServeurRest;
import ma.inpt.stages.common.rest.Acteur;
import ma.inpt.stages.common.rest.ClientsAutorises;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/** API REST du service S4. Une instance par requête ; règles dans {@link ServiceCandidatures}, processus dans {@link Orchestrateur}. */
@Path(ServeurRest.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class RessourceCandidatures {

    private final ServiceCandidatures service;
    private final Orchestrateur orchestrateur;

    @Context
    private ContainerRequestContext requete;

    @Context
    private UriInfo uri;

    @Inject
    public RessourceCandidatures(ServiceCandidatures service, Orchestrateur orchestrateur) {
        this.service = service;
        this.orchestrateur = orchestrateur;
    }

    private String acteur() {
        return Acteur.de(requete);
    }

    // Soumissions : dépôt de l'étudiant et suivi du processus

    /** Démarre le processus. 202 : la soumission est acceptée ; son statut dit si la candidature est enregistrée ou rejetée. */
    @POST
    @Path("soumissions")
    @ClientsAutorises({"portail", "postman"})
    public Response deposer(NouvelleSoumission demande) {
        Soumission s = orchestrateur.deposer(demande, acteur());
        return Response.accepted(s).location(uri.getBaseUriBuilder().path("api/v1/soumissions/{id}").build(s.id())).build();
    }

    @GET
    @Path("soumissions/{id}")
    public Soumission soumission(@PathParam("id") long id) {
        return service.soumission(id);
    }

    @GET
    @Path("soumissions/{id}/etapes")
    public List<Etape> etapesSoumission(@PathParam("id") long id) {
        return service.soumission(id).etapes();
    }

    @POST
    @Path("soumissions/{id}/etapes")
    @ClientsAutorises({"bonita", "services", "postman"})
    public Response signalerEtape(@PathParam("id") long id, NouvelleEtape etape) {
        return Response.status(201).entity(service.enregistrerEtape(id, etape, acteur())).build();
    }

    @POST
    @Path("soumissions/{id}/rejet")
    @ClientsAutorises({"bonita", "services", "postman"})
    public Soumission rejeter(@PathParam("id") long id, Rejet rejet) {
        return service.rejeter(id, rejet, acteur());
    }

    @GET
    @Path("etudiants/{id}/soumissions")
    public List<Soumission> soumissionsEtudiant(@PathParam("id") long id) {
        return service.soumissionsEtudiant(id);
    }

    // Candidatures

    /** Enregistrement par le processus, après la passerelle de décision. */
    @POST
    @Path("candidatures")
    @ClientsAutorises({"bonita", "services", "postman"})
    public Response enregistrer(NouvelleCandidature saisie) {
        Candidature c = service.enregistrer(saisie, acteur());
        return Response.created(uri.getBaseUriBuilder().path("api/v1/candidatures/{id}").build(c.id())).entity(c).build();
    }

    @GET
    @Path("candidatures")
    public List<Candidature> lister(@QueryParam("statut") String statut, @QueryParam("etudiantId") Long etudiantId,
                                    @QueryParam("offreId") Long offreId, @QueryParam("entrepriseId") Long entrepriseId) {
        return service.lister(statut, etudiantId, offreId, entrepriseId);
    }

    @GET
    @Path("candidatures/verification-doublon")
    public VerificationDoublon verifierDoublon(@QueryParam("etudiantId") Long etudiantId, @QueryParam("offreId") Long offreId) {
        return service.verifierDoublon(etudiantId, offreId);
    }

    @GET
    @Path("candidatures/{id}")
    public Candidature consulter(@PathParam("id") long id) {
        return service.consulter(id);
    }

    @GET
    @Path("candidatures/{id}/historique")
    public List<Historique> historique(@PathParam("id") long id) {
        return service.historique(id);
    }

    @GET
    @Path("candidatures/{id}/etapes")
    public List<Etape> etapes(@PathParam("id") long id) {
        return service.etapesCandidature(id);
    }

    /** Changement de statut brut, soumis à R9. Utilisé par le processus Bonita. */
    @PATCH
    @Path("candidatures/{id}/statut")
    @ClientsAutorises({"bonita", "services", "postman"})
    public Candidature changerStatut(@PathParam("id") long id, ChangementStatut demande) {
        return service.changerStatut(id, demande, acteur());
    }

    /** Annulation par l'étudiant (R8) : la candidature passe ANNULEE et reste consultable. */
    @DELETE
    @Path("candidatures/{id}")
    public Response annuler(@PathParam("id") long id) {
        orchestrateur.annuler(id, acteur());
        return Response.noContent().build();
    }

    @POST
    @Path("candidatures/{id}/evaluation")
    @ClientsAutorises({"portail", "bonita", "postman"})
    public Candidature demarrerEvaluation(@PathParam("id") long id) {
        return service.demarrerEvaluation(id, acteur());
    }

    @POST
    @Path("candidatures/{id}/decision")
    @ClientsAutorises({"portail", "postman"})
    public ResultatDecision decider(@PathParam("id") long id, Decision decision) {
        return orchestrateur.decider(id, decision, acteur());
    }

    @POST
    @Path("candidatures/{id}/suivi/reprendre")
    @ClientsAutorises({"portail", "services", "postman"})
    public ResultatDecision reprendreSuivi(@PathParam("id") long id) {
        return orchestrateur.reprendreSuivi(id, acteur());
    }

    @GET
    @Path("etudiants/{id}/candidatures")
    public List<Candidature> candidaturesEtudiant(@PathParam("id") long id) {
        return service.lister(null, positif(id), null, null);
    }

    @GET
    @Path("etudiants/{id}/candidatures/actives/compte")
    public CompteActives compterActives(@PathParam("id") long id) {
        return service.compterActives(id);
    }

    @GET
    @Path("offres/{id}/candidatures")
    public List<Candidature> candidaturesOffre(@PathParam("id") long id) {
        return service.lister(null, null, positif(id), null);
    }

    @GET
    @Path("entreprises/{id}/candidatures")
    public List<Candidature> candidaturesEntreprise(@PathParam("id") long id, @QueryParam("statut") String statut) {
        return service.lister(statut, null, null, positif(id));
    }

    private static long positif(long id) {
        ma.inpt.stages.common.Validateur.verifierIdentifiant(id);
        return id;
    }

    public static ResourceConfig configuration(ServiceCandidatures service, Orchestrateur orchestrateur) {
        return new ResourceConfig().register(RessourceCandidatures.class).register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(service).to(ServiceCandidatures.class);
                bind(orchestrateur).to(Orchestrateur.class);
            }
        });
    }
}
