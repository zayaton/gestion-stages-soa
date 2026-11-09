package ma.inpt.stages.candidatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.candidatures.Partenaires.Compatibilite;
import ma.inpt.stages.candidatures.Partenaires.EtatEntreprise;
import ma.inpt.stages.candidatures.Partenaires.EtatEtudiant;
import ma.inpt.stages.candidatures.Partenaires.EtatOffre;
import ma.inpt.stages.common.ErreurMetier;
import org.junit.jupiter.api.Test;

/** Le processus en mode direct : parcours nominal, un rejet par règle (R1 à R7), décisions (R10, R11), pannes. */
class OrchestrateurDirectTest {

    private static final String LETTRE = "Je souhaite rejoindre votre équipe pour ce stage passionnant.";

    private final Doublures.Dao dao = new Doublures.Dao();
    private final Doublures.Monde monde = new Doublures.Monde();
    private final ServiceCandidatures service = new ServiceCandidatures(dao, 5);
    private final OrchestrateurDirect orchestrateur = new OrchestrateurDirect(service, dao, monde, "responsable@inpt.ac.ma", 30,
            Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneId.of("Africa/Casablanca")));

    private Soumission deposer(long etudiant, long offre) {
        return orchestrateur.deposer(new NouvelleSoumission(etudiant, offre, LETTRE), "etudiant:" + etudiant);
    }

    private static List<String> noms(List<Etape> etapes) {
        return etapes.stream().map(e -> e.etape() + ":" + e.resultat()).toList();
    }

    private void rejeteePar(Soumission s, String regle) {
        assertEquals("REJETEE", s.statut(), s.toString());
        assertEquals(regle, s.regleRejetee());
        assertNull(s.candidatureId());
        assertTrue(dao.candidatures.isEmpty() || dao.candidatures.values().stream().noneMatch(c -> Long.valueOf(s.id()).equals(c.soumissionId())));
        assertTrue(noms(s.etapes()).contains("PASSERELLE_DECISION:REJET"), noms(s.etapes()).toString());
    }

    // Parcours nominal (étapes 1 à 6)

    @Test
    void parcoursNominalEnregistreEtNotifieEtudiantEtEntreprise() {
        Soumission s = deposer(1, 1);
        assertEquals("ENREGISTREE", s.statut());
        assertNotNull(s.candidatureId());
        assertEquals(List.of("RECEPTION:OK", "VERIFICATION_ETUDIANT:OK", "VERIFICATION_OFFRE:OK", "CONTROLE_CANDIDATURE:OK",
                "PASSERELLE_DECISION:OK", "ENREGISTREMENT:OK", "NOTIFICATION_DEPOT:OK", "TRANSMISSION_ENTREPRISE:OK"), noms(s.etapes()));
        assertEquals(List.of("CONFIRMATION_DEPOT", "NOUVELLE_CANDIDATURE_ENTREPRISE"), monde.types());
        assertTrue(monde.notifications.get(1).contains("contact@novapay.ma"));
        Candidature c = service.consulter(s.candidatureId());
        assertEquals("DEPOSEE", c.statut());
        assertEquals(67, c.scoreCompetences());
        assertEquals(1, dao.historique(c.id()).size());
    }

    // Rejets : une règle à la fois

    @Test
    void r1EtudiantSuspendu() {
        monde.etudiants.put(17L, new EtatEtudiant(true, false, "Le compte de l'étudiant est suspendu : il ne peut pas candidater.", "Sara Bouzidi",
                "sara@inpt.ac.ma", "ASEDS", "2A"));
        Soumission s = deposer(17, 1);
        rejeteePar(s, "R1");
        assertEquals(List.of("CANDIDATURE_REJETEE"), monde.types());
        assertTrue(monde.notifications.get(0).contains("sara@inpt.ac.ma"));
        assertTrue(monde.notifications.get(0).contains("Stage AMOA"), "le courriel nomme l'offre même pour un rejet R1");
        assertEquals(List.of("RECEPTION:OK", "VERIFICATION_ETUDIANT:REJET", "PASSERELLE_DECISION:REJET", "NOTIFICATION_REJET:OK"), noms(s.etapes()),
                "les contrôles suivants ne sont pas exécutés");
    }

    @Test
    void r1EtudiantInconnuRejeteSansDestinataire() {
        Soumission s = deposer(404, 1);
        rejeteePar(s, "R1");
        assertTrue(monde.notifications.isEmpty());
        assertTrue(noms(s.etapes()).contains("NOTIFICATION_REJET:INFO"));
    }

    @Test
    void r2OffreNonActive() {
        monde.offres.put(11L, new EtatOffre(true, "Sécurité des SI", "BROUILLON", true, true, null, 1, 16));
        Soumission s = deposer(1, 11);
        rejeteePar(s, "R2");
        assertTrue(s.motifRejet().contains("BROUILLON"));
        assertTrue(monde.notifications.get(0).contains("Sécurité des SI"));
    }

    @Test
    void r2OffreInconnue() {
        rejeteePar(deposer(1, 404), "R2");
        assertTrue(monde.notifications.get(0).contains("n° 404"));
    }

    @Test
    void r2EntrepriseInactive() {
        monde.offres.put(12L, new EtatOffre(true, "Applications métier", "ACTIVE", true, true, null, 8, 12));
        monde.entreprises.put(8L, new EtatEntreprise(true, false, "L'entreprise Médina Systèmes n'est pas active.", "Médina Systèmes", "c@m.ma", null));
        Soumission s = deposer(1, 12);
        rejeteePar(s, "R2");
        assertTrue(s.motifRejet().contains("Médina Systèmes"));
    }

    @Test
    void r3DateLimiteDepassee() {
        monde.offres.put(13L, new EtatOffre(true, "Consultant décisionnel", "ACTIVE", false, true, "La date limite de candidature est dépassée (2026-11-04).", 1, 12));
        Soumission s = deposer(1, 13);
        rejeteePar(s, "R3");
        assertEquals(List.of("RECEPTION:OK", "VERIFICATION_ETUDIANT:OK", "VERIFICATION_OFFRE:OK", "CONTROLE_CANDIDATURE:REJET",
                "PASSERELLE_DECISION:REJET", "NOTIFICATION_REJET:OK"), noms(s.etapes()));
    }

    @Test
    void r4PlusDePlace() {
        monde.offres.put(14L, new EtatOffre(true, "Chef de projet junior", "ACTIVE", true, false, "Cette offre n'a plus de place disponible.", 1, 12));
        rejeteePar(deposer(1, 14), "R4");
    }

    @Test
    void r5Doublon() {
        dao.ajouter(1, 1, "DEPOSEE");
        Soumission s = deposer(1, 1);
        rejeteePar(s, "R5");
        assertEquals(1, dao.candidatures.size());
    }

    @Test
    void r5UneCandidatureAnnuleeNEmpechePasDeRecandidater() {
        dao.ajouter(1, 1, "ANNULEE");
        assertEquals("ENREGISTREE", deposer(1, 1).statut());
    }

    @Test
    void r6CinqCandidaturesActives() {
        for (long offre = 2; offre <= 4; offre++) {
            dao.ajouter(1, offre, "DEPOSEE");
        }
        dao.ajouter(1, 5, "EN_COURS_EVALUATION");
        dao.ajouter(1, 6, "EN_COURS_EVALUATION");
        dao.ajouter(1, 7, "REFUSEE");
        Soumission s = deposer(1, 1);
        rejeteePar(s, "R6");
        assertTrue(s.motifRejet().contains("5"));
    }

    @Test
    void r6QuatreActivesEtDesTermineesNeBloquentPas() {
        for (long offre = 2; offre <= 5; offre++) {
            dao.ajouter(1, offre, "DEPOSEE");
        }
        dao.ajouter(1, 6, "ACCEPTEE");
        dao.ajouter(1, 7, "ANNULEE");
        assertEquals("ENREGISTREE", deposer(1, 1).statut());
    }

    @Test
    void r7NiveauInsuffisant() {
        monde.compatibilites.put(1L, new Compatibilite(true, false, 33, "Cette offre exige le niveau 2A au minimum (niveau de l'étudiant : 1A)."));
        Soumission s = deposer(1, 1);
        rejeteePar(s, "R7");
        assertTrue(s.motifRejet().contains("2A"));
    }

    @Test
    void r7FiliereNonAcceptee() {
        monde.compatibilites.put(1L, new Compatibilite(false, true, 0, "La filière ICCN n'est pas acceptée par cette offre."));
        rejeteePar(deposer(1, 1), "R7");
    }

    @Test
    void r7UnScoreNulNeBloquePas() {
        monde.compatibilites.put(1L, new Compatibilite(true, true, 0, null));
        assertEquals("ENREGISTREE", deposer(1, 1).statut());
    }

    // Gestion des erreurs et indisponibilités

    @Test
    void s1IndisponibleDonne503SoumissionEnErreurEtAlerte() {
        monde.enPanne.add("etudiants");
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> deposer(1, 1));
        assertEquals(ErreurMetier.Type.INDISPONIBLE, e.type());
        assertEquals("SERVICE_ETUDIANTS_INDISPONIBLE", e.code());
        assertTrue(e.getMessage().contains("soumission 1"), e.getMessage());
        Soumission s = service.soumission(1);
        assertEquals("ERREUR", s.statut());
        assertTrue(noms(s.etapes()).contains("ERREUR:ECHEC"));
        assertEquals(List.of("ALERTE_RESPONSABLE"), monde.types());
        assertTrue(monde.notifications.get(0).contains("responsable@inpt.ac.ma"));
        assertTrue(dao.candidatures.isEmpty());
    }

    @Test
    void s2IndisponibleALEtape3() {
        monde.enPanne.add("entreprises");
        assertEquals("SERVICE_ENTREPRISES_INDISPONIBLE", assertThrows(ErreurMetier.class, () -> deposer(1, 1)).code());
        assertTrue(service.soumission(1).etapes().stream().anyMatch(x -> x.detail() != null && x.detail().startsWith("Vérification de l'offre")));
    }

    @Test
    void s5IndisponibleNEmpechePasLEnregistrement() {
        monde.enPanne.add("notifications");
        Soumission s = deposer(1, 1);
        assertEquals("ENREGISTREE", s.statut());
        assertTrue(noms(s.etapes()).containsAll(List.of("NOTIFICATION_DEPOT:ECHEC", "TRANSMISSION_ENTREPRISE:ECHEC")));
    }

    @Test
    void uneDemandeInvalideNeCreeAucuneSoumission() {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> orchestrateur.deposer(new NouvelleSoumission(1L, null, "trop court"), "x"));
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, e.type());
        assertEquals(2, e.details().size());
        assertTrue(dao.soumissions.isEmpty());
    }

    // Décision de l'entreprise (étapes 7 à 9)

    private Candidature enEvaluation() {
        Soumission s = deposer(1, 1);
        monde.notifications.clear();
        return service.demarrerEvaluation(s.candidatureId(), "entreprise:1");
    }

    @Test
    void r10AccepterRetireUnePlaceCreeLeStageEtPasseLEtudiantEnStage() {
        Candidature c = enEvaluation();
        ResultatDecision r = orchestrateur.decider(c.id(), new Decision("ACCEPTER", "Bienvenue"), "entreprise:1");
        assertEquals("ACCEPTEE", r.candidature().statut());
        assertEquals(1, monde.places.get(1L));
        assertEquals(List.of(c.id()), monde.stages);
        assertTrue(monde.enStage.contains(1L));
        assertEquals(List.of("ACCEPTATION"), monde.types());
        assertTrue(monde.notifications.get(0).contains("Bienvenue"));
        assertEquals(List.of("DECISION_ENTREPRISE:OK", "MISE_A_JOUR_PLACES:OK", "NOTIFICATION_RESULTAT:OK", "CREATION_STAGE:OK",
                "MISE_A_JOUR_ETUDIANT:OK"), noms(r.etapes()));
    }

    @Test
    void r10SansPlaceLAcceptationEstRefuseeEtRienNeChange() {
        Candidature c = enEvaluation();
        monde.places.put(1L, 0);
        assertEquals("PLUS_DE_PLACE", assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("ACCEPTER", null), "entreprise:1")).code());
        assertEquals("EN_COURS_EVALUATION", service.consulter(c.id()).statut());
        assertTrue(monde.stages.isEmpty());
        assertTrue(monde.notifications.isEmpty());
    }

    @Test
    void r10SiS6EstEnPanneLAcceptationResteEtLeSuiviSeRejoue() {
        Candidature c = enEvaluation();
        monde.enPanne.add("suivi");
        ResultatDecision r = orchestrateur.decider(c.id(), new Decision("ACCEPTER", null), "entreprise:1");
        assertEquals("ACCEPTEE", r.candidature().statut());
        assertTrue(noms(r.etapes()).contains("CREATION_STAGE:ECHEC"));
        assertTrue(noms(r.etapes()).contains("MISE_A_JOUR_ETUDIANT:OK"));
        assertTrue(monde.types().contains("ALERTE_RESPONSABLE"));
        assertEquals(1, monde.places.get(1L), "la place n'est retirée qu'une fois");

        monde.enPanne.clear();
        ResultatDecision reprise = orchestrateur.reprendreSuivi(c.id(), "responsable");
        assertEquals(List.of("CREATION_STAGE:OK", "MISE_A_JOUR_ETUDIANT:OK"), noms(reprise.etapes()));
        assertEquals(List.of(c.id()), monde.stages);
        assertEquals(1, monde.places.get(1L), "reprendre le suivi ne retire pas de seconde place");
        orchestrateur.reprendreSuivi(c.id(), "responsable");
        assertEquals(1, monde.stages.size(), "rejouer encore ne crée pas de doublon");
    }

    @Test
    void refuser() {
        Candidature c = enEvaluation();
        ResultatDecision r = orchestrateur.decider(c.id(), new Decision("REFUSER", "Profil trop éloigné"), "entreprise:1");
        assertEquals("REFUSEE", r.candidature().statut());
        assertEquals(List.of("REFUS"), monde.types());
        assertEquals(2, monde.places.get(1L));
        assertTrue(monde.stages.isEmpty());
    }

    @Test
    void r11DemanderUnComplementLaisseLaCandidatureEnEvaluationPuisLaDecisionReprend() {
        Candidature c = enEvaluation();
        ResultatDecision r = orchestrateur.decider(c.id(), new Decision("DEMANDER_COMPLEMENT", "Précisez vos disponibilités."), "entreprise:1");
        assertEquals("EN_COURS_EVALUATION", r.candidature().statut());
        assertEquals(List.of("DEMANDE_COMPLEMENT"), monde.types());
        assertTrue(monde.notifications.get(0).contains("Précisez vos disponibilités."));
        var dernier = dao.historique(c.id()).get(dao.historique(c.id()).size() - 1);
        assertEquals("EN_COURS_EVALUATION", dernier.ancienStatut());
        assertEquals("EN_COURS_EVALUATION", dernier.nouveauStatut());
        assertTrue(dernier.commentaire().contains("complément"));
        // La boucle : l'entreprise peut ensuite décider.
        assertEquals("ACCEPTEE", orchestrateur.decider(c.id(), new Decision("ACCEPTER", null), "entreprise:1").candidature().statut());
    }

    @Test
    void r11UnComplementSansCommentaireEstInvalide() {
        Candidature c = enEvaluation();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("DEMANDER_COMPLEMENT", "  "), "x")).type());
    }

    @Test
    void uneDecisionInconnueEstInvalide() {
        Candidature c = enEvaluation();
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("PEUT_ETRE", null), "x")).type());
    }

    @Test
    void decideurAvantLEvaluationOuApresLaDecisionDonne409() {
        Soumission s = deposer(1, 1);
        ErreurMetier avant = assertThrows(ErreurMetier.class, () -> orchestrateur.decider(s.candidatureId(), new Decision("ACCEPTER", null), "x"));
        assertEquals("DECISION_IMPOSSIBLE", avant.code());
        assertTrue(avant.getMessage().contains("Démarrez d'abord"));
        service.demarrerEvaluation(s.candidatureId(), "x");
        orchestrateur.decider(s.candidatureId(), new Decision("REFUSER", null), "x");
        assertEquals("DECISION_IMPOSSIBLE", assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(s.candidatureId(), new Decision("ACCEPTER", null), "x")).code());
    }

    @Test
    void s1IndisponibleALaDecisionNeChangeRien() {
        Candidature c = enEvaluation();
        monde.enPanne.add("etudiants");
        assertEquals(ErreurMetier.Type.INDISPONIBLE, assertThrows(ErreurMetier.class,
                () -> orchestrateur.decider(c.id(), new Decision("ACCEPTER", null), "x")).type());
        assertEquals("EN_COURS_EVALUATION", service.consulter(c.id()).statut());
        assertEquals(2, monde.places.get(1L));
    }

    @Test
    void leSuiviNeSeReprendQuePourUneCandidatureAcceptee() {
        Candidature c = enEvaluation();
        assertEquals("SUIVI_IMPOSSIBLE", assertThrows(ErreurMetier.class, () -> orchestrateur.reprendreSuivi(c.id(), "x")).code());
    }
}
