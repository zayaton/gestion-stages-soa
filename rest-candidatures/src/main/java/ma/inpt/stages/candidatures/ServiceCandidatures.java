package ma.inpt.stages.candidatures;

import java.util.List;
import java.util.Map;
import java.util.Set;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.ChangementStatut;
import ma.inpt.stages.candidatures.Modeles.CompteActives;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.Historique;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.NouvelleEtape;
import ma.inpt.stages.candidatures.Modeles.Rejet;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.candidatures.Modeles.VerificationDoublon;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;

/** Règles locales du service S4 : R5, R6 (enregistrement), R8 (annulation) et R9 (transitions). */
public final class ServiceCandidatures {

    /** Règle R9 : seules ces transitions sont permises. Toute autre donne 409. */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DEPOSEE", Set.of("EN_COURS_EVALUATION", "ANNULEE"),
            "EN_COURS_EVALUATION", Set.of("ACCEPTEE", "REFUSEE"),
            "ACCEPTEE", Set.of(),
            "REFUSEE", Set.of(),
            "ANNULEE", Set.of());

    private final CandidatureDao dao;
    private final int limiteActives;

    public ServiceCandidatures(CandidatureDao dao, int limiteActives) {
        this.dao = dao;
        this.limiteActives = limiteActives;
    }

    public int limiteActives() {
        return limiteActives;
    }

    static void exiger(Object corps, String quoi) {
        if (corps == null) {
            throw ErreurMetier.requeteInvalide("Le corps de la requête est obligatoire.", List.of(quoi + " attendu en JSON."));
        }
    }

    private static ErreurMetier invalide(String detail) {
        return ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", List.of(detail));
    }

    // Enregistrement (appelé par le processus après la passerelle de décision)

    /** Enregistre la candidature. R5 et R6 sont revérifiées ici, de façon atomique : 409 si l'une est violée. */
    public Candidature enregistrer(NouvelleCandidature saisie, String acteur) {
        exiger(saisie, "Une candidature");
        Validateur.verifier(saisie);
        if (saisie.soumissionId() != null) {
            Soumission s = soumission(saisie.soumissionId());
            if (s.etudiantId() != saisie.etudiantId() || s.offreId() != saisie.offreId()) {
                throw invalide("La soumission " + s.id() + " concerne un autre étudiant ou une autre offre.");
            }
            if (s.candidatureId() != null) {
                throw ErreurMetier.conflit("SOUMISSION_DEJA_ENREGISTREE", "La soumission " + s.id() + " a déjà donné la candidature " + s.candidatureId() + ".");
            }
        }
        CandidatureDao.Enregistrement e = dao.enregistrer(saisie, limiteActives, acteur);
        if ("R5".equals(e.regleViolee())) {
            throw ErreurMetier.conflit("CANDIDATURE_EN_DOUBLE", "Règle R5 : une candidature non annulée existe déjà pour cette offre"
                    + (e.candidatureExistante() == null ? "." : " (candidature " + e.candidatureExistante() + ")."));
        }
        if ("R6".equals(e.regleViolee())) {
            throw ErreurMetier.conflit("LIMITE_CANDIDATURES_ATTEINTE", "Règle R6 : l'étudiant a déjà " + limiteActives + " candidatures actives.");
        }
        if (saisie.soumissionId() != null) {
            dao.terminerSoumission(saisie.soumissionId(), "ENREGISTREE", null, null);
        }
        return e.candidature();
    }

    // Consultation

    public Candidature consulter(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouver(id).orElseThrow(() -> ErreurMetier.introuvable("CANDIDATURE_INTROUVABLE",
                "Aucune candidature ne correspond à l'identifiant " + id + "."));
    }

    public List<Candidature> lister(String statut, Long etudiantId, Long offreId, Long entrepriseId) {
        if (statut != null && !statut.isBlank() && !Modeles.STATUTS.contains(statut)) {
            throw ErreurMetier.requeteInvalide("Paramètre de recherche invalide.",
                    List.of("Le statut doit être " + String.join(", ", Modeles.STATUTS) + "."));
        }
        return dao.lister(new CandidatureDao.Filtre(statut == null || statut.isBlank() ? null : statut, etudiantId, offreId, entrepriseId));
    }

    public List<Historique> historique(long id) {
        consulter(id);
        return dao.historique(id);
    }

    public List<Etape> etapesCandidature(long id) {
        Candidature c = consulter(id);
        return c.soumissionId() == null ? List.of() : dao.etapes(c.soumissionId());
    }

    /** Règle R5 : y a-t-il une candidature non annulée pour ce couple étudiant et offre ? */
    public VerificationDoublon verifierDoublon(Long etudiantId, Long offreId) {
        if (etudiantId == null || offreId == null || etudiantId <= 0 || offreId <= 0) {
            throw ErreurMetier.requeteInvalide("Paramètres manquants ou invalides.",
                    List.of("Les paramètres etudiantId et offreId sont obligatoires et positifs."));
        }
        Long existante = dao.candidatureNonAnnulee(etudiantId, offreId).map(Candidature::id).orElse(null);
        return new VerificationDoublon(etudiantId, offreId, existante != null, existante);
    }

    /** Règle R6 : nombre de candidatures actives (DEPOSEE et EN_COURS_EVALUATION). */
    public CompteActives compterActives(long etudiantId) {
        Validateur.verifierIdentifiant(etudiantId);
        int n = dao.compterActives(etudiantId);
        return new CompteActives(etudiantId, n, limiteActives, n >= limiteActives);
    }

    // Statuts

    /** Règle R9. Reprendre le statut actuel est sans effet, pour qu'une étape du processus puisse être rejouée. */
    public Candidature changerStatut(long id, ChangementStatut demande, String acteur) {
        exiger(demande, "Un statut");
        Validateur.verifier(demande);
        if (!Modeles.STATUTS.contains(demande.statut())) {
            throw invalide("Le statut doit être " + String.join(", ", Modeles.STATUTS) + ".");
        }
        Candidature c = consulter(id);
        if (c.statut().equals(demande.statut())) {
            return c;
        }
        transition(c, demande.statut(), acteur, demande.commentaire());
        return consulter(id);
    }

    /** Applique une transition permise par R9, ou lève 409 en nommant la règle. */
    void transition(Candidature c, String nouveau, String acteur, String commentaire) {
        if (!TRANSITIONS.get(c.statut()).contains(nouveau)) {
            throw ErreurMetier.conflit("TRANSITION_INTERDITE", "Règle R9 : le passage du statut " + c.statut() + " au statut " + nouveau
                    + " n'est pas autorisé.");
        }
        if (!dao.changerStatut(c.id(), c.statut(), nouveau, acteur, commentaire)) {
            throw ErreurMetier.conflit("CANDIDATURE_MODIFIEE", "La candidature " + c.id() + " vient d'être modifiée par un autre traitement : rechargez-la.");
        }
    }

    /** Règle R8 : l'annulation n'est permise que tant que la candidature est DEPOSEE. */
    public Candidature annuler(long id, String acteur) {
        Candidature c = consulter(id);
        if (!"DEPOSEE".equals(c.statut())) {
            throw ErreurMetier.conflit("ANNULATION_IMPOSSIBLE", "Règle R8 : une candidature ne peut être annulée que tant qu'elle est déposée "
                    + "(statut actuel : " + c.statut() + ").");
        }
        transition(c, "ANNULEE", acteur, "Candidature annulée");
        tracer(c, Etapes.ANNULATION, "INFO", "Candidature annulée", acteur);
        return consulter(id);
    }

    /** L'entreprise ouvre le dossier : DEPOSEE devient EN_COURS_EVALUATION. Dès lors, l'annulation n'est plus possible (R8). */
    public Candidature demarrerEvaluation(long id, String acteur) {
        Candidature c = consulter(id);
        if (!"DEPOSEE".equals(c.statut())) {
            throw ErreurMetier.conflit("EVALUATION_IMPOSSIBLE", "L'évaluation ne peut commencer que pour une candidature déposée "
                    + "(statut actuel : " + c.statut() + ").");
        }
        transition(c, "EN_COURS_EVALUATION", acteur, "Évaluation démarrée");
        tracer(c, Etapes.DEBUT_EVALUATION, "OK", "Évaluation démarrée par l'entreprise", acteur);
        return consulter(id);
    }

    void tracer(Candidature c, String etape, String resultat, String detail, String acteur) {
        if (c.soumissionId() != null) {
            dao.ajouterEtape(c.soumissionId(), etape, resultat, detail, acteur);
        }
    }

    // Soumissions

    public Soumission soumission(long id) {
        Validateur.verifierIdentifiant(id);
        return dao.trouverSoumission(id).orElseThrow(() -> ErreurMetier.introuvable("SOUMISSION_INTROUVABLE",
                "Aucune soumission ne correspond à l'identifiant " + id + "."));
    }

    public List<Soumission> soumissionsEtudiant(long etudiantId) {
        Validateur.verifierIdentifiant(etudiantId);
        return dao.soumissionsEtudiant(etudiantId);
    }

    /** Étape signalée par le processus Bonita (vue BPMN en direct). */
    public Soumission enregistrerEtape(long soumissionId, NouvelleEtape etape, String acteur) {
        exiger(etape, "Une étape");
        Validateur.verifier(etape);
        if (!Etapes.TOUTES.contains(etape.etape())) {
            throw invalide("Étape inconnue. Étapes possibles : " + String.join(", ", Etapes.TOUTES) + ".");
        }
        if (!Etapes.RESULTATS.contains(etape.resultat())) {
            throw invalide("Le résultat doit être OK, REJET, ECHEC ou INFO.");
        }
        soumission(soumissionId);
        dao.ajouterEtape(soumissionId, etape.etape(), etape.resultat(), etape.detail(), acteur);
        return soumission(soumissionId);
    }

    /** Rejet constaté par le processus Bonita à la passerelle de décision (règles R1 à R7). */
    public Soumission rejeter(long soumissionId, Rejet rejet, String acteur) {
        exiger(rejet, "Un rejet");
        Validateur.verifier(rejet);
        if (!rejet.regle().matches("R[1-7]")) {
            throw invalide("La règle doit être l'une de R1 à R7.");
        }
        Soumission s = soumission(soumissionId);
        if (!"EN_TRAITEMENT".equals(s.statut())) {
            throw ErreurMetier.conflit("SOUMISSION_TERMINEE", "La soumission " + soumissionId + " est déjà terminée (statut " + s.statut() + ").");
        }
        dao.terminerSoumission(soumissionId, "REJETEE", rejet.regle(), rejet.motif());
        dao.ajouterEtape(soumissionId, Etapes.PASSERELLE_DECISION, "REJET", rejet.regle() + " : " + rejet.motif(), acteur);
        return soumission(soumissionId);
    }
}
