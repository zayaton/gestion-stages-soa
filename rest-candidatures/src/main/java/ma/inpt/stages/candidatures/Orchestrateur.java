package ma.inpt.stages.candidatures;

import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Decision;
import ma.inpt.stages.candidatures.Modeles.NouvelleSoumission;
import ma.inpt.stages.candidatures.Modeles.ResultatDecision;
import ma.inpt.stages.candidatures.Modeles.Soumission;

/**
 * Exécution du processus de candidature. Deux implémentations, choisies par ORCHESTRATION_MODE :
 * « bonita » (le moteur BPMN orchestre, référence du projet) et « direct » (les mêmes étapes en Java,
 * secours pour le développement et les tests automatiques).
 */
public interface Orchestrateur {

    /** Étapes 1 à 6 : réception, vérifications, passerelle de décision, enregistrement ou rejet, transmission à l'entreprise. */
    Soumission deposer(NouvelleSoumission demande, String acteur);

    /** Étapes 7 à 9 : décision de l'entreprise, notification du résultat, mise à jour du suivi (R10, R11). */
    ResultatDecision decider(long candidatureId, Decision decision, String acteur);

    /** Rejoue les étapes de suivi d'une candidature acceptée qui avaient échoué (stage, statut de l'étudiant). */
    ResultatDecision reprendreSuivi(long candidatureId, String acteur);

    /** Annulation par l'étudiant (R8). */
    Candidature annuler(long candidatureId, String acteur);
}
