# Gestion Intelligente des Stages et des Offres de Recrutement

Projet du module SOA, filière AMOA, INPT, année universitaire 2026-2027.
Hiba Elamdaoui, Aya Zahri, Hibatollah Ait Ahmed.

Plateforme de gestion des offres de stage et des candidatures, en architecture orientée services :

- **trois services SOAP** (JAX-WS, contrats WSDL) : étudiants (S1), entreprises (S2), offres de stage (S3) ;
- **trois services REST** (JAX-RS, contrats OpenAPI) : candidatures (S4), notifications par courriel (S5), suivi des stages (S6) ;
- un **processus BPMN** exécuté par Bonita, qui orchestre le traitement d'une candidature du dépôt à la création du stage ;
- un **portail web** pour les étudiants, les entreprises et le responsable des stages, avec l'avancement de chaque candidature sur le diagramme BPMN.

Chaque service est un processus Java indépendant avec son propre schéma MySQL. Onze règles de gestion (R1 à R11) encadrent le traitement.
Le rapport technique est dans [rapport/rapport.pdf](rapport/rapport.pdf).

## Prérequis

- JDK 17, Maven 3.9, Docker Desktop (Windows, avec WSL 2).
- Bonita Studio Community 2025.2 pour l'orchestration par le processus BPMN (sinon, mode `direct`).
- SoapUI Open Source pour les tests SOAP, Node.js LTS pour Newman, le test de bout en bout et Playwright (installés automatiquement par les scripts), Microsoft Edge pour le parcours de l'interface.
- Pour recompiler le rapport : MiKTeX ou TeX Live avec XeLaTeX (ou Overleaf, compilateur XeLaTeX).

## Démarrage rapide

```powershell
git clone <adresse du dépôt>
cd <dossier du dépôt>
.\scripts\start-all.ps1 -Rebuild -Orchestration direct   # construit, démarre Docker et les sept services
```

Puis ouvrir http://localhost:8090 et se connecter avec un compte de démonstration (plus bas). Pour le mode `bonita` (par défaut),
déployer d'abord le processus en suivant [docs/bonita/GUIDE_BONITA.md](docs/bonita/GUIDE_BONITA.md).

## Commandes

Depuis PowerShell, à la racine du projet :

```powershell
.\scripts\start-all.ps1          # construit si besoin, démarre Docker et les services
.\scripts\check-health.ps1       # état de chaque service
.\scripts\stop-all.ps1           # arrête tout, les données sont conservées
.\scripts\reset-db.ps1           # remet les bases et Mailpit à l'état initial
.\scripts\verifier-socle.ps1     # vérifie le socle et les données de démonstration
.\scripts\build.ps1              # compile et lance les tests unitaires
.\scripts\arreter-service.ps1 S3 # arrête un seul service (démonstration d'une panne) ; start-all le relance
```

Si PowerShell refuse d'exécuter les scripts :
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, ou `powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1`.

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `common` | Socle commun : configuration, base de données, sécurité SOAP et REST, erreurs, corrélation |
| `contracts` | WSDL des trois services SOAP (ils font foi) et classes générées par wsimport |
| `soap-etudiants`, `soap-entreprises`, `soap-offres` | Services S1, S2, S3 |
| `rest-candidatures`, `rest-notifications`, `rest-suivi` | Services S4, S5, S6 (contrat dans `src/main/resources/openapi.yaml`) |
| `portal-gateway` | Portail : API des trois espaces et interface web (`src/main/resources/public`) |
| `bpmn` | Processus BPMN « Candidature » (fichier à importer dans Bonita) |
| `db/init` | Schémas, comptes et données de démonstration |
| `scripts` | Scripts PowerShell : démarrage, arrêt, réinitialisation, tests |
| `tests` | SoapUI, Postman, test de bout en bout, simulation Bonita, parcours de l'interface |
| `docs` | Guides (Bonita, sécurité SOAP, scénarios, vidéo, captures) et journal du projet |
| `rapport` | Rapport LaTeX, ses générateurs (`outils`) et le PDF |

## Ports

