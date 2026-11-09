package ma.inpt.stages.offres;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;

/** Règles de gestion du service S3 : R2, R3, R4, R7 et le décompte des places de R10. */
public final class ServiceOffres {

    public record Disponibilite(boolean existe, boolean active, boolean dateLimiteRespectee, boolean placesDisponibles,
                                Integer nombrePlaces, boolean disponible, String regle, String motif, Long entrepriseId) {
    }

    public record Compatibilite(boolean compatible, boolean filiereAcceptee, boolean niveauSuffisant, boolean dateLimiteRespectee,
                                int scoreCompetences, String regle, String motif) {
    }

    /** Filtre reçu : ouvertesSeulement sera traduit avec la date du jour. */
    public record Criteres(String statut, String ville, String filiere, String niveau, Long entrepriseId,
                           String competence, String recherche, boolean ouvertesSeulement) {
    }

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "BROUILLON", Set.of("ACTIVE", "CLOTUREE"),
            "ACTIVE", Set.of("POURVUE", "CLOTUREE"),
            "POURVUE", Set.of("ACTIVE", "CLOTUREE"),
            "CLOTUREE", Set.of());

    private final OffreDao dao;
    private final ClientsExternes externes;
    private final Clock horloge;

    public ServiceOffres(OffreDao dao, ClientsExternes externes, Clock horloge) {
        this.dao = dao;
        this.externes = externes;
        this.horloge = horloge;
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    public Offre creer(Offre saisie) {
        String statut = saisie.statut() == null ? "BROUILLON" : saisie.statut();
        Offre offre = saisie.avec(null, statut);
        Validateur.verifier(offre);
        if (!Set.of("BROUILLON", "ACTIVE").contains(statut)) {
            throw invalide("Une nouvelle offre doit être en BROUILLON ou ACTIVE.");
        }
        if (offre.nombrePlaces() < 1) {
            throw invalide("Une nouvelle offre doit proposer au moins une place.");
        }
        if (offre.dateLimite().isBefore(aujourdhui())) {
            throw invalide("La date limite d'une nouvelle offre ne peut pas être passée.");
        }
        ClientsExternes.EtatEntreprise entreprise = externes.etatEntreprise(offre.entrepriseId());
        if (!entreprise.referencee() || !entreprise.active()) {
            throw invalide(entreprise.motif());
        }
        return dao.inserer(offre);
    }

    private static ErreurMetier invalide(String detail) {
        return ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", List.of(detail));
    }

    public Offre consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable(
                "OFFRE_INTROUVABLE", "Aucune offre ne correspond à l'identifiant " + id + "."));
    }

    public List<Offre> lister(Criteres c) {
        return dao.lister(new OffreDao.Filtre(c.statut(), c.ville(), c.filiere(), c.niveau(), c.entrepriseId(),
                c.competence(), c.recherche(), c.ouvertesSeulement() ? aujourdhui() : null));
    }

    public Offre modifier(long id, Offre saisie) {
        Offre existante = consulter(id);
        Offre offre = saisie.avec(id, saisie.statut() == null ? existante.statut() : saisie.statut());
        Validateur.verifier(offre);
        if (offre.entrepriseId() != existante.entrepriseId()) {
            throw invalide("L'entreprise d'une offre ne peut pas être changée.");
        }
        if ("CLOTUREE".equals(existante.statut())) {
            throw ErreurMetier.conflit("OFFRE_CLOTUREE", "Une offre clôturée ne peut plus être modifiée.");
        }
        if (!offre.statut().equals(existante.statut()) && !TRANSITIONS.get(existante.statut()).contains(offre.statut())) {
            throw ErreurMetier.conflit("TRANSITION_INTERDITE",
                    "Le passage du statut " + existante.statut() + " au statut " + offre.statut() + " n'est pas autorisé.");
        }
        dao.modifier(offre);
        return offre;
    }

    public void supprimer(long id) {
        if (!"BROUILLON".equals(consulter(id).statut())) {
            throw ErreurMetier.conflit("OFFRE_PUBLIEE", "Seule une offre en BROUILLON peut être supprimée : clôturez cette offre.");
        }
        dao.supprimer(id);
    }

    public Disponibilite verifierDisponibilite(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).map(this::evaluerDisponibilite).orElseGet(() ->
                new Disponibilite(false, false, false, false, null, false, "R2", "Aucune offre ne correspond à l'identifiant " + id + ".", null));
    }

    private Disponibilite evaluerDisponibilite(Offre o) {
        boolean active = "ACTIVE".equals(o.statut());
        boolean dateOk = !o.dateLimite().isBefore(aujourdhui());
        boolean places = o.nombrePlaces() > 0;
        String regle = null;
        String motif = null;
        if (!active) {
            regle = "R2";
            motif = "Cette offre n'est pas active (statut " + o.statut() + ").";
        } else if (!dateOk) {
            regle = "R3";
            motif = "La date limite de candidature est dépassée (" + o.dateLimite() + ").";
        } else if (!places) {
            regle = "R4";
            motif = "Cette offre n'a plus de place disponible.";
        }
        return new Disponibilite(true, active, dateOk, places, o.nombrePlaces(), regle == null, regle, motif, o.entrepriseId());
    }

    public Compatibilite verifierCompatibilite(long offreId, long etudiantId) {
        Offre offre = consulter(offreId);
        Validateur.verifierIdentifiant(etudiantId);
        ClientsExternes.ProfilEtudiant profil = externes.profilEtudiant(etudiantId).orElseThrow(() -> ErreurMetier.introuvable(
                "ETUDIANT_INTROUVABLE", "Aucun étudiant ne correspond à l'identifiant " + etudiantId + "."));
        boolean filiere = offre.filieres().contains(profil.filiere());
        // Les niveaux 1A, 2A et 3A se comparent dans l'ordre alphabétique.
        boolean niveau = profil.niveau().compareTo(offre.niveauMinimum()) >= 0;
        boolean dateOk = !offre.dateLimite().isBefore(aujourdhui());
        String regle = null;
        String motif = null;
        if (!filiere) {
            regle = "R7";
            motif = "La filière " + profil.filiere() + " n'est pas acceptée par cette offre.";
        } else if (!niveau) {
            regle = "R7";
            motif = "Cette offre exige le niveau " + offre.niveauMinimum() + " au minimum (niveau de l'étudiant : " + profil.niveau() + ").";
        } else if (!dateOk) {
            regle = "R3";
            motif = "La date limite de candidature est dépassée (" + offre.dateLimite() + ").";
        }
        return new Compatibilite(regle == null, filiere, niveau, dateOk, score(offre.competences(), profil.competences()), regle, motif);
    }

    /** Part des compétences requises que possède l'étudiant. Indicatif : ne bloque jamais une candidature (R7). */
    static int score(List<String> requises, List<String> possedees) {
        if (requises.isEmpty()) {
            return 100;
        }
        Set<String> etudiant = possedees.stream().map(c -> c.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        long communes = requises.stream().filter(c -> etudiant.contains(c.trim().toLowerCase(Locale.ROOT))).count();
        return Math.round(100f * communes / requises.size());
    }

    public Offre decrementerPlaces(long id) {
        Offre offre = consulter(id);
        if (!dao.decrementerPlaces(id)) {
            throw ErreurMetier.conflit("PLUS_DE_PLACE", "Impossible de retirer une place : l'offre n'est pas active ou n'a plus de place (statut " + offre.statut() + ").");
        }
        return consulter(id);
    }
}
