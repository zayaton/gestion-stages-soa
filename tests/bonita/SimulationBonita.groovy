// Simulation du processus Bonita « Candidature », pour tester sans Bonita Studio :
//  - répond aux appels de l'API REST de Bonita que fait S4 (connexion, processus, démarrage de cas, tâche humaine, suppression) ;
//  - déroule le graphe du processus (docs/bonita/processus-candidature.bpmn) tâche par tâche, en appelant pour chaque tâche
//    exactement ce que le guide fait configurer dans Bonita : le connecteur envoie enveloppeXxx / corpsXxx au service,
//    l'opération applique apresXxx à sa réponse (classe livrée docs/bonita/groovy/inpt/SoaProcessus.groovy).
// Ce que la simulation NE prouve PAS : la configuration réelle dans Bonita Studio (clics, connecteurs, acteurs).
//
// Lancement (scripts\test-bout-en-bout.ps1 -Orchestration simulation) : groovy -cp docs/bonita/groovy SimulationBonita.groovy 8099
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import inpt.SoaProcessus as P

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

int port = (args ? args[0] : '8099') as int
@Field ConcurrentHashMap<Long, Map> cas = new ConcurrentHashMap<>()
@Field AtomicLong numeros = new AtomicLong(1000)
def moteur = Executors.newSingleThreadExecutor()
// SIMULATION_SECOURS=1 : chaque tâche utilise la méthode « secours » du guide (aucun connecteur, appel HTTP dans l'opération).
@Field boolean SECOURS = System.getenv('SIMULATION_SECOURS') == '1'
def journal = { String m -> println "[simulation ${java.time.LocalTime.now().withNano(0)}] ${m}"; System.out.flush() }

// ---------------------------------------------------------------------------------------------------------------------
// Tâches automatiques : connecteur (appel) puis opération (apresXxx). Une panne déclenche le sous-processus d'erreur.

Map soap(Map d, String url, String enveloppe, Closure<Map> apres) { apres(d, P.soap(url, enveloppe)) }
Map rest(Map d, String methode, String url, String corps, Closure<Map> apres) { apres(d, P.rest(methode, url, corps, d)) }
Map notifier(Map d, String etape, String corps) { P.apresNotification(d, etape, P.rest('POST', P.urlNotifications(), corps, d)) }

void sousProcessusErreur(Map etat, Exception e, Closure journal) {
    Map d = etat.dossier
    journal "cas ${etat.id} : erreur après « ${d.etapeReussie} » (${e.message}) -> sous-processus d'erreur"
    // Tâches « Signaler l'interruption » (S4) et « Alerter le responsable » (S5), puis fin d'erreur.
    try { P.rest('POST', P.urlErreur(d), P.corpsErreur(d), d) } catch (Exception ignore) { journal "  S4 injoignable pour signaler l'erreur" }
    try { P.rest('POST', P.urlNotifications(), P.corpsAlerte(d), d) } catch (Exception ignore) { journal "  S5 injoignable pour l'alerte" }
    etat.etat = 'ERREUR'
}

/** Étapes 2 à 6, jusqu'à la tâche humaine ou à la fin « rejetée ». */
void etapesAutomatiques(Map etat, Closure journal) {
    if (SECOURS) {
        etapesAutomatiquesSecours(etat, journal)
        return
    }
    Map d = etat.dossier
    try {
        d = soap(d, P.S1, P.enveloppeVerifierEtudiant(d), P.&apresVerifierEtudiant)                          // Vérification de l'étudiant
        if (d.etudiantEligible) {                                                                               // passerelle « Étudiant éligible ? »
            d = soap(d, P.S3, P.enveloppeDisponibilite(d), P.&apresDisponibilite)                              // Vérification de l'offre
            if (P.offreValable(d)) {                                                                            // passerelle « Offre existante et active ? »
                d = soap(d, P.S3, P.enveloppeConsulterOffre(d), P.&apresConsulterOffre)                        // Vérification de l'entreprise et du profil :
                d = soap(d, P.S2, P.enveloppeVerifierEntreprise(d), P.&apresVerifierEntreprise)                //   trois connecteurs dans la même tâche
                d = soap(d, P.S3, P.enveloppeCompatibilite(d), P.&apresCompatibilite)
                d = rest(d, 'GET', P.urlDoublon(d), null, P.&apresDoublon)                                      // Contrôle de la candidature :
                d = rest(d, 'GET', P.urlActives(d), null, P.&apresActives)                                      //   deux connecteurs REST
            }
        }
        etat.dossier = d
        if (P.conditionsSatisfaites(d)) {                                                                       // Passerelle de décision
            d = rest(d, 'POST', P.urlCandidatures(), P.corpsEnregistrement(d), P.&apresEnregistrement)          // Enregistrement
            d = notifier(d, 'NOTIFICATION_DEPOT', P.corpsConfirmation(d))                                       // Confirmation du dépôt
            d = notifier(d, 'TRANSMISSION_ENTREPRISE', P.corpsTransmission(d))                                  // Transmission à l'entreprise
            etat.dossier = d
            etat.tache = numeros.incrementAndGet()
            etat.etat = 'DECISION'
            journal "cas ${etat.id} : candidature ${d.candidatureId} enregistrée, en attente de la décision (tâche ${etat.tache})"
        } else {
            d = rest(d, 'POST', P.urlRejet(d), P.corpsRejet(d), P.&apresRejet)                                 // Enregistrement du rejet
            d = notifier(d, 'NOTIFICATION_REJET', P.corpsNotificationRejet(d))                                  // Notification du rejet
            etat.dossier = d
            etat.etat = 'TERMINE'
            journal "cas ${etat.id} : rejeté (${d.regleRejet})"
        }
    } catch (Exception e) {
        etat.dossier = d
        sousProcessusErreur(etat, e, journal)
    }
}

