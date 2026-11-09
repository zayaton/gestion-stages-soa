# Journal de bord

## Phase 1 – Socle

### Réalisé
- Projet Maven multi-module : `common`, 3 services SOAP, 3 services REST, `portal-gateway`.
- `docker-compose.yml` : MySQL 8.4 (port 3307), Mailpit (1025/8025), Adminer (8088).
- Schémas SQL et données de démonstration dans `db/init`, rejoués par `scripts/reset-db.ps1`.
- Scripts : `build`, `start-all`, `stop-all`, `reset-db`, `check-health`, `verifier-socle`.
- `GET /health` sur chaque service : 200 si le service et sa base répondent, 503 sinon.

### Décisions
- **Un compte MySQL par service**, avec des droits sur son seul schéma et sans droit de structure. La règle « une base par service » est garantie par MySQL : `svc_etudiants` ne peut pas lire `soa_offres`.
- **Table `soumission` distincte de `candidature`.** L'énoncé n'enregistre la candidature qu'après la passerelle de décision (étape 5). La soumission conserve donc la trace et le motif d'un rejet. `POST /soumissions` répondra 202, car le traitement est asynchrone.
- **R5 aussi garantie par la base** : index unique sur `(etudiant_id, offre_id, non_annulee)`, où `non_annulee` est une colonne générée qui vaut NULL pour une candidature annulée. Deux dépôts simultanés ne peuvent donc pas créer de doublon.
- **R8 et « Démarrer l'évaluation ».** Si l'étape 6 faisait passer la candidature en `EN_COURS_EVALUATION`, le statut `DEPOSEE` ne durerait que quelques millisecondes et l'annulation serait impossible en pratique. La candidature reste donc `DEPOSEE` jusqu'à ce que l'entreprise démarre l'évaluation. L'annulation doit aussi arrêter le cas Bonita.
- **Ajout de `etudiant_competence` (S1).** L'énoncé demande un profil étudiant sans compétences, alors que le score indicatif de R7 compare celles de l'étudiant à celles de l'offre.
- **Ajout de `stage_historique` (S6)**, pour afficher l'historique des opérations de chaque stage.
- **Type de notification `ALERTE_RESPONSABLE`**, pour la gestion d'erreurs du processus BPMN.
- **Dates de démonstration relatives à `CURDATE()`**, pour que les scénarios restent valables le jour de l'enregistrement.
- **Un compte par étudiant et par entreprise**, et pas seulement un par rôle : chaque scénario de rejet se joue avec un étudiant différent.
- **Jars + dossier `lib/`** plutôt qu'un jar unique : Metro et Jersey s'appuient sur des fichiers `META-INF` qu'un jar fusionné risquerait d'écraser.
- **Services à l'écoute sur 127.0.0.1** : pas de fenêtre de pare-feu Windows pendant la démonstration, et Bonita tourne sur la même machine.
- **Le mode `direct` reste le défaut jusqu'à la phase 5**, puis `bonita`.

### Points techniques à retenir
- **Les clients HTTP Java doivent imposer HTTP/1.1.** Par défaut, `java.net.http.HttpClient` tente un passage en h2c ; Grizzly répond alors sans ses en-têtes (`Content-Type` absent). Constaté et reproduit sur S6. À appliquer aux clients de la passerelle et de S4.
- **Scripts PowerShell en UTF-8 avec BOM** : sans BOM, Windows PowerShell 5.1 lit les accents de travers.
- **`Start-Process` ne protège pas ses arguments** : le chemin du jar porte ses propres guillemets, car le dossier du projet contient un espace.
- **`Start-Process -RedirectStandardOutput` fait hériter les flux de l'appelant** : un script qui lit la sortie de `start-all` ne rendait jamais la main. Les services sont lancés via `cmd /c` sans redirection PowerShell.
- **`-u$Compte` n'est pas développé par PowerShell** (il faut `"-u$Compte"`). Ce bug faisait passer à tort les tests de refus d'accès, qui échouaient pour une autre raison. Les tests négatifs exigent désormais le code d'erreur MySQL exact : à appliquer aussi aux tests des phases suivantes.
- **Contrôle de santé de MySQL en TCP** : le serveur temporaire de l'initialisation n'écoute que sur le socket, donc `up --wait` n'aboutit qu'une fois les données chargées.

