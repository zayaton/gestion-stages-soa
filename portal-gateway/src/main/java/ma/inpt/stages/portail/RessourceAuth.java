package ma.inpt.stages.portail;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.portail.Vues.Connexion;
import ma.inpt.stages.portail.securite.FiltreSession;
import ma.inpt.stages.portail.securite.RolesAutorises;
import ma.inpt.stages.portail.securite.ServiceAuthentification;
import ma.inpt.stages.portail.securite.Utilisateur;

/** Connexion, déconnexion et identité de la personne connectée. */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class RessourceAuth {

    /** Ce que l'interface sait de l'utilisateur : rôle, référence et nom à afficher. */
    public record Moi(String identifiant, String role, Long referenceId, String nom) {
    }

    public record Reponse(String jeton, LocalDateTime expireLe, Moi utilisateur) {
    }

    private final ServiceAuthentification auth;
    private final Services services;

    @Context
    private ContainerRequestContext requete;

    @Inject
    public RessourceAuth(ServiceAuthentification auth, Services services) {
        this.auth = auth;
        this.services = services;
    }

    @POST
    @Path("connexion")
    public Reponse connecter(Connexion c) {
        if (c == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", java.util.List.of("Identifiant et mot de passe attendus."));
        }
        ServiceAuthentification.Session s = auth.connecter(c.identifiant(), c.motDePasse());
        return new Reponse(s.jeton(), s.expireLe(), moi(s.utilisateur()));
    }

    @POST
    @Path("deconnexion")
    public Response deconnecter() {
        auth.deconnecter(FiltreSession.jeton(requete));
        return Response.noContent().build();
    }

    @GET
    @Path("moi")
    @RolesAutorises({Utilisateur.ETUDIANT, Utilisateur.ENTREPRISE, Utilisateur.RESPONSABLE})
    public Moi moi() {
        return moi((Utilisateur) requete.getProperty(FiltreSession.PROPRIETE_UTILISATEUR));
    }

    /** Le nom vient du service qui possède la donnée ; s'il est arrêté, la connexion reste possible avec l'identifiant. */
    private Moi moi(Utilisateur u) {
        String nom = u.identifiant();
        try {
            if (Utilisateur.ETUDIANT.equals(u.role())) {
                var e = Services.soap("etudiants", () -> services.etudiants.consulterEtudiant(u.referenceId()));
                nom = e.getPrenom() + " " + e.getNom();
            } else if (Utilisateur.ENTREPRISE.equals(u.role())) {
                nom = Services.soap("entreprises", () -> services.entreprises.consulterEntreprise(u.referenceId())).getRaisonSociale();
            } else {
                nom = "Responsable des stages";
            }
        } catch (ErreurMetier e) {
            // Nom indisponible : l'identifiant suffit.
        }
        return new Moi(u.identifiant(), u.role(), u.referenceId(), nom);
    }
}
