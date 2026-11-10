package ma.inpt.stages.portail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.contrats.entreprises.FiltreEntreprises;
import ma.inpt.stages.contrats.etudiants.FiltreEtudiants;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.portail.Vues.ChangementStatut;
import ma.inpt.stages.portail.Vues.EntrepriseVue;
import ma.inpt.stages.portail.Vues.EtudiantVue;
import ma.inpt.stages.portail.securite.FiltreSession;
import ma.inpt.stages.portail.securite.RolesAutorises;
import ma.inpt.stages.portail.securite.Utilisateur;

/** Espace responsable des stages : tableau de bord, candidatures, stages, étudiants, entreprises, rappels. */
@Path("/api/responsable")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAutorises(Utilisateur.RESPONSABLE)
public final class RessourceResponsable {

    /** Adresses de santé des services, pour l'écran d'état du système. */
    public record AdressesSante(Map<String, String> parService) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();

    private final Services services;
    private final AdressesSante sante;

    @Context
    private ContainerRequestContext requete;

    @Inject
    public RessourceResponsable(Services services, AdressesSante sante) {
        this.services = services;
        this.sante = sante;
    }

    private String acteur() {
        return ((Utilisateur) requete.getProperty(FiltreSession.PROPRIETE_UTILISATEUR)).acteur();
    }