/** Mode secours : une opération par tâche (colonne « Secours » du guide). Une panne : la méthode signale l'incident et lève. */
void etapesAutomatiquesSecours(Map etat, Closure journal) {
    Map d = etat.dossier
    try {
        d = P.verifierEtudiant(d)
        if (d.etudiantEligible) {
            d = P.verifierOffre(d)
            if (P.offreValable(d)) {
                d = P.verifierEntrepriseEtProfil(d)
                d = P.controlerCandidature(d)
            }
        }
        if (P.conditionsSatisfaites(d)) {
            d = P.transmettre(P.confirmerDepot(P.enregistrer(d)))
            etat.tache = numeros.incrementAndGet()
            etat.etat = 'DECISION'
        } else {
            d = P.notifierRejet(P.enregistrerRejet(d))
            etat.etat = 'TERMINE'
        }
        journal "cas ${etat.id} (secours) : ${etat.etat}"
    } catch (Exception e) {
        etat.etat = 'ECHEC'   // tâche en échec dans Bonita ; l'incident a déjà été signalé par la classe
        journal "cas ${etat.id} (secours) : tâche en échec, ${e.message}"
    }
    etat.dossier = d
}

void apresDecisionSecours(Map etat, String decision, String commentaire, Closure journal) {
    Map d = P.apresDecision(etat.dossier, decision, commentaire)
    try {
        if (P.decisionEst(d, 'ACCEPTER')) {
            d = P.passerEnStage(P.creerStage(P.notifierAcceptation(P.enregistrerStatut(P.retirerPlace(d), 'ACCEPTEE'))))
            etat.etat = 'TERMINE'
        } else if (P.decisionEst(d, 'REFUSER')) {
            d = P.notifierRefus(P.enregistrerStatut(d, 'REFUSEE'))
            etat.etat = 'TERMINE'
        } else {
            d = P.demanderComplement(d)
            etat.tache = numeros.incrementAndGet()
            etat.etat = 'DECISION'
        }
    } catch (Exception e) {
        etat.etat = 'ECHEC'
    }
    etat.dossier = d
}

