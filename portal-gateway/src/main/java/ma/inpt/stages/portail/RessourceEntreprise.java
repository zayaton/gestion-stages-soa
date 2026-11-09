package ma.inpt.stages.portail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.contrats.offres.Offre;
import ma.inpt.stages.portail.Vues.Decision;
import ma.inpt.stages.portail.Vues.EntrepriseVue;
import ma.inpt.stages.portail.Vues.OffreVue;
import ma.inpt.stages.portail.securite.FiltreSession;
import ma.inpt.stages.portail.securite.RolesAutorises;
import ma.inpt.stages.portail.securite.Utilisateur;

/**
 * Espace entreprise. L'entreprise est celle de la session ; ses offres et ses candidatures seulement (404 sinon).
 * Les coordonnées des étudiants sont masquées par le serveur.
 */
@Path("/api/entreprise")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAutorises(Utilisateur.ENTREPRISE)
public final class RessourceEntreprise {

    private final Services services;

    @Context
    private ContainerRequestContext requete;

    @Inject
    public RessourceEntreprise(Services services) {
        this.services = services;
    }

    private Utilisateur moi() {
        return (Utilisateur) requete.getProperty(FiltreSession.PROPRIETE_UTILISATEUR);
    }

    private long id() {
        return moi().referenceId();
    }

    // Profil

    @GET
    @Path("profil")
    public EntrepriseVue profil() {
        return EntrepriseVue.de(Services.soap("entreprises", () -> services.entreprises.consulterEntreprise(id())));
    }

    /** Une entreprise met à jour ses coordonnées, pas son statut : il reste celui fixé par le responsable. */
    @PUT
    @Path("profil")
    public EntrepriseVue modifierProfil(EntrepriseVue saisie) {
        exiger(saisie);
        EntrepriseVue actuelle = profil();
        var soap = saisie.versSoap();
        soap.setStatut(actuelle.statut());
        soap.setIce(actuelle.ice());
        return EntrepriseVue.de(Services.soap("entreprises", () -> services.entreprises.modifierEntreprise(id(), soap)));
    }

    // Offres

    /** Offres de l'entreprise : S2 interroge S3 ; le portail ajoute le nombre de candidatures reçues (S4). */
    @GET
    @Path("offres")
    public ArrayNode offres() {
        Map<Long, Integer> recues = new HashMap<>();
        for (JsonNode c : Services.rest(services.candidatures.get("/entreprises/" + id() + "/candidatures"))) {
            if (!"ANNULEE".equals(c.path("statut").asText())) {
                recues.merge(c.path("offreId").asLong(), 1, Integer::sum);
            }
        }
        ArrayNode resultat = Json.MAPPEUR.createArrayNode();
        for (var o : Services.soap("entreprises", () -> services.entreprises.consulterOffresEntreprise(id()))) {
            ObjectNode n = resultat.addObject();
            n.put("id", o.getId());
            n.put("titre", o.getTitre());
            n.put("statut", o.getStatut());
            n.put("dateLimite", o.getDateLimite().toXMLFormat());
            n.put("nombrePlaces", o.getNombrePlaces());
            n.put("ville", o.getVille());
            n.put("candidatures", recues.getOrDefault(o.getId(), 0));
        }
        return resultat;
    }

    @GET
    @Path("offres/{id}")
    public OffreVue offre(@PathParam("id") long offreId) {
        return OffreVue.de(sienne(offreId), profil().raisonSociale(), null);
    }

    @POST
    @Path("offres")
    public Response creerOffre(OffreVue saisie) {
        exiger(saisie);
        Offre creee = Services.soap("offres", () -> services.offres.creerOffre(saisie.versSoap(id())));
        return Response.status(201).entity(OffreVue.de(creee, profil().raisonSociale(), null)).build();
    }

    @PUT
    @Path("offres/{id}")
    public OffreVue modifierOffre(@PathParam("id") long offreId, OffreVue saisie) {
        exiger(saisie);
        sienne(offreId);
        Offre modifiee = Services.soap("offres", () -> services.offres.modifierOffre(offreId, saisie.versSoap(id())));
        return OffreVue.de(modifiee, profil().raisonSociale(), null);
    }

    /** Clôture : l'offre n'accepte plus de candidature et reste consultable. */
    @POST
    @Path("offres/{id}/cloture")
    public OffreVue cloturer(@PathParam("id") long offreId) {
        Offre o = sienne(offreId);
        o.setStatut("CLOTUREE");
        Offre modifiee = Services.soap("offres", () -> services.offres.modifierOffre(offreId, o));
        return OffreVue.de(modifiee, profil().raisonSociale(), null);
    }

    private Offre sienne(long offreId) {
        Offre o = Services.soap("offres", () -> services.offres.consulterOffre(offreId));
        if (o.getEntrepriseId() != id()) {
            throw ErreurMetier.introuvable("OFFRE_INTROUVABLE", "Aucune offre ne correspond à l'identifiant " + offreId + ".");
        }
        return o;
    }

    // Candidatures reçues

    @GET
    @Path("candidatures")
    public ArrayNode candidatures(@QueryParam("statut") String statut) {
        String filtre = statut == null || statut.isBlank() ? "" : "?statut=" + statut;
        JsonNode liste = Services.rest(services.candidatures.get("/entreprises/" + id() + "/candidatures" + filtre));
        return new Composition(services).liste(liste, true, true);
    }

    @GET
    @Path("candidatures/{id}")
    public ObjectNode candidature(@PathParam("id") long candidatureId) {
        Composition c = new Composition(services);
        ObjectNode n = c.avecEtudiant(c.avecOffre(sienneCandidature(candidatureId)), true);
        n.set("historique", Services.rest(services.candidatures.get("/candidatures/" + candidatureId + "/historique")));
        n.set("etapes", Services.rest(services.candidatures.get("/candidatures/" + candidatureId + "/etapes")));
        return n;
    }

    /** Ouvre le dossier : DEPOSEE devient EN_COURS_EVALUATION ; l'étudiant ne peut plus annuler (R8). */
    @POST
    @Path("candidatures/{id}/evaluation")
    public JsonNode demarrerEvaluation(@PathParam("id") long candidatureId) {
        sienneCandidature(candidatureId);
        return Services.rest(services.candidatures.appeler("POST", "/candidatures/" + candidatureId + "/evaluation", null, moi().acteur()));
    }

    /** Décision (étape 7) : ACCEPTER, REFUSER ou DEMANDER_COMPLEMENT. S4 exécute ou fait exécuter les étapes suivantes. */
    @POST
    @Path("candidatures/{id}/decision")
    public JsonNode decider(@PathParam("id") long candidatureId, Decision decision) {
        exiger(decision);
        sienneCandidature(candidatureId);
        return Services.rest(services.candidatures.appeler("POST", "/candidatures/" + candidatureId + "/decision", decision, moi().acteur()));
    }

    private JsonNode sienneCandidature(long candidatureId) {
        JsonNode c;
        try {
            c = Services.rest(services.candidatures.get("/candidatures/" + candidatureId));
        } catch (ErreurMetier e) {
            throw e.type() == ErreurMetier.Type.INTROUVABLE || e.type() == ErreurMetier.Type.DONNEES_INVALIDES
                    ? RessourceEtudiant.introuvable(candidatureId) : e;
        }
        if (c.path("entrepriseId").asLong() != id()) {
            throw RessourceEtudiant.introuvable(candidatureId);
        }
        return c;
    }

    private static void exiger(Object corps) {
        if (corps == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of("Données attendues en JSON."));
        }
    }
}
