# Guide Bonita : processus « Candidature »

Ce guide fait construire, dans Bonita Studio Community 2025.2, le processus BPMN qui orchestre le traitement d'une candidature
(étapes 1 à 9 de l'énoncé), puis brancher S4 dessus (`ORCHESTRATION_MODE=bonita`).

**Fichiers à utiliser** (tous dans `docs/bonita/`) :

| Fichier | Rôle |
|---|---|
| `processus-candidature.bpmn` | Le diagramme BPMN 2.0 (à importer, ou à reproduire à la main) |
| `groovy/inpt/SoaProcessus.groovy` | Une seule classe Groovy : enveloppes SOAP avec WS-Security, corps REST, lecture des réponses, conditions |
| `CHECKLIST_BONITA.md` | La liste de contrôle finale, avec le résultat attendu à chaque étape |
| `deploiement/` | Outil qui construit et déploie le processus par programme (voir ci-dessous) |

## Le plus simple : déploiement automatique (aucun clic dans le Studio)

Bonita Studio ouvert (il démarre le serveur Bonita sur le port 8080), à la racine du projet :

```powershell
.\scripts\deployer-bonita.ps1                   # construit bpmn\Candidature--1.0.bar et le déploie, acteurs associés, processus activé
.\scripts\start-all.ps1 -Orchestration bonita
.\scripts\test-bout-en-bout.ps1 -Orchestration bonita -DelaiSecondes 900   # vérification face au vrai Bonita
```

Le processus déployé a les mêmes tâches, passerelles, tâche humaine, minuteur et boucle R11 que le diagramme, en **mode secours** :
chaque tâche est une opération Groovy qui appelle les services avec la classe `SoaProcessus` (aucun connecteur de la Marketplace).
Vérifié le 10/11/2026 face à Bonita Community 2025.2 : test de bout en bout réussi (59 + 6 vérifications).
Deux différences avec la version à connecteurs construite ci-dessous : une panne met la tâche « en erreur » dans Bonita
(l'incident est signalé à S4 et au responsable par la classe) au lieu de déclencher le sous-processus d'erreur, et le
processus n'apparaît pas comme diagramme modifiable dans le Studio. La suite de ce guide décrit la construction manuelle,
avec connecteurs.

## 0. Ce qui est vérifié, et ce qui ne l'est pas

| Élément | Vérifié ? | Comment |
|---|---|---|
| Classe `SoaProcessus.groovy` (Groovy 3.0.25, la version de votre Studio) | **Oui** | Exécutée contre les vrais services par `scripts\test-bout-en-bout.ps1 -Orchestration simulation` : 64 vérifications réussies |
| Ordre des tâches, passerelles, boucle R11, sous-processus d'erreur | **Oui, en simulation** | `tests\bonita\SimulationBonita.groovy` déroule ce graphe tâche par tâche |
| Pont S4 vers l'API REST de Bonita (connexion, démarrage, tâche, annulation) | **Oui, face à un Bonita simulé** | `OrchestrateurBonitaTest` et la simulation |
| Fichier `.bpmn` | **Oui** (syntaxe BPMN 2.0) | Lu sans avertissement par `bpmn-moddle`, l'analyseur de bpmn-js |
| Import du `.bpmn` dans Bonita Studio | **Non** | À essayer : section 3 |
| Libellés exacts des menus et des champs de connecteur de Bonita 2025.2 | **Non** | Je n'ai pas accès à l'interface ; les noms peuvent différer légèrement |
| Disponibilité des connecteurs SOAP et REST pour votre version | **Non** | Votre installation n'en contient aucun : ils s'ajoutent depuis la Marketplace (section 4) |

Si un libellé diffère, cherchez l'équivalent le plus proche ; si un connecteur manque, utilisez le **mode secours** décrit pour chaque tâche (aucun connecteur, une opération Groovy).

## 1. Avant de commencer

1. Démarrez la pile : `.\scripts\start-all.ps1 -Orchestration direct` (S4 en mode direct pendant la construction).
2. Vérifiez que les services répondent : `.\scripts\check-health.ps1` (7 lignes « 200 »).
3. Lancez **Bonita Studio Community 2025.2** (`C:\BonitaStudioCommunity-2025.2-u0\BonitaStudioCommunity.exe`). Le moteur de Bonita démarre avec le Studio, sur le port **8080** (`http://localhost:8080/bonita`).
4. Bonita Studio et les services tournent sur la même machine : les adresses `http://localhost:808x` de `SoaProcessus.groovy` conviennent telles quelles.

## 2. Créer le projet

1. **Fichier > Nouveau projet**. Nom : `stages-soa`. Valider.
2. Le projet s'ouvre sur sa page « Vue d'ensemble » (Project overview).

## 3. Le diagramme : importer, ou dessiner

### 3.1 Essayer l'import (gain de temps)

1. **Fichier > Importer > BPMN 2.0** (le Studio contient un importateur BPMN 2 : `org.bonitasoft.studio.importer`).
2. Choisir `docs\bonita\processus-candidature.bpmn`.
3. Contrôler le résultat avec le tableau de la section 3.2. Selon la version, l'import peut perdre le sous-processus d'erreur, le minuteur ou les conditions : les recréer à la main (sections 7 à 9). Les **identifiants** des tâches n'ont pas d'importance pour Bonita ; seuls comptent les **noms** et l'enchaînement.

Si l'import échoue : dessiner à la main (section 3.2), le fichier `.bpmn` reste la référence du dessin (ouvrez-le dans le portail en phase 6, ou sur https://demo.bpmn.io pour le voir).

### 3.2 Éléments du diagramme

Un **bassin** (pool) `Candidature`, version `1.0`, avec trois **couloirs** (lanes) : `Étudiant`, `Système`, `Entreprise`.

| Nom (à reprendre exactement) | Type Bonita | Couloir | Étape de l'énoncé |
|---|---|---|---|
| Candidature déposée | Événement de début | Étudiant | 1. Réception |
| Vérification de l'étudiant | Tâche de service | Système | 2 |
| Étudiant éligible ? | Passerelle exclusive (XOR) | Système | |
| Vérification de l'offre | Tâche de service | Système | 3 |
| Offre existante et active ? | Passerelle exclusive | Système | |
| Vérification de l'entreprise et du profil | Tâche de service | Système | 3 |
| Contrôle de la candidature | Tâche de service | Système | 4 |
| Conditions satisfaites ? | Passerelle exclusive | Système | 5. Passerelle de décision |
| Enregistrement de la candidature | Tâche de service | Système | 5 (oui) |
| Confirmation du dépôt | Tâche de service | Système | |
| Transmission à l'entreprise | Tâche de service | Système | 6 |
| Enregistrement du rejet | Tâche de service | Système | 5 (non) |
| Notification du rejet | Tâche de service | Système | 5 (non) |
| Candidature rejetée | Événement de fin | Système | |
| **Décision de l'entreprise** | **Tâche humaine** | Entreprise | 7 |
| 7 jours sans décision | Minuteur en bordure de « Décision de l'entreprise » | Entreprise | |
| Relance de l'entreprise | Tâche de service | Entreprise | |
| Décision ? | Passerelle exclusive | Entreprise | |
| Demande de complément | Tâche de service | Système | R11 |
| Réservation de la place | Tâche de service | Système | 9 (R10) |
| Enregistrement de l'acceptation | Tâche de service | Système | |
| Notification du résultat | Tâche de service | Système | 8 |
| Création du dossier de stage | Tâche de service | Système | 9 (R10) |
| Mise à jour du statut de l'étudiant | Tâche de service | Système | 9 (R10) |
| Candidature acceptée | Événement de fin | Système | |
| Enregistrement du refus | Tâche de service | Système | |
| Notification du refus | Tâche de service | Système | 8 |
| Candidature refusée | Événement de fin | Système | |
| **Gestion des erreurs techniques** | **Sous-processus événementiel** | Système | gestion des erreurs |

Enchaînement (flèches) :

```
Candidature déposée → Vérification de l'étudiant → Étudiant éligible ?
    oui → Vérification de l'offre → Offre existante et active ?
              oui → Vérification de l'entreprise et du profil → Contrôle de la candidature → Conditions satisfaites ?
              non → Conditions satisfaites ?
    non → Conditions satisfaites ?
Conditions satisfaites ?
    oui → Enregistrement de la candidature → Confirmation du dépôt → Transmission à l'entreprise → Décision de l'entreprise
    non → Enregistrement du rejet → Notification du rejet → Candidature rejetée
Décision de l'entreprise → Décision ?
    ACCEPTER → Réservation de la place → Enregistrement de l'acceptation → Notification du résultat
               → Création du dossier de stage → Mise à jour du statut de l'étudiant → Candidature acceptée
    REFUSER → Enregistrement du refus → Notification du refus → Candidature refusée
    DEMANDER_COMPLEMENT → Demande de complément → Décision de l'entreprise   (boucle R11)
7 jours sans décision → Relance de l'entreprise → Décision de l'entreprise
Sous-processus « Gestion des erreurs techniques » :
    Service indisponible (début d'erreur, code SERVICE_INDISPONIBLE) → Signaler l'interruption → Alerter le responsable → Traitement interrompu (fin)
```

## 4. Ajouter les connecteurs et la classe Groovy au projet

### 4.1 Connecteurs (Marketplace)

1. Dans la vue d'ensemble du projet, ouvrir **Extensions** (ou **Marketplace** / « Parcourir les extensions »).
2. Ajouter **Web service (SOAP)** (`bonita-connector-webservice`) et **REST** (`bonita-connector-rest`). Une connexion Internet est nécessaire.
3. S'ils n'apparaissent pas : passer au **mode secours** (pas de connecteur ; une opération par tâche). La classe Groovy fait alors les appels elle-même.

### 4.2 Classe Groovy `SoaProcessus`

Méthode sûre, indépendante des menus :

1. Fermer l'éditeur du diagramme.
2. Copier `docs\bonita\groovy\inpt\SoaProcessus.groovy` dans le dossier du projet Bonita :
   `C:\BonitaStudioCommunity-2025.2-u0\workspace\stages-soa\app\src-groovy\inpt\SoaProcessus.groovy`
   (créer le sous-dossier `inpt`).
3. Dans le Studio, explorateur de projet : clic droit sur le projet > **Actualiser** (F5). La classe apparaît sous « Scripts Groovy » (src-groovy).
4. Dans toute expression Groovy du processus, elle s'appelle `inpt.SoaProcessus.xxx(...)`.

Pour changer une adresse ou un mot de passe : modifier les constantes en tête de classe (`S1`… `S6`, `WS_MOT_DE_PASSE`, `CLE_API`).

## 5. Le bassin : contrat, variable, acteurs

Sélectionner le **bassin** `Candidature`.

1. **Général** : nom `Candidature`, version `1.0` (S4 cherche exactement ce nom et cette version).
2. **Exécution > Contrat**, onglet **Entrées**, ajouter :

   | Nom | Type | Description |
   |---|---|---|
   | `soumissionId` | LONG | Soumission créée par S4 |
   | `etudiantId` | LONG | |
   | `offreId` | LONG | |
   | `lettreMotivation` | TEXT | |

   Onglet **Contraintes** (facultatif) : `etudiantId > 0 && offreId > 0`.
   Le sujet demande `etudiantId`, `offreId`, `lettreMotivation` ; `soumissionId` est ajouté pour que le processus écrive dans la bonne soumission (journal des étapes, enregistrement ou rejet).

3. **Données > Variables de processus**, ajouter **une seule** variable :
   - Nom : `dossier`
   - Type : **Objet Java**, classe `java.util.Map`
   - Valeur par défaut : script Groovy
     ```groovy
     inpt.SoaProcessus.initialiser(soumissionId, etudiantId, offreId, lettreMotivation)
     ```
     (les entrées du contrat sont disponibles dans le script : cliquer sur l'icône d'expression, choisir « Script »).

4. **Acteurs** (onglet Général > Acteurs) : `Étudiant` (cocher **Initiateur**), `Système`, `Entreprise`. Associer chaque couloir à son acteur (sélectionner le couloir > Acteur).

## 6. Paramètres communs des connecteurs

### Connecteur SOAP (tâches marquées SOAP)

| Champ | Valeur |
|---|---|
| Espace de noms du service | `http://stages.inpt.ma/etudiants`, `.../entreprises` ou `.../offres` selon le service |
| Nom du service / du port | `EtudiantsService` / `EtudiantsPort` (resp. `EntreprisesService`/`EntreprisesPort`, `OffresService`/`OffresPort`) |
| Adresse (End point) | `http://localhost:8081/ws/etudiants`, `:8082/ws/entreprises`, `:8083/ws/offres` |
| Liaison (Binding) | `http://schemas.xmlsoap.org/wsdl/soap/http` (SOAP 1.1) |
| SOAPAction | vide |
| Enveloppe | expression **Script** : la méthode `enveloppeXxx` indiquée pour la tâche |
| Sortie | cocher la construction du document de réponse (**Build response document body**) |
| En cas d'échec | **Lever un événement d'erreur**, code `SERVICE_INDISPONIBLE` |

L'enveloppe produite contient l'en-tête WS-Security exigé par les services (exemple complet : `docs/SECURITE_SOAP.md`) :

```xml
<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
  <wsse:UsernameToken>
    <wsse:Username>bonita</wsse:Username>
    <wsse:Password>Bonita#Ws2026</wsse:Password>
  </wsse:UsernameToken>
</wsse:Security>
<cor:CorrelationId xmlns:cor="http://stages.inpt.ma/commun">soumission-14</cor:CorrelationId>
```

**Opération de sortie** de chaque connecteur SOAP (onglet Opérations de la tâche, ou sorties du connecteur) :
`dossier` **prend la valeur** de l'expression Script `inpt.SoaProcessus.apresXxx(dossier, responseDocumentBody)`.
(`responseDocumentBody` est la sortie du connecteur ; la méthode accepte aussi `response` ou un texte.)

### Connecteur REST

| Champ | Valeur |
|---|---|
| URL | expression Script : la méthode `urlXxx` indiquée |
| Méthode | celle de la tâche (GET, POST) |
| Content-Type | `application/json` ; charset `UTF-8` |
| En-têtes | `X-API-Key` = `cle-bonita-2026-Xp4MzN` ; `X-Acteur` = `processus-bonita` ; `X-Correlation-Id` = Script `inpt.SoaProcessus.correlation(dossier)` |
| Corps | expression Script : la méthode `corpsXxx` indiquée |
| Sortie | `bodyAsString` |
| En cas d'échec | **Lever un événement d'erreur**, code `SERVICE_INDISPONIBLE` |

**Opération de sortie** : `dossier` prend la valeur de `inpt.SoaProcessus.apresXxx(dossier, bodyAsString)`.

Les deux tâches qui changent un statut dans S4 utilisent **PATCH**. Si votre connecteur REST ne propose pas PATCH : méthode **POST** et en-tête supplémentaire `X-HTTP-Method-Override` = `PATCH` (S4 l'accepte).

### Mode secours (aucun connecteur)

Pour chaque tâche de service : onglet **Opérations**, une seule opération, `dossier` prend la valeur du script indiqué dans la colonne « Secours ».
Limite : sans connecteur, une panne ne déclenche pas l'événement d'erreur BPMN ; la classe signale elle-même l'incident (S4 + responsable) et la tâche passe à l'état « Échec » dans le portail Bonita.

## 7. Tâches de service : configuration de chacune

`P` = `inpt.SoaProcessus`. Toutes les opérations affectent la variable `dossier`.

| Tâche | Connecteur(s), dans l'ordre | Opération après chaque connecteur | Secours (une opération) |
|---|---|---|---|
| Vérification de l'étudiant | SOAP S1, enveloppe `P.enveloppeVerifierEtudiant(dossier)` | `P.apresVerifierEtudiant(dossier, responseDocumentBody)` | `P.verifierEtudiant(dossier)` |
| Vérification de l'offre | SOAP S3, `P.enveloppeDisponibilite(dossier)` | `P.apresDisponibilite(dossier, responseDocumentBody)` | `P.verifierOffre(dossier)` |
| Vérification de l'entreprise et du profil | 1) SOAP S3 `P.enveloppeConsulterOffre(dossier)`<br>2) SOAP S2 `P.enveloppeVerifierEntreprise(dossier)`<br>3) SOAP S3 `P.enveloppeCompatibilite(dossier)` | 1) `P.apresConsulterOffre(...)`<br>2) `P.apresVerifierEntreprise(...)`<br>3) `P.apresCompatibilite(...)` | `P.verifierEntrepriseEtProfil(dossier)` |
| Contrôle de la candidature | 1) REST GET `P.urlDoublon(dossier)`<br>2) REST GET `P.urlActives(dossier)` | 1) `P.apresDoublon(dossier, bodyAsString)`<br>2) `P.apresActives(dossier, bodyAsString)` | `P.controlerCandidature(dossier)` |
| Enregistrement de la candidature | REST POST `P.urlCandidatures()`, corps `P.corpsEnregistrement(dossier)` | `P.apresEnregistrement(dossier, bodyAsString)` | `P.enregistrer(dossier)` |
| Confirmation du dépôt | REST POST `P.urlNotifications()`, corps `P.corpsConfirmation(dossier)` | `P.apresNotification(dossier, 'NOTIFICATION_DEPOT', bodyAsString)` | `P.confirmerDepot(dossier)` |
| Transmission à l'entreprise | REST POST `P.urlNotifications()`, corps `P.corpsTransmission(dossier)` | `P.apresNotification(dossier, 'TRANSMISSION_ENTREPRISE', bodyAsString)` | `P.transmettre(dossier)` |
| Enregistrement du rejet | REST POST `P.urlRejet(dossier)`, corps `P.corpsRejet(dossier)` | `P.apresRejet(dossier, bodyAsString)` | `P.enregistrerRejet(dossier)` |
| Notification du rejet | REST POST `P.urlNotifications()`, corps `P.corpsNotificationRejet(dossier)` | `P.apresNotification(dossier, 'NOTIFICATION_REJET', bodyAsString)` | `P.notifierRejet(dossier)` |
| Relance de l'entreprise | REST POST `P.urlNotifications()`, corps `P.corpsRelance(dossier)` | `P.apresNotification(dossier, 'TRANSMISSION_ENTREPRISE', bodyAsString)` | `P.relancer(dossier)` |
| Demande de complément | REST POST `P.urlNotifications()`, corps `P.corpsComplement(dossier)` | `P.apresNotification(dossier, 'NOTIFICATION_RESULTAT', bodyAsString)` | `P.demanderComplement(dossier)` |
| Réservation de la place | SOAP S3, `P.enveloppeRetirerPlace(dossier)` | `P.apresRetirerPlace(dossier, responseDocumentBody)` | `P.retirerPlace(dossier)` |
| Enregistrement de l'acceptation | REST **PATCH** `P.urlStatut(dossier)`, corps `P.corpsStatut(dossier, 'ACCEPTEE')` | aucune | `P.enregistrerStatut(dossier, 'ACCEPTEE')` |
| Notification du résultat | REST POST `P.urlNotifications()`, corps `P.corpsAcceptation(dossier)` | `P.apresNotification(dossier, 'NOTIFICATION_RESULTAT', bodyAsString)` | `P.notifierAcceptation(dossier)` |
| Création du dossier de stage | REST POST `P.urlStages()`, corps `P.corpsStage(dossier)` | `P.apresStage(dossier, bodyAsString)` | `P.creerStage(dossier)` |
| Mise à jour du statut de l'étudiant | SOAP S1, `P.enveloppePasserEnStage(dossier)` | `P.apresPasserEnStage(dossier, responseDocumentBody)` | `P.passerEnStage(dossier)` |
| Enregistrement du refus | REST **PATCH** `P.urlStatut(dossier)`, corps `P.corpsStatut(dossier, 'REFUSEE')` | aucune | `P.enregistrerStatut(dossier, 'REFUSEE')` |
| Notification du refus | REST POST `P.urlNotifications()`, corps `P.corpsRefus(dossier)` | `P.apresNotification(dossier, 'NOTIFICATION_RESULTAT', bodyAsString)` | `P.notifierRefus(dossier)` |

