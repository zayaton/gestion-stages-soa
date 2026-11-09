package inpt

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.time.LocalDate

/**
 * Processus « Candidature » : tout ce que les tâches Bonita calculent, dans une seule classe Groovy du projet Bonita.
 *
 * Toutes les données du cas vivent dans une seule variable de processus, « dossier » (java.util.Map) :
 * chaque tâche n'a donc qu'une opération, « dossier prend la valeur de SoaProcessus.apresXxx(dossier, réponse) ».
 *
 * Deux façons de câbler une tâche automatique (voir docs/bonita/GUIDE_BONITA.md) :
 *  - avec les connecteurs Bonita (SOAP et REST) : le connecteur fait l'appel avec enveloppeXxx(dossier) ou corpsXxx(dossier),
 *    puis l'opération appelle apresXxx(dossier, réponse du connecteur) ;
 *  - sans connecteur (secours) : l'opération appelle xxx(dossier), qui fait l'appel HTTP elle-même (HttpURLConnection).
 *
 * Vérifiée en dehors de Bonita, avec Groovy 3.0.25 (la version de Bonita Studio 2025.2), contre les vrais services :
 * tests/bonita/SimulationBonita.groovy rejoue le processus et ses deux branches de décision.
 */
class SoaProcessus {

    // Adresses et identifiants : à adapter si les services ne tournent pas sur la même machine que Bonita.
    static String S1 = 'http://localhost:8081/ws/etudiants'
    static String S2 = 'http://localhost:8082/ws/entreprises'
    static String S3 = 'http://localhost:8083/ws/offres'
    static String S4 = 'http://localhost:8084/api/v1'
    static String S5 = 'http://localhost:8085/api/v1'
    static String S6 = 'http://localhost:8086/api/v1'
    static String WS_UTILISATEUR = 'bonita'
    static String WS_MOT_DE_PASSE = 'Bonita#Ws2026'
    static String CLE_API = 'cle-bonita-2026-Xp4MzN'
    static String RESPONSABLE = 'responsable@inpt.ac.ma'
    static int DEBUT_STAGE_JOURS = 30

    static final String NS_ETU = 'http://stages.inpt.ma/etudiants'
    static final String NS_ENT = 'http://stages.inpt.ma/entreprises'
    static final String NS_OFF = 'http://stages.inpt.ma/offres'

    // ----------------------------------------------------------------------------------------------------------------
    // Démarrage : contrat d'instanciation -> dossier

    /** Valeur initiale de la variable « dossier », à partir des entrées du contrat. */
    static Map initialiser(def soumissionId, def etudiantId, def offreId, String lettreMotivation) {
        [soumissionId    : soumissionId as Long, etudiantId: etudiantId as Long, offreId: offreId as Long,
         lettreMotivation: lettreMotivation, regleRejet: null, motifRejet: null, etapeReussie: 'Réception de la candidature']
    }

    /** Identifiant de corrélation : le même dans les journaux de tous les services pour cette candidature. */
    static String correlation(Map d) { "soumission-${d.soumissionId}" }

    // ----------------------------------------------------------------------------------------------------------------
    // Étape 2 : vérification de l'étudiant (S1, SOAP)

    static String enveloppeVerifierEtudiant(Map d) {
        enveloppe(NS_ETU, "<s:verifierEtudiant><s:id>${d.etudiantId}</s:id></s:verifierEtudiant>", d)
    }