/** Après la tâche humaine : passerelle « Décision ? » et ses trois branches. */
void apresDecision(Map etat, String decision, String commentaire, Closure journal) {
    if (SECOURS) {
        apresDecisionSecours(etat, decision, commentaire, journal)
        return
    }
    Map d = P.apresDecision(etat.dossier, decision, commentaire)
    try {
        if (P.decisionEst(d, 'ACCEPTER')) {
            d = soap(d, P.S3, P.enveloppeRetirerPlace(d), P.&apresRetirerPlace)                                // Réservation de la place (R10)
            d = rest(d, 'PATCH', P.urlStatut(d), P.corpsStatut(d, 'ACCEPTEE'), { m, r -> m })                    // Enregistrement de l'acceptation
            d = notifier(d, 'NOTIFICATION_RESULTAT', P.corpsAcceptation(d))                                     // Notification du résultat
            d = rest(d, 'POST', P.urlStages(), P.corpsStage(d), P.&apresStage)                                  // Création du dossier de stage
            d = soap(d, P.S1, P.enveloppePasserEnStage(d), P.&apresPasserEnStage)                              // Mise à jour du statut de l'étudiant
            etat.etat = 'TERMINE'
        } else if (P.decisionEst(d, 'REFUSER')) {
            d = rest(d, 'PATCH', P.urlStatut(d), P.corpsStatut(d, 'REFUSEE'), { m, r -> m })                     // Enregistrement du refus
            d = notifier(d, 'NOTIFICATION_RESULTAT', P.corpsRefus(d))                                          // Notification du refus
            etat.etat = 'TERMINE'
        } else {
            d = notifier(d, 'NOTIFICATION_RESULTAT', P.corpsComplement(d))                                     // Demande de complément (R11)
            etat.tache = numeros.incrementAndGet()                                                              // retour à la décision
            etat.etat = 'DECISION'
        }
        etat.dossier = d
        journal "cas ${etat.id} : décision ${decision} traitée, état ${etat.etat}"
    } catch (Exception e) {
        etat.dossier = d
        sousProcessusErreur(etat, e, journal)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// API REST de Bonita (ce qu'utilise ma.inpt.stages.candidatures.ClientBonita)

def repondre = { HttpExchange e, int code, Object corps ->
    byte[] octets = corps == null ? new byte[0] : JsonOutput.toJson(corps).getBytes('UTF-8')
    e.responseHeaders.set('Content-Type', 'application/json')
    e.sendResponseHeaders(code, octets.length == 0 ? -1 : octets.length)
    if (octets.length > 0) e.responseBody.withStream { it.write(octets) }
    e.close()
}

def serveur = HttpServer.create(new InetSocketAddress('127.0.0.1', port), 0)
serveur.createContext('/bonita') { HttpExchange e ->
    try {
        String chemin = URLDecoder.decode(e.requestURI.toString(), 'UTF-8').replace('/bonita', '')
        String corps = e.requestBody.getText('UTF-8')
        String methode = e.requestMethod
        if (chemin == '/loginservice') {
            e.responseHeaders.add('Set-Cookie', 'JSESSIONID=simulation; Path=/bonita')
            e.responseHeaders.add('Set-Cookie', 'X-Bonita-API-Token=jeton-simulation; Path=/bonita')
            repondre(e, 204, null)
        } else if (e.requestHeaders.getFirst('X-Bonita-API-Token') != 'jeton-simulation') {
            repondre(e, 401, [message: 'jeton absent'])
        } else if (chemin.startsWith('/API/bpm/process?')) {
            repondre(e, 200, chemin.contains('name=Candidature') && chemin.contains('version=1.0') ? [[id: '1', name: 'Candidature', version: '1.0']] : [])
        } else if (chemin == '/API/bpm/process/1/instantiation') {
            def contrat = new JsonSlurper().parseText(corps)
            long id = numeros.incrementAndGet()
            Map etat = [id: id, etat: 'AUTOMATIQUE', dossier: P.initialiser(contrat.soumissionId, contrat.etudiantId, contrat.offreId, contrat.lettreMotivation)]
            cas[id] = etat
            journal "cas ${id} démarré pour la soumission ${contrat.soumissionId}"
            moteur.submit { etapesAutomatiques(etat, journal) }
            repondre(e, 200, [caseId: String.valueOf(id)])
        } else if (chemin.startsWith('/API/bpm/humanTask?')) {
            long id = (chemin =~ /caseId=(\d+)/)[0][1] as long
            Map etat = cas[id]
            repondre(e, 200, etat?.etat == 'DECISION' ? [[id: String.valueOf(etat.tache), name: "Décision de l'entreprise", state: 'ready']] : [])
        } else if (chemin == '/API/system/session/unusedid') {
            repondre(e, 200, [user_id: '4', user_name: 'walter.bates'])
        } else if (methode == 'PUT' && chemin.startsWith('/API/bpm/humanTask/')) {
            repondre(e, 200, null)
        } else if (methode == 'POST' && chemin ==~ /\/API\/bpm\/userTask\/\d+\/execution/) {
            long tache = (chemin =~ /userTask\/(\d+)/)[0][1] as long
            Map etat = cas.values().find { it.tache == tache && it.etat == 'DECISION' }
            if (etat == null) {
                repondre(e, 404, [message: 'tâche inconnue'])
            } else {
                def contrat = new JsonSlurper().parseText(corps)
                etat.etat = 'AUTOMATIQUE'
                moteur.submit { apresDecision(etat, contrat.decision, contrat.commentaire, journal) }
                repondre(e, 204, null)
            }
        } else if (methode == 'DELETE' && chemin.startsWith('/API/bpm/case/')) {
            long id = chemin.substring('/API/bpm/case/'.length()) as long
            cas[id]?.etat = 'SUPPRIME'
            journal "cas ${id} supprimé (annulation)"
            repondre(e, 200, null)
        } else {
            repondre(e, 404, [message: "inconnu : ${methode} ${chemin}".toString()])
        }
    } catch (Exception ex) {
        journal "erreur de la simulation : ${ex}"
        repondre(e, 500, [message: ex.message])
    }
}
serveur.start()
journal "Bonita simulé à l'écoute sur http://127.0.0.1:${port}/bonita (processus Candidature 1.0, ${SECOURS ? 'tâches en mode secours' : 'tâches avec connecteurs'})"
Thread.currentThread().join()
