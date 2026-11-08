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
