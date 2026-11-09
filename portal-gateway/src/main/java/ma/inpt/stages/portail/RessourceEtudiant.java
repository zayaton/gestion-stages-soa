package ma.inpt.stages.portail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.contrats.offres.Offre;
import ma.inpt.stages.portail.Vues.Candidater;
import ma.inpt.stages.portail.Vues.CompatibiliteVue;
import ma.inpt.stages.portail.Vues.EtudiantVue;
import ma.inpt.stages.portail.Vues.OffreVue;
import ma.inpt.stages.portail.securite.FiltreSession;
import ma.inpt.stages.portail.securite.RolesAutorises;
import ma.inpt.stages.portail.securite.Utilisateur;

/** Espace étudiant. L'étudiant est toujours celui de la session : jamais un identifiant reçu du navigateur. */
@Path("/api/etudiant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAutorises(Utilisateur.ETUDIANT)
public final class RessourceEtudiant {

    private final Services services;

    @Context
    private ContainerRequestContext requete;

    @Inject
    public RessourceEtudiant(Services services) {
        this.services = services;
    }

    private Utilisateur moi() {
        return (Utilisateur) requete.getProperty(FiltreSession.PROPRIETE_UTILISATEUR);
    }

    private long id() {
        return moi().referenceId();
    }

    @GET
    @Path("profil")
    public EtudiantVue profil() {
        return EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.consulterEtudiant(id())));
    }

    /** Offres ouvertes, avec la compatibilité de chacune pour l'étudiant connecté (R7 et score indicatif). */
    @GET
    @Path("offres")
    public List<OffreVue> offres(@QueryParam("recherche") String recherche, @QueryParam("ville") String ville,
                                 @QueryParam("filiere") String filiere) {
        FiltreOffres filtre = new FiltreOffres();
        filtre.setOuvertesSeulement(true);
        filtre.setRecherche(recherche);
        filtre.setVille(ville);
        filtre.setFiliere(filiere);
        Composition c = new Composition(services);
        List<OffreVue> resultat = new ArrayList<>();
        for (Offre o : Services.soap("offres", () -> services.offres.listerOffres(filtre))) {
            CompatibiliteVue compat = CompatibiliteVue.de(Services.soap("offres", () -> services.offres.verifierCompatibilite(o.getId(), id())));
            resultat.add(OffreVue.de(o, c.entreprise(o.getEntrepriseId()), compat));
        }
        return resultat;
    }

    @GET
    @Path("offres/{id}")
    public OffreVue offre(@PathParam("id") long offreId) {
        Offre o = Services.soap("offres", () -> services.offres.consulterOffre(offreId));
        if ("BROUILLON".equals(o.getStatut())) {
            throw ErreurMetier.introuvable("OFFRE_INTROUVABLE", "Aucune offre ne correspond à l'identifiant " + offreId + ".");
        }
        CompatibiliteVue compat = CompatibiliteVue.de(Services.soap("offres", () -> services.offres.verifierCompatibilite(offreId, id())));
        return OffreVue.de(o, new Composition(services).entreprise(o.getEntrepriseId()), compat);
    }

    /** Dépôt : déclenche le processus (S4). 202 : la soumission dit si la candidature est enregistrée ou rejetée, et pourquoi. */
    @POST
    @Path("candidatures")
    public Response candidater(Candidater c) {
        if (c == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of("Offre et lettre de motivation attendues."));
        }
        Map<String, Object> corps = new HashMap<>();
        corps.put("etudiantId", id());
        corps.put("offreId", c.offreId());
        corps.put("lettreMotivation", c.lettreMotivation());
        JsonNode soumission = Services.rest(services.candidatures.appeler("POST", "/soumissions", corps, moi().acteur()));
        return Response.status(202).entity(new Composition(services).avecOffre(soumission)).build();
    }

    /** Candidatures enregistrées et dépôts rejetés ou interrompus, chacun avec le titre de l'offre. */
    @GET
    @Path("candidatures")
    public ObjectNode candidatures() {
        Composition c = new Composition(services);
        JsonNode candidatures = Services.rest(services.candidatures.get("/etudiants/" + id() + "/candidatures"));
        ArrayNode rejets = Json.MAPPEUR.createArrayNode();
        for (JsonNode s : Services.rest(services.candidatures.get("/etudiants/" + id() + "/soumissions"))) {
            if (!"ENREGISTREE".equals(s.path("statut").asText())) {
                ObjectNode sansEtapes = c.avecOffre(s);
                sansEtapes.remove("etapes");
                rejets.add(sansEtapes);
            }
        }
        ObjectNode resultat = Json.MAPPEUR.createObjectNode();
        resultat.set("candidatures", c.liste(candidatures, false, false));
        resultat.set("soumissionsNonEnregistrees", rejets);
        return resultat;
    }

    /** Détail d'une candidature de l'étudiant : historique et étapes du processus. 404 si elle appartient à un autre. */
    @GET
    @Path("candidatures/{id}")
    public ObjectNode candidature(@PathParam("id") long candidatureId) {
        ObjectNode c = new Composition(services).avecOffre(sienne(candidatureId));
        c.set("historique", Services.rest(services.candidatures.get("/candidatures/" + candidatureId + "/historique")));
        c.set("etapes", Services.rest(services.candidatures.get("/candidatures/" + candidatureId + "/etapes")));
        return c;
    }

    /** Annulation (R8 vérifiée par S4 : 409 si la candidature n'est plus déposée). */
    @DELETE
    @Path("candidatures/{id}")
    public Response annuler(@PathParam("id") long candidatureId) {
        sienne(candidatureId);
        Services.rest(services.candidatures.appeler("DELETE", "/candidatures/" + candidatureId, null, moi().acteur()));
        return Response.noContent().build();
    }

    private JsonNode sienne(long candidatureId) {
        JsonNode c;
        try {
            c = Services.rest(services.candidatures.get("/candidatures/" + candidatureId));
        } catch (ErreurMetier e) {
            throw e.type() == ErreurMetier.Type.INTROUVABLE || e.type() == ErreurMetier.Type.DONNEES_INVALIDES ? introuvable(candidatureId) : e;
        }
        if (c.path("etudiantId").asLong() != id()) {
            // Même réponse qu'une candidature inexistante : on ne révèle pas l'existence du dossier d'un autre.
            throw introuvable(candidatureId);
        }
        return c;
    }

    static ErreurMetier introuvable(long candidatureId) {
        return ErreurMetier.introuvable("CANDIDATURE_INTROUVABLE", "Aucune candidature ne correspond à l'identifiant " + candidatureId + ".");
    }

    @GET
    @Path("notifications")
    public JsonNode notifications() {
        String email = profil().email();
        return Services.rest(services.notifications.get("/notifications?limite=100&destinataire=" + URLEncoder.encode(email, StandardCharsets.UTF_8)));
    }

    /** Stages de l'étudiant, avec comptes rendus, évaluations et historique des opérations. */
    @GET
    @Path("stages")
    public ArrayNode stages() {
        Composition c = new Composition(services);
        ArrayNode resultat = Json.MAPPEUR.createArrayNode();
        for (JsonNode s : Services.rest(services.suivi.get("/etudiants/" + id() + "/stages"))) {
            ObjectNode stage = c.avecOffre(s);
            long stageId = s.path("id").asLong();
            stage.set("comptesRendus", Services.rest(services.suivi.get("/stages/" + stageId + "/comptes-rendus")));
            stage.set("evaluations", Services.rest(services.suivi.get("/stages/" + stageId + "/evaluations")));
            stage.set("historique", Services.rest(services.suivi.get("/stages/" + stageId + "/historique")));
            resultat.add(stage);
        }
        return resultat;
    }
}