Chaque méthode `apresXxx` écrit aussi l'étape dans le journal de S4 (`POST /soumissions/{id}/etapes`) : la vue BPMN du portail suit ainsi le cas Bonita en direct.

## 8. Passerelles : conditions

Sur chaque flux sortant, onglet **Général > Condition**, expression Script :

| Passerelle | Flux | Condition |
|---|---|---|
| Étudiant éligible ? | oui | `dossier.etudiantEligible == true` |
| | non | **Flux par défaut** (cocher « défaut ») |
| Offre existante et active ? | oui | `inpt.SoaProcessus.offreValable(dossier)` |
| | non | flux par défaut |
| Conditions satisfaites ? | oui | `inpt.SoaProcessus.conditionsSatisfaites(dossier)` |
| | non | flux par défaut |
| Décision ? | ACCEPTER | `inpt.SoaProcessus.decisionEst(dossier, 'ACCEPTER')` |
| | REFUSER | `inpt.SoaProcessus.decisionEst(dossier, 'REFUSER')` |
| | DEMANDER_COMPLEMENT | `inpt.SoaProcessus.decisionEst(dossier, 'DEMANDER_COMPLEMENT')` |

## 9. Tâche humaine « Décision de l'entreprise »

1. Type **Tâche humaine**, couloir `Entreprise`. Le **nom** doit être exactement `Décision de l'entreprise` : S4 cherche la tâche par ce nom (paramètre `bonita.tache.decision`).
2. **Exécution > Contrat**, entrées :

   | Nom | Type |
   |---|---|
   | `decision` | TEXT |
   | `commentaire` | TEXT |

   Contrainte : `['ACCEPTER', 'REFUSER', 'DEMANDER_COMPLEMENT'].contains(decision)`, message « Décision inconnue ».