    private static void exiger(Object corps) {
        if (corps == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of("Données attendues en JSON."));
        }
    }

    private static String encode(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }

    // Tableau de bord

    /** Indicateurs : offres par statut (S3), candidatures par statut et taux d'acceptation (S4), stages par statut (S6). */
    @GET
    @Path("tableau-de-bord")
    public ObjectNode tableauDeBord() {
        // Chaque source est facultative : si un service est indisponible, ses indicateurs valent null et il est
        // nommé dans « sourcesIndisponibles », mais le reste du tableau de bord s'affiche (le responsable doit voir la panne).
        ObjectNode n = Json.MAPPEUR.createObjectNode();
        ArrayNode indisponibles = n.putArray("sourcesIndisponibles");

        Map<String, Integer> offres = new TreeMap<>();
        if (lire(indisponibles, "S3 Offres", () -> Services.soap("offres", () -> services.offres.listerOffres(new FiltreOffres()))
                .forEach(o -> offres.merge(o.getStatut(), 1, Integer::sum)))) {
            n.set("offresParStatut", Json.MAPPEUR.valueToTree(offres));
            n.put("offresTotal", offres.values().stream().mapToInt(Integer::intValue).sum());
        } else {
            n.putNull("offresParStatut");
            n.putNull("offresTotal");
        }

        Map<String, Integer> candidatures = new LinkedHashMap<>();
        List.of("DEPOSEE", "EN_COURS_EVALUATION", "ACCEPTEE", "REFUSEE", "ANNULEE").forEach(s -> candidatures.put(s, 0));
        if (lire(indisponibles, "S4 Candidatures", () -> Services.rest(services.candidatures.get("/candidatures"))
                .forEach(c -> candidatures.merge(c.path("statut").asText(), 1, Integer::sum)))) {
            int decidees = candidatures.get("ACCEPTEE") + candidatures.get("REFUSEE");
            n.set("candidaturesParStatut", Json.MAPPEUR.valueToTree(candidatures));
            n.put("candidaturesTotal", candidatures.values().stream().mapToInt(Integer::intValue).sum());
            // Taux d'acceptation : part des candidatures acceptées parmi celles qui ont reçu une décision.
            if (decidees > 0) {
                n.put("tauxAcceptation", Math.round(1000.0 * candidatures.get("ACCEPTEE") / decidees) / 10.0);
            } else {
                n.putNull("tauxAcceptation");
            }
        } else {
            n.putNull("candidaturesParStatut");
            n.putNull("candidaturesTotal");
            n.putNull("tauxAcceptation");
        }

        Map<String, Integer> stages = new LinkedHashMap<>();
        List.of("PLANIFIE", "EN_COURS", "TERMINE", "INTERROMPU").forEach(s -> stages.put(s, 0));
        if (lire(indisponibles, "S6 Suivi des stages", () -> Services.rest(services.suivi.get("/stages"))
                .forEach(s -> stages.merge(s.path("statut").asText(), 1, Integer::sum)))) {
            n.set("stagesParStatut", Json.MAPPEUR.valueToTree(stages));
            n.put("stagesEnCours", stages.get("EN_COURS"));
        } else {
            n.putNull("stagesParStatut");
            n.putNull("stagesEnCours");
        }
        return n;
    }

    /** Exécute une lecture ; renvoie false (et note le service) si celui-ci est indisponible. Les autres erreurs remontent. */
    private static boolean lire(ArrayNode indisponibles, String service, Runnable lecture) {
        try {
            lecture.run();
            return true;
        } catch (ErreurMetier e) {
            if (e.type() != ErreurMetier.Type.INDISPONIBLE) {
                throw e;
            }
            indisponibles.add(service);
            return false;
        }
    }

    /** État de chaque service (GET /health), pour repérer une panne depuis l'interface. */
    @GET
    @Path("services")
    public ArrayNode etatServices() {
        ArrayNode resultat = Json.MAPPEUR.createArrayNode();
        sante.parService().forEach((nom, url) -> {
            ObjectNode n = resultat.addObject();
            n.put("service", nom);
            n.put("adresse", url);
            try {
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).build(),
                        HttpResponse.BodyHandlers.ofString());
                n.put("etat", r.statusCode() == 200 ? "DISPONIBLE" : "DEGRADE");
                n.put("codeHttp", r.statusCode());
            } catch (Exception e) {
                n.put("etat", "INDISPONIBLE");
            }
        });
        return resultat;
    }

    // Candidatures

    @GET
    @Path("candidatures")
    public ArrayNode candidatures(@QueryParam("statut") String statut) {
        String filtre = statut == null || statut.isBlank() ? "" : "?statut=" + encode(statut);
        return new Composition(services).liste(Services.rest(services.candidatures.get("/candidatures" + filtre)), true, false);
    }

    @GET
    @Path("candidatures/{id}")
    public ObjectNode candidature(@PathParam("id") long id) {
        Composition c = new Composition(services);
        ObjectNode n = c.avecEtudiant(c.avecOffre(Services.rest(services.candidatures.get("/candidatures/" + id))), false);
        n.set("historique", Services.rest(services.candidatures.get("/candidatures/" + id + "/historique")));
        n.set("etapes", Services.rest(services.candidatures.get("/candidatures/" + id + "/etapes")));
        try {
            n.set("stage", Services.rest(services.suivi.get("/candidatures/" + id + "/stage")));
        } catch (ErreurMetier e) {
            if (e.type() != ErreurMetier.Type.INTROUVABLE) {
                throw e;
            }
        }
        return n;
    }

    /** Rejoue les étapes de suivi (stage, statut de l'étudiant) qui avaient échoué après une acceptation. */
    @POST
    @Path("candidatures/{id}/suivi/reprendre")
    public JsonNode reprendreSuivi(@PathParam("id") long id) {
        return Services.rest(services.candidatures.appeler("POST", "/candidatures/" + id + "/suivi/reprendre", null, acteur()));
    }

    // Stages

    @GET
    @Path("stages")
    public ArrayNode stages(@QueryParam("statut") String statut) {
        String filtre = statut == null || statut.isBlank() ? "" : "?statut=" + encode(statut);
        return new Composition(services).liste(Services.rest(services.suivi.get("/stages" + filtre)), true, false);
    }

    @GET
    @Path("stages/{id}")
    public ObjectNode stage(@PathParam("id") long id) {
        Composition c = new Composition(services);
        ObjectNode n = c.avecEtudiant(c.avecOffre(Services.rest(services.suivi.get("/stages/" + id))), false);
        n.set("comptesRendus", Services.rest(services.suivi.get("/stages/" + id + "/comptes-rendus")));
        n.set("evaluations", Services.rest(services.suivi.get("/stages/" + id + "/evaluations")));
        n.set("historique", Services.rest(services.suivi.get("/stages/" + id + "/historique")));
        return n;
    }

    @PATCH
    @Path("stages/{id}/statut")
    public JsonNode changerStatutStage(@PathParam("id") long id, ChangementStatut demande) {
        exiger(demande);
        return Services.rest(services.suivi.appeler("PATCH", "/stages/" + id + "/statut", demande, acteur()));
    }

    @POST
    @Path("stages/{id}/comptes-rendus")
    public Response compteRendu(@PathParam("id") long id, JsonNode saisie) {
        exiger(saisie);
        return Response.status(201).entity(Services.rest(services.suivi.appeler("POST", "/stages/" + id + "/comptes-rendus", saisie, acteur()))).build();
    }

    @POST
    @Path("stages/{id}/evaluations")
    public Response evaluation(@PathParam("id") long id, JsonNode saisie) {
        exiger(saisie);
        return Response.status(201).entity(Services.rest(services.suivi.appeler("POST", "/stages/" + id + "/evaluations", saisie, acteur()))).build();
    }

    // Étudiants (S1)

    @GET
    @Path("etudiants")
    public List<EtudiantVue> etudiants(@QueryParam("filiere") String filiere, @QueryParam("niveau") String niveau,
                                       @QueryParam("statut") String statut, @QueryParam("recherche") String recherche) {
        FiltreEtudiants f = new FiltreEtudiants();
        f.setFiliere(filiere);
        f.setNiveau(niveau);
        f.setStatut(statut);
        f.setRecherche(recherche);
        return Services.soap("etudiants", () -> services.etudiants.listerEtudiants(f)).stream().map(EtudiantVue::de).toList();
    }

    @GET
    @Path("etudiants/{id}")
    public ObjectNode etudiant(@PathParam("id") long id) {
        ObjectNode n = Json.MAPPEUR.valueToTree(EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.consulterEtudiant(id))));
        Composition c = new Composition(services);
        n.set("candidatures", c.liste(Services.rest(services.candidatures.get("/etudiants/" + id + "/candidatures")), false, false));
        n.set("stages", c.liste(Services.rest(services.suivi.get("/etudiants/" + id + "/stages")), false, false));
        return n;
    }

    @POST
    @Path("etudiants")
    public Response creerEtudiant(EtudiantVue saisie) {
        exiger(saisie);
        return Response.status(201).entity(EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.ajouterEtudiant(saisie.versSoap())))).build();
    }

    @PUT
    @Path("etudiants/{id}")
    public EtudiantVue modifierEtudiant(@PathParam("id") long id, EtudiantVue saisie) {
        exiger(saisie);
        return EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.modifierEtudiant(id, saisie.versSoap())));
    }

    @PATCH
    @Path("etudiants/{id}/statut")
    public EtudiantVue changerStatutEtudiant(@PathParam("id") long id, ChangementStatut demande) {
        exiger(demande);
        return EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.changerStatutEtudiant(id, demande.statut())));
    }

    /** S1 ne connaît pas les candidatures : le portail interroge S4 avant de permettre la suppression. */
    @DELETE
    @Path("etudiants/{id}")
    public Response supprimerEtudiant(@PathParam("id") long id) {
        for (JsonNode c : Services.rest(services.candidatures.get("/etudiants/" + id + "/candidatures"))) {
            if (!"ANNULEE".equals(c.path("statut").asText())) {
                throw ErreurMetier.conflit("ETUDIANT_AVEC_CANDIDATURES",
                        "Cet étudiant a des candidatures : il ne peut pas être supprimé. Changez plutôt son statut.");
            }
        }
        Services.soap("etudiants", () -> {
            services.etudiants.supprimerEtudiant(id);
            return null;
        });
        return Response.noContent().build();
    }

    // Entreprises (S2)

    @GET
    @Path("entreprises")
    public List<EntrepriseVue> entreprises(@QueryParam("secteur") String secteur, @QueryParam("ville") String ville,
                                           @QueryParam("statut") String statut, @QueryParam("recherche") String recherche) {
        FiltreEntreprises f = new FiltreEntreprises();
        f.setSecteur(secteur);
        f.setVille(ville);
        f.setStatut(statut);
        f.setRecherche(recherche);
        return Services.soap("entreprises", () -> services.entreprises.listerEntreprises(f)).stream().map(EntrepriseVue::de).toList();
    }

    @GET
    @Path("entreprises/{id}")
    public ObjectNode entreprise(@PathParam("id") long id) {
        ObjectNode n = Json.MAPPEUR.valueToTree(EntrepriseVue.de(Services.soap("entreprises", () -> services.entreprises.consulterEntreprise(id))));
        ArrayNode offres = n.putArray("offres");
        Services.soap("entreprises", () -> services.entreprises.consulterOffresEntreprise(id)).forEach(o -> {
            ObjectNode x = offres.addObject();
            x.put("id", o.getId());
            x.put("titre", o.getTitre());
            x.put("statut", o.getStatut());
            x.put("dateLimite", o.getDateLimite().toXMLFormat());
            x.put("nombrePlaces", o.getNombrePlaces());
        });
        return n;
    }

    @POST
    @Path("entreprises")
    public Response creerEntreprise(EntrepriseVue saisie) {
        exiger(saisie);
        return Response.status(201).entity(EntrepriseVue.de(Services.soap("entreprises", () -> services.entreprises.ajouterEntreprise(saisie.versSoap())))).build();
    }

    @PUT
    @Path("entreprises/{id}")
    public EntrepriseVue modifierEntreprise(@PathParam("id") long id, EntrepriseVue saisie) {
        exiger(saisie);
        return EntrepriseVue.de(Services.soap("entreprises", () -> services.entreprises.modifierEntreprise(id, saisie.versSoap())));
    }

    @DELETE
    @Path("entreprises/{id}")
    public Response supprimerEntreprise(@PathParam("id") long id) {
        Services.soap("entreprises", () -> {
            services.entreprises.supprimerEntreprise(id);
            return null;
        });
        return Response.noContent().build();
    }

    // Notifications et rappels (S5)

    @POST
    @Path("rappels")
    public JsonNode executerRappels(@QueryParam("jours") Integer jours) {
        return Services.rest(services.notifications.appeler("POST", "/rappels/executer" + (jours == null ? "" : "?jours=" + jours), null, acteur()));
    }

    @GET
    @Path("notifications")
    public JsonNode notifications(@QueryParam("destinataire") String destinataire, @QueryParam("type") String type) {
        StringBuilder q = new StringBuilder("/notifications?limite=200");
        if (destinataire != null && !destinataire.isBlank()) {
            q.append("&destinataire=").append(encode(destinataire.trim()));
        }
        if (type != null && !type.isBlank()) {
            q.append("&type=").append(encode(type.trim()));
        }
        return Services.rest(services.notifications.get(q.toString()));
    }
}