### Limites connues
- Les candidatures en cours issues des données de démonstration n'ont pas de cas Bonita. En mode `bonita`, seules les candidatures déposées pendant la démonstration passent par une tâche humaine. À traiter en phases 4 et 5.
- Les mots de passe de démonstration figurent dans `soa-defaults.properties` et `docker-compose.yml` comme valeurs par défaut locales. Ils sont surchargeables par variable d'environnement. En production, ils viendraient d'un coffre à secrets.

## Phase 2 – Services SOAP S1, S2, S3

### Réalisé
- Contrats écrits d'abord : `etudiants.wsdl`, `entreprises.wsdl`, `offres.wsdl`, avec espaces de noms explicites, opérations documentées et fautes typées. Le WSDL publié est le fichier du dépôt.
- Module Maven `contracts`, ajouté à la liste initiale : il génère les classes depuis les WSDL. Une construction n'a donc besoin d'aucun service démarré.
- Fautes : `XIntrouvableFault`, `DonneesInvalidesFault`, `ConflitFault`, `ServiceIndisponibleFault`, `ErreurInterneFault`, chacune avec un code et un message en français.
- WS-Security UsernameToken par `SOAPHandler` sur chaque service. Comptes de service en configuration, mots de passe hachés en BCrypt.
- Corrélation : en-tête SOAP `CorrelationId` et en-tête HTTP `X-Correlation-Id`, repris dans chaque ligne de journal et transmis aux appels suivants.
- Tests : JUnit (règles et échanges SOAP sans base), tests `*IT` sur le vrai MySQL, projet SoapUI.

### Décisions
- **Règles de statut.** L'énoncé ne fixe que les statuts eux-mêmes. Transitions retenues pour les étudiants : `ACTIF` vers `SUSPENDU`, `EN_STAGE` ou `DIPLOME` ; `SUSPENDU` vers `ACTIF` ; `EN_STAGE` vers `ACTIF` ou `DIPLOME` ; `DIPLOME` est définitif. Pour les offres : `BROUILLON` vers `ACTIVE` ou `CLOTUREE` ; `ACTIVE` vers `POURVUE` ou `CLOTUREE` ; `POURVUE` vers `ACTIVE` ou `CLOTUREE` ; `CLOTUREE` est définitif. Reprendre le même statut est sans effet, pour qu'une étape du processus puisse être relancée.
- **Suppression.** Un étudiant en stage ne se supprime pas. Une entreprise avec des offres ne se supprime pas (S2 interroge S3) : elle passe en `INACTIVE`. Seule une offre en `BROUILLON` se supprime : une offre publiée se clôture, ce qui conserve l'historique.
- **`verifierX` répond sans faute pour un identifiant inconnu** (`existe=false`). Le processus BPMN peut ainsi traiter ce cas à sa passerelle de décision, comme les autres rejets.
- **Création d'une offre contrôlée auprès de S2** : une offre ne peut pas être créée pour une entreprise inconnue ou inactive (R2).
- **Énumérations anonymes dans les WSDL.** JAXB les lie à `String`. Avec une énumération nommée, il transformerait une valeur inconnue en `null` sans le signaler, et `<statut>INVENTE</statut>` passerait pour un statut absent.
- **Éléments de réponse distincts de ceux de la requête** (`etudiantCree`, `etudiantModifie`, …). Avec le même nom, wsimport génère des paramètres `Holder` difficiles à utiliser.
- **Mot de passe en clair (PasswordText) dans le jeton.** Avec des hachages BCrypt stockés, le serveur ne peut pas vérifier un PasswordDigest. Sans HTTPS en local, ce mot de passe circule en clair : TLS est indispensable en production (voir le rapport).

### Points techniques à retenir
- **JAX-WS parcourt la chaîne de gestionnaires à l'envers en entrée** : pour que la corrélation passe avant la sécurité, elle est placée après dans la liste.
- **Un `id` non numérique devient 0 en JAXB** : sans contrôle, le client recevait « étudiant 0 introuvable ». Un identifiant non positif donne maintenant `DonneesInvalidesFault`.
- **Dans SoapUI, les identifiants d'assertion `SOAP Fault Assertion` et `Not SOAP Fault Assertion` sont inversés** par rapport à leur nom.
- **Tests DAO sur MySQL : `ConnexionAnnulable`** garde chaque test dans une transaction annulée, y compris les `commit` des DAO. Seul le test de concurrence écrit réellement, puis nettoie.