3. **Opérations** : `dossier` prend la valeur de `inpt.SoaProcessus.apresDecision(dossier, decision, commentaire)`.
4. **Formulaire** : onglet Exécution > Formulaire, bouton **Créer un formulaire à partir du contrat** (UI Designer). Un champ texte pour `commentaire`, une liste pour `decision` (valeurs ACCEPTER, REFUSER, DEMANDER_COMPLEMENT). Ce formulaire sert si l'on décide depuis le portail Bonita ; le portail du projet passe, lui, par S4.
5. **Minuteur en bordure** : glisser un événement **minuteur** sur le bord de la tâche ; type **Durée**, 7 jours (pour la démonstration : 2 minutes). Il interrompt la tâche ; son flux va vers « Relance de l'entreprise », qui revient à « Décision de l'entreprise ».

## 10. Gestion des erreurs

1. Pour **chaque connecteur** des tâches de service : « En cas d'échec » = **Lever un événement d'erreur**, code `SERVICE_INDISPONIBLE`.
2. Dessiner le **sous-processus événementiel** `Gestion des erreurs techniques` (palette : sous-processus d'événement), dans le bassin.
3. À l'intérieur : un **événement de début d'erreur** `Service indisponible`, code `SERVICE_INDISPONIBLE` ; puis les tâches :
   - `Signaler l'interruption` : REST POST `P.urlErreur(dossier)`, corps `P.corpsErreur(dossier)` (S4 passe la soumission en ERREUR) ;
   - `Alerter le responsable` : REST POST `P.urlNotifications()`, corps `P.corpsAlerte(dossier)` (courriel ALERTE_RESPONSABLE) ;
   - événement de **fin** `Traitement interrompu`.
   Dans ce sous-processus, « En cas d'échec » des connecteurs = **Ignorer** (on ne boucle pas sur une erreur pendant le traitement d'une erreur).
   Secours : une seule tâche avec l'opération `inpt.SoaProcessus.signalerErreur(dossier)`.