    static Map apresVerifierEtudiant(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        r.etudiantExiste = valeur(x, 'existe') == 'true'
        r.etudiantEligible = valeur(x, 'eligible') == 'true'
        r.nomEtudiant = valeur(x, 'nomComplet')
        r.emailEtudiant = valeur(x, 'email')
        r.filiere = valeur(x, 'filiere')
        r.niveau = valeur(x, 'niveau')
        if (!r.etudiantEligible) {
            rejeter(r, 'R1', valeur(x, 'motif'))
        }
        r.etapeReussie = "Vérification de l'étudiant"
        journal(r, 'VERIFICATION_ETUDIANT', r.etudiantEligible ? 'OK' : 'REJET', r.etudiantEligible ? 'Étudiant actif (R1)' : "R1 : ${r.motifRejet}")
        r
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Étape 3 : vérification de l'offre (S3), puis de l'entreprise (S2) et du profil (S3)

    static String enveloppeDisponibilite(Map d) {
        enveloppe(NS_OFF, "<s:verifierDisponibilite><s:offreId>${d.offreId}</s:offreId></s:verifierDisponibilite>", d)
    }

    static Map apresDisponibilite(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        r.offreExiste = valeur(x, 'existe') == 'true'
        r.offreActive = valeur(x, 'active') == 'true'
        r.dateLimiteOk = valeur(x, 'dateLimiteRespectee') == 'true'
        r.placesOk = valeur(x, 'placesDisponibles') == 'true'
        r.motifDisponibilite = valeur(x, 'motif')
        String entreprise = valeur(x, 'entrepriseId')
        r.entrepriseId = entreprise ? entreprise as Long : null
        if (!r.offreExiste || !r.offreActive) {
            rejeter(r, 'R2', r.motifDisponibilite)
            journal(r, 'VERIFICATION_OFFRE', 'REJET', "R2 : ${r.motifRejet}")
        }
        r.etapeReussie = "Vérification de l'offre"
        r
    }

    /** Condition de la passerelle « Offre existante et active ? » (branche oui). */
    static boolean offreValable(Map d) { d.offreExiste && d.offreActive }

    static String enveloppeConsulterOffre(Map d) {
        enveloppe(NS_OFF, "<s:consulterOffre><s:id>${d.offreId}</s:id></s:consulterOffre>", d)
    }

    static Map apresConsulterOffre(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        r.titreOffre = valeur(x, 'titre')
        r.dureeSemaines = (valeur(x, 'dureeSemaines') ?: '12') as Integer
        r
    }

    static String enveloppeVerifierEntreprise(Map d) {
        enveloppe(NS_ENT, "<s:verifierEntreprise><s:id>${d.entrepriseId}</s:id></s:verifierEntreprise>", d)
    }

    static Map apresVerifierEntreprise(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        r.raisonSociale = valeur(x, 'raisonSociale')
        r.emailEntreprise = valeur(x, 'email')
        if (valeur(x, 'referencee') != 'true' || valeur(x, 'active') != 'true') {
            rejeter(r, 'R2', valeur(x, 'motif'))
        }
        r
    }

    static String enveloppeCompatibilite(Map d) {
        enveloppe(NS_OFF, "<s:verifierCompatibilite><s:offreId>${d.offreId}</s:offreId><s:etudiantId>${d.etudiantId}</s:etudiantId></s:verifierCompatibilite>", d)
    }

    static Map apresCompatibilite(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        r.score = (valeur(x, 'scoreCompetences') ?: '0') as Integer
        if (valeur(x, 'filiereAcceptee') != 'true' || valeur(x, 'niveauSuffisant') != 'true') {
            rejeter(r, 'R7', valeur(x, 'motif'))
        }
        r.etapeReussie = "Vérification de l'entreprise et du profil"
        journal(r, 'VERIFICATION_OFFRE', r.regleRejet ? 'REJET' : 'OK',
                r.regleRejet ? "${r.regleRejet} : ${r.motifRejet}" : 'Offre active, entreprise active, profil compatible (R2, R7)')
        r
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Étape 4 : contrôle de la candidature (R3, R4 d'après S3 ; R5, R6 auprès de S4, REST)

    static String urlDoublon(Map d) { "${S4}/candidatures/verification-doublon?etudiantId=${d.etudiantId}&offreId=${d.offreId}" }

    static Map apresDoublon(Map d, def reponse) {
        Map r = new HashMap(d)
        r.doublon = json(reponse).doublon == true
        r
    }

    static String urlActives(Map d) { "${S4}/etudiants/${d.etudiantId}/candidatures/actives/compte" }

    /** Dernier contrôle : R3, R4, R5 puis R6, dans cet ordre ; le premier motif est gardé. */
    static Map apresActives(Map d, def reponse) {
        def compte = json(reponse)
        Map r = new HashMap(d)
        boolean dejaRejetee = r.regleRejet != null
        if (!dejaRejetee) {
            if (!r.dateLimiteOk) {
                rejeter(r, 'R3', r.motifDisponibilite ?: 'La date limite de candidature est dépassée.')
            } else if (!r.placesOk) {
                rejeter(r, 'R4', r.motifDisponibilite ?: "Cette offre n'a plus de place disponible.")
            } else if (r.doublon) {
                rejeter(r, 'R5', 'Vous avez déjà une candidature en cours pour cette offre.')
            } else if (compte.limiteAtteinte == true) {
                rejeter(r, 'R6', "Vous avez déjà ${compte.limite} candidatures actives : c'est le maximum.")
            }
            journal(r, 'CONTROLE_CANDIDATURE', r.regleRejet ? 'REJET' : 'OK',
                    r.regleRejet ? "${r.regleRejet} : ${r.motifRejet}" : 'Date limite, places, doublon et limite de 5 respectés (R3 à R6)')
        }
        r.etapeReussie = 'Contrôle de la candidature'
        r
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Étape 5 : passerelle de décision

    /** Condition de la passerelle « Conditions satisfaites ? » (branche oui). */
    static boolean conditionsSatisfaites(Map d) { d.regleRejet == null }

    // Branche « non » : rejet enregistré dans S4, puis étudiant notifié (S5)

    static String urlRejet(Map d) { "${S4}/soumissions/${d.soumissionId}/rejet" }

    static String corpsRejet(Map d) { JsonOutput.toJson([regle: d.regleRejet, motif: (d.motifRejet ?: 'Conditions non satisfaites.').take(500)]) }

    static Map apresRejet(Map d, def reponse) {
        Map r = new HashMap(d)
        r.etapeReussie = 'Enregistrement du rejet'
        r
    }

    /** Étudiant connu : courriel de rejet. Étudiant inconnu : personne à prévenir, le responsable est alerté de l'anomalie. */
    static String corpsNotificationRejet(Map d) {
        if (!d.emailEtudiant) {
            return notification('ALERTE_RESPONSABLE', RESPONSABLE, [etape: 'Réception de la candidature',
                    erreur: "Dépôt pour un étudiant inconnu (${d.etudiantId}) : ${d.motifRejet}", reference: "Soumission ${d.soumissionId}"], d)
        }
        notification('CANDIDATURE_REJETEE', d.emailEtudiant, [nomEtudiant: d.nomEtudiant, titreOffre: titre(d), motif: d.motifRejet ?: 'Conditions non satisfaites.',
                regle: d.regleRejet], d)
    }

    // Branche « oui » : enregistrement (S4), confirmation (S5), transmission à l'entreprise (S5, étape 6)

    static String urlCandidatures() { "${S4}/candidatures" }

    static String corpsEnregistrement(Map d) {
        JsonOutput.toJson([soumissionId: d.soumissionId, etudiantId: d.etudiantId, offreId: d.offreId, entrepriseId: d.entrepriseId,
                           lettreMotivation: d.lettreMotivation, scoreCompetences: d.score])
    }

    static Map apresEnregistrement(Map d, def reponse) {
        Map r = new HashMap(d)
        r.candidatureId = json(reponse).id as Long
        journal(r, 'PASSERELLE_DECISION', 'OK', 'Conditions satisfaites')
        journal(r, 'ENREGISTREMENT', 'OK', "Candidature ${r.candidatureId} enregistrée")
        r.etapeReussie = 'Enregistrement de la candidature'
        r
    }

    static String corpsConfirmation(Map d) {
        notification('CONFIRMATION_DEPOT', d.emailEtudiant, [nomEtudiant: d.nomEtudiant, titreOffre: d.titreOffre, entreprise: d.raisonSociale], d)
    }

    static String corpsTransmission(Map d) {
        notification('NOUVELLE_CANDIDATURE_ENTREPRISE', d.emailEntreprise, [entreprise: d.raisonSociale, titreOffre: d.titreOffre,
                nomEtudiant: d.nomEtudiant, filiere: d.filiere, niveau: d.niveau, score: String.valueOf(d.score ?: 0)], d)
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Étape 7 : décision de l'entreprise (tâche humaine, contrat {decision, commentaire})

    static Map apresDecision(Map d, String decision, String commentaire) {
        Map r = new HashMap(d)
        r.decision = decision
        r.commentaire = commentaire?.trim() ?: null
        r.etapeReussie = "Décision de l'entreprise"
        r
    }

    static boolean decisionEst(Map d, String valeur) { d.decision == valeur }

    /** R11 : l'étudiant est prévenu ; la candidature reste en évaluation et le processus revient à la décision. */
    static String corpsComplement(Map d) {
        notification('DEMANDE_COMPLEMENT', d.emailEtudiant, [nomEtudiant: d.nomEtudiant, titreOffre: d.titreOffre, entreprise: d.raisonSociale,
                commentaire: d.commentaire ?: 'Merci de compléter votre dossier.'], d)
    }

    /** Minuteur sur la tâche de décision : l'entreprise est relancée, puis la tâche est recréée. */
    static String corpsRelance(Map d) {
        notification('NOUVELLE_CANDIDATURE_ENTREPRISE', d.emailEntreprise, [entreprise: d.raisonSociale, titreOffre: d.titreOffre,
                nomEtudiant: d.nomEtudiant, filiere: d.filiere, niveau: d.niveau, score: String.valueOf(d.score ?: 0)], d)
    }

    // Branche ACCEPTER (R10) : place retirée (S3), statut (S4), résultat notifié (S5), stage (S6), étudiant EN_STAGE (S1)

    static String enveloppeRetirerPlace(Map d) {
        enveloppe(NS_OFF, "<s:decrementerPlaces><s:offreId>${d.offreId}</s:offreId></s:decrementerPlaces>", d)
    }

    static Map apresRetirerPlace(Map d, def reponse) {
        def x = xml(reponse)
        Map r = new HashMap(d)
        journal(r, 'MISE_A_JOUR_PLACES', 'OK', "Place retirée de l'offre (R10) : ${valeur(x, 'nombrePlaces')} restante(s), statut ${valeur(x, 'statut')}")
        r.etapeReussie = 'Réservation de la place'
        r
    }

    static String urlStatut(Map d) { "${S4}/candidatures/${d.candidatureId}/statut" }

    static String corpsStatut(Map d, String statut) {
        JsonOutput.toJson([statut: statut, commentaire: d.commentaire ?: (statut == 'ACCEPTEE' ? 'Candidature acceptée' : 'Candidature refusée')])
    }

    static String corpsAcceptation(Map d) {
        Map variables = [nomEtudiant: d.nomEtudiant, titreOffre: d.titreOffre, entreprise: d.raisonSociale]
        if (d.commentaire) variables.commentaire = d.commentaire
        notification('ACCEPTATION', d.emailEtudiant, variables, d)
    }

    static String urlStages() { "${S6}/stages" }

    static String corpsStage(Map d) {
        LocalDate debut = LocalDate.now().plusDays(DEBUT_STAGE_JOURS)
        LocalDate fin = debut.plusWeeks(Math.max(1, (d.dureeSemaines ?: 12) as int))
        JsonOutput.toJson([candidatureId: d.candidatureId, etudiantId: d.etudiantId, offreId: d.offreId, entrepriseId: d.entrepriseId,
                           dateDebut: debut.toString(), dateFin: fin.toString()])
    }

    static Map apresStage(Map d, def reponse) {
        Map r = new HashMap(d)
        def stage = json(reponse)
        journal(r, 'CREATION_STAGE', 'OK', "Dossier de stage ${stage.id} créé du ${stage.dateDebut} au ${stage.dateFin} (R10)")
        r.stageId = stage.id as Long
        r.etapeReussie = 'Création du dossier de stage'
        r
    }

    static String enveloppePasserEnStage(Map d) {
        enveloppe(NS_ETU, "<s:changerStatutEtudiant><s:id>${d.etudiantId}</s:id><s:statut>EN_STAGE</s:statut></s:changerStatutEtudiant>", d)
    }

    static Map apresPasserEnStage(Map d, def reponse) {
        xml(reponse)
        Map r = new HashMap(d)
        journal(r, 'MISE_A_JOUR_ETUDIANT', 'OK', 'Étudiant passé EN_STAGE (R10)')
        r.etapeReussie = "Mise à jour du statut de l'étudiant"
        r
    }

    // Branche REFUSER

    static String corpsRefus(Map d) {
        Map variables = [nomEtudiant: d.nomEtudiant, titreOffre: d.titreOffre, entreprise: d.raisonSociale]
        if (d.commentaire) variables.commentaire = d.commentaire
        notification('REFUS', d.emailEtudiant, variables, d)
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Notifications (S5) : toutes passent par le même service, seule l'étape journalisée change

    static String urlNotifications() { "${S5}/notifications" }

    /** Après un envoi : l'étape est journalisée OK, ou ECHEC si S5 n'a pas pu envoyer le courriel (sans bloquer le processus). */
    static Map apresNotification(Map d, String etape, def reponse) {
        Map r = new HashMap(d)
        def n = json(reponse)
        boolean envoye = n.statut == 'ENVOYEE'
        journal(r, etape, envoye ? 'OK' : 'ECHEC', envoye ? "Courriel ${n.type} envoyé à ${n.destinataire}" : "Courriel ${n.type} non envoyé : ${n.erreur}")
        r.etapeReussie = libelle(etape)
        r
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Sous-processus d'erreur : un service n'a pas répondu

    static String urlErreur(Map d) { "${S4}/soumissions/${d.soumissionId}/erreur" }

    static String corpsErreur(Map d) {
        JsonOutput.toJson([etape: "Après « ${d.etapeReussie ?: 'Réception de la candidature'} »".toString(),
                           motif: 'Un service appelé par le processus ne répond pas. Le traitement est interrompu.'])
    }

    static String corpsAlerte(Map d) {
        notification('ALERTE_RESPONSABLE', RESPONSABLE, [etape: "Après « ${d.etapeReussie ?: 'Réception de la candidature'} »".toString(),
                erreur: 'Un service appelé par le processus Bonita ne répond pas.', reference: "Soumission ${d.soumissionId}".toString()], d)
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Mode secours sans connecteur : la même chose, appel HTTP compris (une opération par tâche)

    static Map verifierEtudiant(Map d) { secours(d) { apresVerifierEtudiant(d, soap(S1, enveloppeVerifierEtudiant(d))) } }

    static Map verifierOffre(Map d) { secours(d) { apresDisponibilite(d, soap(S3, enveloppeDisponibilite(d))) } }

    static Map verifierEntrepriseEtProfil(Map d) {
        secours(d) {
            Map r = apresConsulterOffre(d, soap(S3, enveloppeConsulterOffre(d)))
            r = apresVerifierEntreprise(r, soap(S2, enveloppeVerifierEntreprise(r)))
            apresCompatibilite(r, soap(S3, enveloppeCompatibilite(r)))
        }
    }

    static Map controlerCandidature(Map d) {
        secours(d) {
            Map r = apresDoublon(d, rest('GET', urlDoublon(d), null, d))
            apresActives(r, rest('GET', urlActives(r), null, r))
        }
    }

    static Map enregistrerRejet(Map d) { secours(d) { apresRejet(d, rest('POST', urlRejet(d), corpsRejet(d), d)) } }

    static Map notifierRejet(Map d) { secours(d) { apresNotification(d, 'NOTIFICATION_REJET', rest('POST', urlNotifications(), corpsNotificationRejet(d), d)) } }

    static Map enregistrer(Map d) { secours(d) { apresEnregistrement(d, rest('POST', urlCandidatures(), corpsEnregistrement(d), d)) } }

    static Map confirmerDepot(Map d) { secours(d) { apresNotification(d, 'NOTIFICATION_DEPOT', rest('POST', urlNotifications(), corpsConfirmation(d), d)) } }

    static Map transmettre(Map d) { secours(d) { apresNotification(d, 'TRANSMISSION_ENTREPRISE', rest('POST', urlNotifications(), corpsTransmission(d), d)) } }

    static Map demanderComplement(Map d) { secours(d) { apresNotification(d, 'NOTIFICATION_RESULTAT', rest('POST', urlNotifications(), corpsComplement(d), d)) } }

    static Map relancer(Map d) { secours(d) { apresNotification(d, 'TRANSMISSION_ENTREPRISE', rest('POST', urlNotifications(), corpsRelance(d), d)) } }

    static Map retirerPlace(Map d) { secours(d) { apresRetirerPlace(d, soap(S3, enveloppeRetirerPlace(d))) } }

    static Map enregistrerStatut(Map d, String statut) { secours(d) { rest('PATCH', urlStatut(d), corpsStatut(d, statut), d); new HashMap(d) } }

    static Map notifierAcceptation(Map d) { secours(d) { apresNotification(d, 'NOTIFICATION_RESULTAT', rest('POST', urlNotifications(), corpsAcceptation(d), d)) } }

    static Map creerStage(Map d) { secours(d) { apresStage(d, rest('POST', urlStages(), corpsStage(d), d)) } }

    static Map passerEnStage(Map d) { secours(d) { apresPasserEnStage(d, soap(S1, enveloppePasserEnStage(d))) } }

    static Map notifierRefus(Map d) { secours(d) { apresNotification(d, 'NOTIFICATION_RESULTAT', rest('POST', urlNotifications(), corpsRefus(d), d)) } }

    /** Sous-processus d'erreur, mode secours : S4 marque la soumission ERREUR, le responsable est alerté. */
    static Map signalerErreur(Map d) {
        try { rest('POST', urlErreur(d), corpsErreur(d), d) } catch (Exception ignore) { }
        try { rest('POST', urlNotifications(), corpsAlerte(d), d) } catch (Exception ignore) { }
        new HashMap(d)
    }

    /**
     * Mode secours : une panne ne peut pas déclencher l'événement d'erreur BPMN (réservé aux connecteurs).
     * Elle est donc signalée ici (S4 + responsable), puis l'exception fait passer la tâche à l'état « Échec » dans Bonita.
     */
    private static Map secours(Map d, Closure<Map> appel) {
        try {
            return appel.call()
        } catch (Exception e) {
            signalerErreur(d)
            throw new IllegalStateException("Service indisponible après « ${d.etapeReussie} » : ${e.message}", e)
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Outils : enveloppe SOAP avec WS-Security, appels HTTP, lecture XML et JSON, journal des étapes

    /** Enveloppe SOAP 1.1 complète : en-tête WS-Security UsernameToken et identifiant de corrélation. */
    static String enveloppe(String ns, String corps, Map d) {
        """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:s="${ns}">
  <soapenv:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>${WS_UTILISATEUR}</wsse:Username>
        <wsse:Password>${WS_MOT_DE_PASSE}</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
    <cor:CorrelationId xmlns:cor="http://stages.inpt.ma/commun">${correlation(d)}</cor:CorrelationId>
  </soapenv:Header>
  <soapenv:Body>${corps}</soapenv:Body>
</soapenv:Envelope>""".toString()
    }

    /** Corps JSON d'une notification S5. */
    static String notification(String type, String destinataire, Map variables, Map d) {
        Map corps = [type: type, destinataire: destinataire, variables: variables.findAll { it.value != null }.collectEntries { k, v -> [k, v.toString()] }]
        if (d.candidatureId) corps.candidatureId = d.candidatureId
        if (d.offreId) corps.offreId = d.offreId
        JsonOutput.toJson(corps)
    }

    private static String titre(Map d) { d.titreOffre ?: "n° ${d.offreId}".toString() }

    private static void rejeter(Map r, String regle, String motif) {
        if (r.regleRejet == null) {
            r.regleRejet = regle
            r.motifRejet = motif ?: "Règle ${regle} non respectée."
        }
    }

    private static String libelle(String etape) {
        [NOTIFICATION_REJET: "Notification du rejet", NOTIFICATION_DEPOT: 'Confirmation du dépôt', TRANSMISSION_ENTREPRISE: "Transmission à l'entreprise",
         NOTIFICATION_RESULTAT: 'Notification du résultat'][etape] ?: etape
    }

    /** POST SOAP. Une faute SOAP lève une exception : avec un connecteur, c'est lui qui la transforme en événement d'erreur. */
    static String soap(String url, String enveloppe) {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection()
        c.connectTimeout = 3000
        c.readTimeout = 10000
        c.requestMethod = 'POST'
        c.doOutput = true
        c.setRequestProperty('Content-Type', 'text/xml; charset=utf-8')
        c.setRequestProperty('SOAPAction', '""')
        c.outputStream.withWriter('UTF-8') { it << enveloppe }
        int code = c.responseCode
        String texte = (code < 400 ? c.inputStream : c.errorStream)?.getText('UTF-8') ?: ''
        if (code != 200) {
            def x = new XmlSlurper(false, true).parseText(texte)
            throw new IllegalStateException("Faute SOAP (HTTP ${code}) : ${valeur(x, 'faultstring') ?: texte.take(200)}")
        }
        texte
    }

    /** Appel REST avec la clé d'API de Bonita ; une réponse d'erreur lève une exception avec le message du service. */
    static String rest(String methode, String url, String corps, Map d) {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection()
        c.connectTimeout = 3000
        c.readTimeout = 15000
        if (methode == 'PATCH') {
            // HttpURLConnection ne connaît pas PATCH : surcharge acceptée par JAX-RS (Jersey).
            c.requestMethod = 'POST'
            c.setRequestProperty('X-HTTP-Method-Override', 'PATCH')
        } else {
            c.requestMethod = methode
        }
        c.setRequestProperty('Accept', 'application/json')
        c.setRequestProperty('X-API-Key', CLE_API)
        c.setRequestProperty('X-Acteur', 'processus-bonita')
        c.setRequestProperty('X-Correlation-Id', correlation(d))
        if (corps != null) {
            c.doOutput = true
            c.setRequestProperty('Content-Type', 'application/json; charset=utf-8')
            c.outputStream.withWriter('UTF-8') { it << corps }
        }
        int code = c.responseCode
        String texte = (code < 400 ? c.inputStream : c.errorStream)?.getText('UTF-8') ?: ''
        if (code >= 300) {
            String message = texte
            try { message = new JsonSlurper().parseText(texte).message } catch (Exception ignore) { }
            throw new IllegalStateException("${methode} ${url} : HTTP ${code} ${message}")
        }
        texte
    }

    /** Journal des étapes de S4 (vue BPMN en direct). Sans effet sur le processus si S4 ne répond pas. */
    static void journal(Map d, String etape, String resultat, String detail) {
        try {
            rest('POST', "${S4}/soumissions/${d.soumissionId}/etapes".toString(),
                    JsonOutput.toJson([etape: etape, resultat: resultat, detail: (detail ?: '').toString().take(500)]), d)
        } catch (Exception ignore) {
            // Le journal sert l'affichage ; il ne doit jamais interrompre le traitement d'une candidature.
        }
    }

    /** Réponse d'un connecteur SOAP (Document, Source ou texte) lue en XML ; une faute SOAP lève une exception. */
    static GPathResult xml(def reponse) {
        String texte
        if (reponse instanceof org.w3c.dom.Node) {
            texte = transformer(new DOMSource((org.w3c.dom.Node) reponse))
        } else if (reponse instanceof Source) {
            texte = transformer((Source) reponse)
        } else {
            texte = reponse?.toString() ?: ''
        }
        def x = new XmlSlurper(false, true).parseText(texte)
        if (x.'**'.find { it.name() == 'Fault' }) {
            throw new IllegalStateException("Faute SOAP : ${valeur(x, 'faultstring')}")
        }
        x
    }

    private static String transformer(Source source) {
        def t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, 'yes')
        StringWriter w = new StringWriter()
        t.transform(source, new StreamResult(w))
        w.toString()
    }

    /** Texte du premier élément de ce nom local, ou null. */
    static String valeur(GPathResult x, String nom) {
        def n = x.'**'.find { it.name() == nom }
        n == null || n.isEmpty() ? null : n.text()
    }

    /** Réponse d'un connecteur REST (texte JSON ou objet déjà lu). */
    static def json(def reponse) {
        reponse instanceof Map || reponse instanceof List ? reponse : new JsonSlurper().parseText(reponse.toString())
    }
}