### Incident : `tests-soapui.ps1` bloqué (08/11, résolu le 09/11)
- **Constat.** Lors de la vérification complète du 08/11, le script est resté bloqué une trentaine de minutes et a été arrêté à la main. Le journal d'exécution montre que la suite « Panne S3 arrêté » avait réussi (1 cas, 10 assertions, 0 échec). Le blocage se produisait juste après, au redémarrage des services qui précède « Panne S1 arrêté ».
- **Cause.** Ce redémarrage appelait `start-all.ps1` avec `Start-Process -Wait`. Sous Windows PowerShell 5.1, `-Wait` attend aussi tous les processus lancés par l'enfant. Ici, c'était le service S3 relancé, qui ne s'arrête jamais. Reproduit le 09/11 : un enfant qui se termine en 1 s mais lance une commande de 25 s fait attendre `-Wait` 25 s, contre 1 s avec `Process.WaitForExit`.
- **Correction.** `Invoke-ProcessusLimite` attend seulement le processus lancé, avec une durée maximale : `-DelaiSuiteSecondes` (300 s par défaut) pour chaque suite SoapUI, `-DelaiRedemarrageSecondes` (300 s) pour chaque redémarrage. Au-delà, le processus est arrêté, arborescence comprise pour SoapUI, et l'étape compte comme un échec. MySQL est relancé même si la suite « Panne base arrêtée » échoue.
- **Vérification du garde-fou.** Avec `-DelaiSuiteSecondes 3`, les trois suites sont arrêtées et signalées en échec, le script se termine (code 1, 11 s) et aucun processus SoapUI ne reste actif.
- **Vérification complète du 09/11** : 198 tests JUnit (dont 29 sur MySQL), 66/66 vérifications du socle, toutes les suites SoapUI réussies y compris les trois pannes réelles (98 s), et les 7 services en bonne santé à la fin.
- **À retenir pour la suite du projet** : ne jamais utiliser `Start-Process -Wait` sur un script qui démarre des services.

### Limites connues
- **XML mal formé** : le message d'erreur vient de Metro et reste en anglais, car l'erreur survient avant nos gestionnaires. Le service reste disponible et aucune trace de pile n'est renvoyée.
- **`ErreurInterneFault`** ne peut pas être provoquée de l'extérieur : elle est couverte par JUnit, pas par SoapUI.
- **Suppression d'un étudiant avec des candidatures** : S1 ne peut pas le vérifier seul. Le portail devra interroger S4 avant de la proposer (phase 4).
- **`decrementerPlaces` n'est pas idempotent** : relancer cette étape retire une seconde place. Le processus ne doit la relancer que si elle a échoué (phases 4 et 5).
- **`tests-soapui.ps1` laisse des lignes de test en base** (une offre `POURVUE`, créée pour tester R10, ne peut pas être supprimée). Il faut relancer `reset-db.ps1` ensuite.

## Phase 3 – Services REST S4, S5, S6

### Réalisé
- Socle REST commun (`common/rest`) : corps d'erreur uniforme `{code, message, details, timestamp, path}`, contrôle `X-API-Key` (401) et droits par opération avec `@ClientsAutorises` (403), corrélation `X-Correlation-Id`, journal d'accès, JSON strict (champ inconnu refusé), client REST avec délais d'attente.
- Contrat OpenAPI 3 écrit à la main pour chaque service (`openapi.yaml`, en français), servi à `/openapi.yaml`, avec Swagger UI hors ligne à `/docs/`. Un test compare les routes du code à celles du contrat : une route non documentée fait échouer la construction.
- **S6 Suivi** : stages, transitions de statut, comptes rendus, évaluations (note sur 20), historique des opérations.
- **S5 Notifications** : huit gabarits HTML en français, envoi SMTP réel vers Mailpit, statut `ENVOYEE` ou `ECHEC`, rappels de date limite quotidiens et déclenchables à la main.
- **S4 Candidatures** : soumissions, candidatures, historique, règles R5, R6, R8, R9, et l'orchestration en mode `direct` (étapes 1 à 9, R1 à R11), avec le journal des étapes pour la vue BPMN.
- Collection Postman générée (`tests/postman`), lancée par Newman via `scripts/tests-postman.ps1` : 87 requêtes, 226 assertions, un test négatif par règle.