## 11. Configurer et déployer

1. Bouton **Configurer** (barre d'outils) > **Correspondance des acteurs** : pour la démonstration, associer **les trois acteurs** à l'utilisateur **walter.bates** (organisation ACME par défaut). S4 se connecte avec ce compte pour démarrer les cas et exécuter la tâche de décision.
2. Bouton **Exécuter** (ou Déployer). Le portail Bonita s'ouvre : le processus `Candidature 1.0` est **activé**.
3. En cas d'erreur de configuration (connecteur incomplet, condition vide), le Studio liste les problèmes : les corriger, puis relancer.

## 12. Brancher S4 sur Bonita

Les paramètres sont dans `rest-candidatures/src/main/resources/application.properties` ; chacun se surcharge par variable d'environnement :

| Paramètre | Variable d'environnement | Valeur par défaut |
|---|---|---|
| `orchestration.mode` | `ORCHESTRATION_MODE` | `bonita` |
| `bonita.url` | `BONITA_URL` | `http://localhost:8080/bonita` |
| `bonita.utilisateur` | `BONITA_UTILISATEUR` | `walter.bates` |
| `bonita.motdepasse` | `BONITA_MOTDEPASSE` | `bpm` |
| `bonita.processus.nom` | `BONITA_PROCESSUS_NOM` | `Candidature` |
| `bonita.processus.version` | `BONITA_PROCESSUS_VERSION` | `1.0` |
| `bonita.tache.decision` | `BONITA_TACHE_DECISION` | `Décision de l'entreprise` |

Puis : `.\scripts\start-all.ps1 -Orchestration bonita`. La fin de l'affichage doit indiquer « Orchestration : bonita » **sans** avertissement « Bonita ne répond pas ».

Ce que fait S4 en mode bonita :
- **Dépôt** : crée la soumission (étape « Réception »), se connecte à Bonita (`/loginservice`, jeton `X-Bonita-API-Token`), cherche le processus `Candidature 1.0`, démarre un cas avec le contrat `{soumissionId, etudiantId, offreId, lettreMotivation}`. Réponse 202 `EN_TRAITEMENT` ; le statut avance au fil du processus.
- **Décision** (bouton de l'espace entreprise) : cherche la tâche « Décision de l'entreprise » prête pour ce cas, l'assigne au compte technique et l'exécute avec `{decision, commentaire}`.
- **Annulation** (R8) : annule dans S4, puis supprime le cas Bonita.
- **Candidatures de démonstration** créées avant le processus (sans cas Bonita) : leurs décisions passent par le mode direct.

## 13. Vérifier

Suivre `CHECKLIST_BONITA.md`. Une grande partie est automatisée :

```powershell
.\scripts\start-all.ps1 -Orchestration bonita
.\scripts\test-bout-en-bout.ps1 -Orchestration bonita
```

C'est le même test que dans les autres modes (parcours nominal, R1 à R7, R8, R11, responsable, panne de S3), mais face au vrai Bonita.

## 14. Exporter le processus

Une fois tout validé : **Fichier > Exporter** (ou clic droit sur le diagramme > Exporter), format **.bos**, enregistrer dans `bpmn\Candidature-1.0.bos` du dépôt. Faire aussi une capture du diagramme pour le rapport (voir `docs/CAPTURES_A_FAIRE.md`).

## 15. Dépannage

| Symptôme | Cause probable | Remède |
|---|---|---|
| Dépôt : 503 `BONITA_INDISPONIBLE`, « pas de réponse » | Studio fermé, ou moteur arrêté | Démarrer Bonita Studio ; attendre que le portail réponde sur 8080 |
| 503 `BONITA_INDISPONIBLE`, « connexion refusée » | Utilisateur ou mot de passe | Vérifier `BONITA_UTILISATEUR`/`BONITA_MOTDEPASSE` (walter.bates / bpm par défaut) |
| 503 `BONITA_PROCESSUS_ABSENT` | Processus non déployé, ou nom/version différents | Déployer ; vérifier « Candidature » et « 1.0 » |
| Démarrage d'un cas : HTTP 403 | walter.bates n'est pas initiateur | Correspondance des acteurs : Étudiant → walter.bates |
| Décision : 409 `DECISION_EN_COURS` | Le cas n'est pas sur la tâche de décision | Attendre la fin des étapes automatiques ; vérifier le cas dans le portail Bonita |
| Décision exécutée mais rien ne bouge | Tâche non assignable | Correspondance des acteurs : Entreprise → walter.bates |
| Cas en erreur, soumission ERREUR, courriel « Incident de traitement » | Un service ne répondait pas | `.\scripts\check-health.ps1`, puis redéposer |
| Faute SOAP `wsse:FailedAuthentication` dans les journaux | Mot de passe de service | `WS_MOT_DE_PASSE` de la classe : `Bonita#Ws2026` |
| REST 401 | Clé d'API | En-tête `X-API-Key` = `cle-bonita-2026-Xp4MzN` |
| Rien dans la vue BPMN du portail | Le journal n'est pas écrit | Vérifier que les opérations appellent bien les méthodes `apresXxx` |
