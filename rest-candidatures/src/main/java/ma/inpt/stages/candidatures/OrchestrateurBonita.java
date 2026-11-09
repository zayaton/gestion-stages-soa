package ma.inpt.stages.candidatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.Validateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mode « bonita » : le processus BPMN déployé dans Bonita orchestre. S4 crée la soumission, démarre un cas Bonita
 * avec le contrat {soumissionId, etudiantId, offreId, lettreMotivation}, puis transmet la décision de l'entreprise
 * en exécutant la tâche humaine. Le processus appelle lui-même S1 à S6 et alimente le journal des étapes.
 */
public final class OrchestrateurBonita implements Orchestrateur {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestrateurBonita.class);

    private final ServiceCandidatures service;
    private final CandidatureDao dao;
    private final Partenaires partenaires;
    private final OrchestrateurDirect direct;
    private final ClientBonita bonita;

    public OrchestrateurBonita(ServiceCandidatures service, CandidatureDao dao, Partenaires partenaires, OrchestrateurDirect direct, ClientBonita bonita) {
        this.service = service;
        this.dao = dao;
        this.partenaires = partenaires;
        this.direct = direct;
        this.bonita = bonita;
    }

    @Override
    public String mode() {
        return "bonita";
    }

    /** Étape 1 : réception, puis démarrage du cas. Réponse 202 : le statut avance au fil du processus (GET /soumissions/{id}). */
    @Override
    public Soumission deposer(NouvelleSoumission demande, String acteur) {
        ServiceCandidatures.exiger(demande, "Une candidature");
        Validateur.verifier(demande);
        Soumission s = dao.creerSoumission(demande.etudiantId(), demande.offreId(), demande.lettreMotivation().trim());
        dao.ajouterEtape(s.id(), Etapes.RECEPTION, "OK", "Candidature reçue", acteur);
        Map<String, Object> contrat = new HashMap<>();
        contrat.put("soumissionId", s.id());
        contrat.put("etudiantId", demande.etudiantId());
        contrat.put("offreId", demande.offreId());
        contrat.put("lettreMotivation", demande.lettreMotivation().trim());
        try {
            long cas = bonita.demarrer(contrat);
            dao.definirCasBonita(s.id(), cas);
            LOG.info("Soumission {} : cas Bonita {} démarré", s.id(), cas);
        } catch (ErreurMetier e) {
            dao.terminerSoumission(s.id(), "ERREUR", null, e.getMessage());
            dao.ajouterEtape(s.id(), Etapes.ERREUR, "ECHEC", "Démarrage du processus : " + e.getMessage(), "processus");
            direct.alerterResponsable("Démarrage du processus Bonita", e.getMessage(), "Soumission " + s.id());
            throw ErreurMetier.indisponible(e.code(), e.getMessage() + " La soumission " + s.id()
                    + " n'a pas pu être traitée ; le responsable des stages a été prévenu.");
        }
        return service.soumission(s.id());
    }

    /**
     * Étape 7 : la décision termine la tâche humaine du cas ; le processus exécute ensuite les étapes 8 et 9.
     * Une candidature sans cas Bonita (données de démonstration antérieures au processus) est traitée en mode direct.
     */
    @Override
    public ResultatDecision decider(long candidatureId, Decision decision, String acteur) {
        OrchestrateurDirect.valider(decision);
        Candidature c = OrchestrateurDirect.enEvaluation(service, candidatureId);
        Long cas = c.soumissionId() == null ? null : service.soumission(c.soumissionId()).bonitaCaseId();
        if (cas == null) {
            LOG.info("Candidature {} sans cas Bonita (antérieure au processus) : décision exécutée en mode direct", candidatureId);
            return direct.decider(candidatureId, decision, acteur);
        }
        if ("ACCEPTER".equals(decision.decision()) && !partenaires.offre(c.offreId()).placesDisponibles()) {
            // R10 : sans place, l'acceptation est refusée tout de suite, comme en mode direct, plutôt qu'en erreur dans le processus.
            throw ErreurMetier.conflit("PLUS_DE_PLACE", "Règle R10 : l'offre n'a plus de place disponible ; la candidature ne peut pas être acceptée.");
        }
        Long tache = bonita.tacheDecision(cas);
        if (tache == null) {
            throw ErreurMetier.conflit("DECISION_EN_COURS", "Le processus n'attend pas de décision pour le moment (cas " + cas
                    + ") : la décision précédente est peut-être encore en cours de traitement. Réessayez dans un instant.");
        }
        String commentaire = decision.commentaire() == null ? "" : decision.commentaire().trim();
        bonita.executer(tache, Map.of("decision", decision.decision(), "commentaire", commentaire));
        if ("DEMANDER_COMPLEMENT".equals(decision.decision())) {
            dao.ajouterHistorique(c.id(), c.statut(), c.statut(), acteur, "Demande de complément : " + commentaire);
        }
        String detail = "Décision " + decision.decision() + " transmise au processus Bonita (cas " + cas + ")"
                + (commentaire.isEmpty() ? "" : " : " + commentaire);
        service.tracer(c, Etapes.DECISION_ENTREPRISE, "OK", detail, acteur);
        return new ResultatDecision(service.consulter(c.id()),
                List.of(new Etape(Etapes.DECISION_ENTREPRISE, "OK", detail, java.time.LocalDateTime.now(), acteur)));
    }

    @Override
    public ResultatDecision reprendreSuivi(long candidatureId, String acteur) {
        return direct.reprendreSuivi(candidatureId, acteur);
    }

    /** R8 dans S4, puis suppression du cas Bonita : la tâche de décision disparaît de la liste de l'entreprise. */
    @Override
    public Candidature annuler(long candidatureId, String acteur) {
        Candidature c = service.annuler(candidatureId, acteur);
        Long cas = c.soumissionId() == null ? null : service.soumission(c.soumissionId()).bonitaCaseId();
        if (cas != null) {
            try {
                bonita.supprimerCas(cas);
                service.tracer(c, Etapes.ANNULATION, "INFO", "Cas Bonita " + cas + " supprimé", "processus");
            } catch (ErreurMetier e) {
                // L'annulation reste acquise dans S4 ; le cas orphelin se supprime depuis le portail Bonita.
                LOG.warn("Candidature {} annulée, mais cas Bonita {} non supprimé : {}", candidatureId, cas, e.getMessage());
                service.tracer(c, Etapes.ANNULATION, "ECHEC", "Cas Bonita " + cas + " non supprimé : " + e.getMessage(), "processus");
            }
        }
        return c;
    }
}