### Décisions
- **L'orchestration `direct` est faite en phase 3 et non en phase 4.** Elle vit dans S4, et sans elle la collection Postman ne pouvait pas montrer les rejets R1 à R7. La phase 4 garde la passerelle et le script de bout en bout.
- **`POST /soumissions` répond 202 dans tous les cas où le processus a pu s'exécuter** : statut `ENREGISTREE` ou `REJETEE` (règle et motif). Un rejet est un résultat métier, pas une erreur HTTP. 503 si un service nécessaire est arrêté.
- **Ordre des contrôles** : R1 (étape 2), puis R2 et R7 (étape 3), puis R3, R4, R5, R6 (étape 4). Le premier échec arrête les contrôles et donne le motif.
- **R10 : la place est réservée avant d'enregistrer l'acceptation.** S'il n'en reste plus, la décision est refusée (409) et rien ne change. On évite ainsi d'accepter un étudiant sans place. Création du stage et passage `EN_STAGE` viennent ensuite ; s'ils échouent, l'étape est marquée `ECHEC`, le responsable est alerté et `POST /candidatures/{id}/suivi/reprendre` les rejoue sans effet de bord (S6 est idempotent, S1 accepte de reprendre le même statut).
- **R11** : la candidature reste `EN_COURS_EVALUATION` ; une ligne d'historique (statut inchangé) garde la trace du complément demandé.
- **Une notification qui échoue ne bloque jamais le processus** : S5 l'enregistre en `ECHEC` (réponse 201), S4 marque l'étape `ECHEC` et continue.
- **Rappels** : toutes les données (S3, S1, S2, S4) sont lues avant le premier envoi ; si l'une manque, rien n'est envoyé (503). Un rappel déjà envoyé n'est jamais renvoyé ; un rappel en échec l'est au passage suivant.
- **Variables de gabarit inconnues refusées (422)** : une faute de frappe dans un appelant ne produit pas un courriel incomplet.
- **Clés d'API stockées en SHA-256, pas en BCrypt** : ce sont des clés longues et aléatoires, vérifiées à chaque requête (BCrypt coûterait environ 70 ms par appel).
- **Droits par client** : seuls `portail` et `postman` déposent une candidature ; seuls le processus (`bonita`) et les services l'enregistrent ou changent son statut brut ; le portail ne peut pas faire envoyer de courriel arbitraire.
- **Table `etape_processus`** : chaque étape exécutée, pour la vue BPMN en direct. En mode `bonita`, le processus l'alimentera par `POST /soumissions/{id}/etapes`. Les candidatures de démonstration ont leurs soumissions et étapes.
- **Données de démonstration** : chaque scénario a désormais son propre étudiant. Le parcours nominal fait passer Salma `EN_STAGE` ; les scénarios R2 à R4 utilisent Aïcha Tahiri (voir l'incident ci-dessous).

### Points techniques à retenir
- **Ressources Jersey par requête** : une ressource enregistrée comme instance unique ne peut pas recevoir le contexte de la requête (`Not inside a request scope`). Les ressources sont enregistrées par classe ; le service partagé est injecté par un `AbstractBinder`.
- **Un service démarré verrouille ses jars** : arrêter les services (`stop-all.ps1 -ServicesSeuls`) avant de reconstruire, sinon Maven ne peut pas copier les dépendances dans `target/lib`.
- **Paramètre de chemin non numérique** (`/stages/abc`) : JAX-RS répond 404. Le code d'erreur suit désormais le statut (`RESSOURCE_INTROUVABLE`).
- **Jackson et les records** : un champ obligatoire absent est signalé avant un champ inconnu ; le message dit « absente ou invalide ».

### Incident : scénarios de démonstration qui se gênaient
La première exécution de la collection Postman a échoué sur R2, R3 et R4 : Salma, acceptée dans le parcours nominal, était devenue `EN_STAGE`, et R1 la rejetait avant les règles visées. Le même défaut existait dans `docs/SCENARIOS_DEMO.md` : enregistrer le parcours nominal en premier dans la vidéo aurait cassé ces trois démonstrations. Corrigé en donnant ces scénarios à Aïcha Tahiri, avec une précondition vérifiée par `verifier-socle.ps1` et une consigne d'ordre dans le document.

### Limites connues
- **Mode `bonita` non branché** : `ORCHESTRATION_MODE=bonita` arrête S4 au démarrage avec un message clair. Le pont vers l'API Bonita arrive en phase 5.
- **R10, cas extrême** : si la base de S4 tombe entre la réservation de la place (S3) et l'enregistrement de l'acceptation, la place est perdue. Il faudrait une compensation (rendre la place) : documenté, non traité.
- **Quand une offre devient `POURVUE`**, les autres candidatures en cours sur cette offre restent en évaluation ; l'entreprise les refuse elle-même.
- **Propriété des données** : S4 ne vérifie pas que l'entreprise qui décide est celle de l'offre ; c'est le rôle de la passerelle (phase 4), qui connaît l'utilisateur connecté.
- **Postman** : la collection se joue sur des données fraîches (le script réinitialise les bases).

## Phase 4 – Portail et orchestration de bout en bout

### Réalisé
- **Portail** (port 8090) : connexion BCrypt, sessions, trois espaces (`/api/etudiant`, `/api/entreprise`, `/api/responsable`), masquage des coordonnées, composition des données de plusieurs services, état du système, service des fichiers de l'interface.
- Clients SOAP générés depuis les WSDL du dépôt (module `contracts`, `jaxws-maven-plugin`) ; clients REST avec la clé `portail` et l'en-tête `X-Acteur` (l'auteur de chaque opération arrive dans les historiques des services).
- **Test de bout en bout** (`scripts/test-bout-en-bout.ps1`, script Node `tests/e2e/bout-en-bout.mjs`) : tout passe par le portail avec de vraies connexions ; vérifie les courriels dans Mailpit et la corrélation dans les journaux, puis arrête S3 pour de vrai.

