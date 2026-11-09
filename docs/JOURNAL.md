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
