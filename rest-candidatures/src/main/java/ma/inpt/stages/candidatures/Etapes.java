package ma.inpt.stages.candidatures;

import java.util.List;

/**
 * Noms des étapes du processus, identiques aux identifiants des tâches du diagramme BPMN :
 * la vue BPMN du portail colore une tâche dès que son étape figure dans le journal de la soumission.
 */
public final class Etapes {

    public static final String RECEPTION = "RECEPTION";
    public static final String VERIFICATION_ETUDIANT = "VERIFICATION_ETUDIANT";
    public static final String VERIFICATION_OFFRE = "VERIFICATION_OFFRE";
    public static final String CONTROLE_CANDIDATURE = "CONTROLE_CANDIDATURE";
    public static final String PASSERELLE_DECISION = "PASSERELLE_DECISION";
    public static final String NOTIFICATION_REJET = "NOTIFICATION_REJET";
    public static final String ENREGISTREMENT = "ENREGISTREMENT";
    public static final String NOTIFICATION_DEPOT = "NOTIFICATION_DEPOT";
    public static final String TRANSMISSION_ENTREPRISE = "TRANSMISSION_ENTREPRISE";
    public static final String DEBUT_EVALUATION = "DEBUT_EVALUATION";
    public static final String DECISION_ENTREPRISE = "DECISION_ENTREPRISE";
    public static final String NOTIFICATION_RESULTAT = "NOTIFICATION_RESULTAT";
    public static final String MISE_A_JOUR_PLACES = "MISE_A_JOUR_PLACES";
    public static final String CREATION_STAGE = "CREATION_STAGE";
    public static final String MISE_A_JOUR_ETUDIANT = "MISE_A_JOUR_ETUDIANT";
    public static final String ANNULATION = "ANNULATION";
    public static final String ERREUR = "ERREUR";

    public static final List<String> TOUTES = List.of(RECEPTION, VERIFICATION_ETUDIANT, VERIFICATION_OFFRE, CONTROLE_CANDIDATURE,
            PASSERELLE_DECISION, NOTIFICATION_REJET, ENREGISTREMENT, NOTIFICATION_DEPOT, TRANSMISSION_ENTREPRISE, DEBUT_EVALUATION,
            DECISION_ENTREPRISE, NOTIFICATION_RESULTAT, MISE_A_JOUR_PLACES, CREATION_STAGE, MISE_A_JOUR_ETUDIANT, ANNULATION, ERREUR);

    public static final List<String> RESULTATS = List.of("OK", "REJET", "ECHEC", "INFO");

    private Etapes() {
    }
}