| Composant | Port |
|---|---|
| Bonita Studio | 8080 |
| S1 SOAP Étudiants | 8081 |
| S2 SOAP Entreprises | 8082 |
| S3 SOAP Offres | 8083 |
| S4 REST Candidatures | 8084 |
| S5 REST Notifications | 8085 |
| S6 REST Suivi des stages | 8086 |
| Adminer | 8088 |
| Portail | 8090 |
| MySQL | 3307 |
| Mailpit | 1025 (SMTP), 8025 (boîte mail) |

Chaque service expose `GET /health`.

## Services SOAP

| Service | Adresse | WSDL |
|---|---|---|
| S1 Étudiants | `http://localhost:8081/ws/etudiants` | `http://localhost:8081/ws/etudiants?wsdl` |
| S2 Entreprises | `http://localhost:8082/ws/entreprises` | `http://localhost:8082/ws/entreprises?wsdl` |
| S3 Offres | `http://localhost:8083/ws/offres` | `http://localhost:8083/ws/offres?wsdl` |

Les contrats font foi dans [contracts/src/main/resources/wsdl](contracts/src/main/resources/wsdl).
Chaque appel exige un en-tête WS-Security : voir [docs/SECURITE_SOAP.md](docs/SECURITE_SOAP.md).

## Services REST

| Service | Base | Contrat OpenAPI | Swagger UI |
|---|---|---|---|
| S4 Candidatures | `http://localhost:8084/api/v1` | `http://localhost:8084/openapi.yaml` | http://localhost:8084/docs/ |
| S5 Notifications | `http://localhost:8085/api/v1` | `http://localhost:8085/openapi.yaml` | http://localhost:8085/docs/ |
| S6 Suivi des stages | `http://localhost:8086/api/v1` | `http://localhost:8086/openapi.yaml` | http://localhost:8086/docs/ |

Chaque appel porte l'en-tête `X-API-Key` (une clé par client, 401 sans clé valide). Clés de démonstration :

| Client | Clé |
|---|---|
| Portail | `cle-portail-2026-7Hq2Vd` |
| Processus Bonita | `cle-bonita-2026-Xp4MzN` |
| Appels entre services | `cle-services-2026-Lk9TwQ` |
| Postman | `cle-postman-2026-Rb3WsE` |

Erreurs : corps JSON uniforme `{code, message, details, timestamp, path}`. Swagger UI fonctionne sans Internet : bouton « Authorize » pour saisir la clé.

**Mode d'orchestration** (variable `ORCHESTRATION_MODE`) :
- `bonita` (par défaut) : le processus BPMN déployé dans Bonita Studio orchestre. Construction : [docs/bonita/GUIDE_BONITA.md](docs/bonita/GUIDE_BONITA.md).
- `direct` : S4 exécute les mêmes étapes en Java ; secours pour le développement et les tests automatiques.

Choix au démarrage : `.\scripts\start-all.ps1 -Orchestration direct` (ou `bonita`). Si Bonita ne répond pas, `start-all` l'indique.

## Portail (port 8090)

Interface : **http://localhost:8090** (connexion avec un compte de démonstration, voir plus bas). Trois espaces :

- **Étudiant** : offres ouvertes avec compatibilité du profil, candidature, suivi du dossier avec le diagramme BPMN
  du processus (étapes passées en couleur), annulation tant que l'évaluation n'a pas commencé, courriels reçus, stage.
- **Entreprise** : candidatures reçues (coordonnées masquées), démarrage de l'évaluation puis décision
  (accepter, refuser, demander un complément), gestion des offres et du profil.
- **Responsable des stages** : tableau de bord, toutes les candidatures, stages (statut, comptes rendus, évaluations),
  référentiels étudiants et entreprises, courriels envoyés, rappels de date limite, état des services.

L'interface est écrite en HTML, CSS et JavaScript sans framework (`portal-gateway/src/main/resources/public`) ;
le diagramme est affiché par bpmn-js à partir du même fichier `.bpmn` que celui importé dans Bonita.

Le portail expose l'API des trois espaces sous `/api`. Il appelle les six services :
clients SOAP générés par wsimport depuis les WSDL du dépôt, clients REST avec la clé `portail`.

- Connexion : `POST /api/auth/connexion` avec `{"identifiant": "...", "motDePasse": "..."}`. La réponse contient un jeton,
  à envoyer ensuite dans `Authorization: Bearer <jeton>`. Session de 8 heures ; `POST /api/auth/deconnexion` la ferme.