### Décisions
- **Jeton de session aléatoire (256 bits)**, envoyé dans `Authorization: Bearer`. Seule son empreinte SHA-256 est en base. Un en-tête plutôt qu'un cookie : pas d'attaque CSRF possible ; en contrepartie, l'interface doit éviter toute faille XSS (pas de `innerHTML` avec des données).
- **Même message pour tous les refus de connexion** (mauvais mot de passe, compte inconnu, compte désactivé), et vérification BCrypt même pour un compte inconnu : ni le message ni le temps de réponse ne révèlent quels identifiants existent.
- **L'identité vient toujours de la session**, jamais du navigateur : un étudiant ne peut candidater que pour lui-même, une entreprise ne publie que pour elle.
- **Le dossier d'un autre donne 404, pas 403** : on ne révèle pas son existence.
- **Masquage côté serveur** : le JSON envoyé à l'entreprise ne contient jamais les coordonnées complètes (vérifié par le test de bout en bout).
- **Les erreurs des services sont transmises telles quelles** (code, message, détails) : l'interface affiche par exemple le message exact de la règle R8.
- **Suppression d'un étudiant** : le portail interroge S4 et refuse (409) si l'étudiant a des candidatures (limite relevée en phase 2).
- **Test de bout en bout en Node plutôt qu'en PowerShell** : Windows PowerShell 5.1 décode mal le JSON UTF-8 ; Node est déjà installé pour Newman.

### Incident : la cause d'une panne se perdait entre deux services
Avec S3 arrêté, S4 répondait correctement 503 `SERVICE_OFFRES_INDISPONIBLE` (« le responsable a été prévenu »), mais le client REST commun remplaçait toute réponse 5xx par « le service candidatures est indisponible ». L'étudiant aurait lu la mauvaise cause. Désormais, un 5xx accompagné de notre corps d'erreur est transmis tel quel ; seul un service muet ou sans réponse structurée donne l'erreur générique. Trouvé par le test de bout en bout.

### Points techniques à retenir
- **Une exception levée dans un filtre Jersey passe par le `MappeurErreurs`** : 401 et 403 du portail ont le même corps JSON que les autres erreurs.
- **Chemin encodé remontant au-dessus de la racine** (`/..%2F...`) : Grizzly le refuse lui-même avec un 500 sans corps, avant notre code. Rien ne fuit (vérifié par test) ; seul le code HTTP est inhabituel.
- **Les routes `/api/...` passent avant la route générique des fichiers** : JAX-RS préfère le chemin le plus précis.

### Limites connues
- **Pas de limitation des tentatives de connexion** : en production, il faudrait bloquer temporairement un compte après plusieurs échecs.
- **Un étudiant ou une entreprise créé par le responsable n'a pas de compte portail.** Il faudrait créer le compte et envoyer un lien d'activation.
- **Liste des offres : un appel de compatibilité par offre** (S3, qui appelle S1). Acceptable pour une quinzaine d'offres ; au-delà, il faudrait une opération groupée.
- **L'API du portail n'a pas de contrat OpenAPI** : c'est une passerelle propre à l'interface, pas un service exposé à d'autres clients.