- Espaces : `/api/etudiant/...`, `/api/entreprise/...`, `/api/responsable/...`. Rôle vérifié par le serveur (403 sinon).
- Une entreprise voit les coordonnées des étudiants masquées (`06******12`, `s***@inpt.ac.ma`).

## Tests

```powershell
.\scripts\build.ps1                # tests unitaires et échanges SOAP en mémoire, sans base de données
.\scripts\build.ps1 clean verify   # ajoute les tests sur le vrai MySQL (données fraîches : reset-db avant)

.\scripts\reset-db.ps1
.\scripts\start-all.ps1 -Rebuild
.\scripts\verifier-socle.ps1       # socle et données de démonstration
.\scripts\tests-soapui.ps1         # tests SoapUI, y compris de vraies pannes (S3, S1, MySQL)
.\scripts\tests-postman.ps1        # collection Postman (Newman) : réinitialise les bases puis joue tous les scénarios
.\scripts\test-bout-en-bout.ps1    # par le portail : tous les scénarios, courriels Mailpit, puis une vraie panne de S3
.\scripts\test-bout-en-bout.ps1 -Orchestration simulation   # mêmes scénarios, S4 en mode bonita face au processus simulé
.\scripts\test-bout-en-bout.ps1 -Orchestration bonita       # mêmes scénarios face au vrai Bonita (processus déployé)
.\scripts\captures-ui.ps1         # interface dans Microsoft Edge (Playwright) : cycle complet, erreurs de console, captures
```

`tests-soapui.ps1`, `tests-postman.ps1` et `test-bout-en-bout.ps1` modifient les données : relancez `reset-db.ps1` avant une démonstration ou `verifier-socle.ps1`.
La collection à importer dans Postman est `tests\postman\Stages-SOA.postman_collection.json`, avec l'environnement `Stages-SOA-local.postman_environment.json`. Rapport HTML de la dernière exécution : `tests\postman\rapports\rapport-newman.html`.
Le projet SoapUI à ouvrir dans l'interface graphique est `tests\soapui\Stages-SOA-soapui-project.xml`.
`captures-ui.ps1` réinitialise les bases, joue une candidature jusqu'à l'acceptation par l'interface, visite chaque écran
des trois espaces et échoue à la moindre erreur dans la console du navigateur. Les captures vont dans `rapport\figures\ui-*.png`.
Il utilise Edge, déjà installé sous Windows ; `-Chromium` télécharge et utilise Chromium à la place.

## Comptes de démonstration

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Responsable | `responsable@inpt.ac.ma` | `Responsable@2026` |
| Étudiant | `salma.elidrissi@inpt.ac.ma` | `Etudiant@2026` |
| Entreprise | `contact@novapay-maghreb.ma` | `Entreprise@2026` |

Tous les étudiants et toutes les entreprises ont un compte : voir [docs/SCENARIOS_DEMO.md](docs/SCENARIOS_DEMO.md).
Toutes les données sont fictives.

## Documentation

| Document | Contenu |
|---|---|
| [rapport/rapport.pdf](rapport/rapport.pdf) | Rapport technique (compilation : `.\rapport\compiler.ps1`, XeLaTeX) |
| [docs/CAPTURES_A_FAIRE.md](docs/CAPTURES_A_FAIRE.md) | Captures d'écran à faire à la main pour le rapport (Bonita, SoapUI, Postman) |
| [docs/SCENARIO_VIDEO.md](docs/SCENARIO_VIDEO.md) | Script minute par minute de la vidéo de démonstration et liste de contrôle |
| [docs/SCENARIOS_DEMO.md](docs/SCENARIOS_DEMO.md) | Un compte et une offre par scénario de démonstration |
| [docs/bonita/GUIDE_BONITA.md](docs/bonita/GUIDE_BONITA.md) | Construction du processus dans Bonita Studio, pas à pas |
| [docs/SECURITE_SOAP.md](docs/SECURITE_SOAP.md) | En-tête WS-Security à envoyer aux services SOAP |
| [docs/JOURNAL.md](docs/JOURNAL.md) | Journal du projet : décisions, incidents, limites |
