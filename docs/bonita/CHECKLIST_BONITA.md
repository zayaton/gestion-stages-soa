# Liste de contrôle : processus Bonita en service

À dérouler après le guide (`GUIDE_BONITA.md`), dans l'ordre. Cocher chaque ligne ; en cas d'écart, voir la section « Dépannage » du guide.

## A. Préparation

- [ ] `.\scripts\reset-db.ps1` : « Bases réinitialisées ».
- [ ] Bonita Studio ouvert, processus **Candidature 1.0** déployé et activé (portail Bonita > Administrateur > Processus).
- [ ] `.\scripts\start-all.ps1 -Orchestration bonita` : « Orchestration : bonita », **sans** avertissement « Bonita ne répond pas ».
- [ ] Mailpit ouvert : http://localhost:8025 (boîte vide).

## B. Test automatique

- [ ] `.\scripts\test-bout-en-bout.ps1 -Orchestration bonita` : « Résultat (scenarios, orchestration bonita) : 59 vérification(s) réussie(s), 0 échec(s) » puis « Résultat (panne…) : 5 … 0 échec(s) ».

Si ce test passe, les sections C à F sont acquises ; les dérouler à la main sert à la vidéo et aux captures.

## C. Parcours nominal, à la main (étapes 1 à 9)

Après `.\scripts\reset-db.ps1`, en appelant le portail (interface de la phase 6, ou Postman) :

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Salma (`salma.elidrissi@inpt.ac.ma` / `Etudiant@2026`) candidate à l'offre 1 | Réponse 202, statut `EN_TRAITEMENT` |
| 2 | Portail Bonita > cas en cours | Un cas `Candidature`, sur la tâche « Décision de l'entreprise » après quelques secondes |
| 3 | Mes candidatures (Salma) | Candidature `DEPOSEE`, offre « Stage AMOA – Paiement mobile » |
| 4 | Mailpit | « Candidature enregistrée : … » (Salma) et « Nouvelle candidature : … » (NovaPay) |
| 5 | NovaPay (`contact@novapay-maghreb.ma` / `Entreprise@2026`) démarre l'évaluation | Statut `EN_COURS_EVALUATION` |
| 6 | NovaPay accepte | Le cas Bonita passe la tâche ; quelques secondes plus tard : candidature `ACCEPTEE` |
| 7 | Mailpit | « Candidature retenue : … » (Salma) |
| 8 | Stages de Salma | Un stage `PLANIFIE` ; Salma est `EN_STAGE` |
| 9 | Portail Bonita > cas archivés | Le cas est terminé par « Candidature acceptée » |

## D. Rejet (passerelle de décision, branche « non »)

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Sara (`sara.bouzidi@inpt.ac.ma`) candidate à l'offre 1 | Après quelques secondes : soumission `REJETEE`, règle `R1`, motif « suspendu » |
| 2 | Mailpit | « Candidature non recevable : Stage AMOA – Paiement mobile » |
| 3 | Portail Bonita | Le cas se termine par « Candidature rejetée » |
| 4 | Aïcha (`aicha.tahiri@inpt.ac.ma`) candidate aux offres 11, 12, 13, 14 | Rejets `R2`, `R2`, `R3`, `R4` |

## E. Demande de complément (R11)

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Mehdi (`mehdi.tazi@inpt.ac.ma`) candidate à l'offre 4 ; Détroit Data (`contact@detroit-data.ma`) démarre l'évaluation | `EN_COURS_EVALUATION` |
| 2 | Détroit Data demande un complément | Mailpit : « Complément d'information demandé : … » ; candidature toujours `EN_COURS_EVALUATION` |
| 3 | Portail Bonita | Le cas est **revenu** sur « Décision de l'entreprise » |
| 4 | Détroit Data refuse | Candidature `REFUSEE` ; Mailpit : « Réponse à votre candidature : … » |

## F. Gestion des erreurs

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Arrêter S3 : `Get-CimInstance Win32_Process -Filter "Name='java.exe'" \| ? CommandLine -like '*soap-offres*' \| % { Stop-Process -Id $_.ProcessId }` | |
| 2 | Omar (`omar.bennani@inpt.ac.ma`) candidate à l'offre 4 | 202, puis soumission `ERREUR` |
| 3 | Portail Bonita | Le cas est passé par « Gestion des erreurs techniques » et s'est terminé par « Traitement interrompu » |
| 4 | Mailpit | « Incident de traitement : … » (responsable) |
| 5 | `.\scripts\start-all.ps1 -Orchestration bonita` | S3 redémarre |

## G. Minuteur (facultatif, pour la vidéo)

- [ ] Dans le Studio, régler le minuteur de la tâche de décision sur **2 minutes**, redéployer.
- [ ] Déposer une candidature, ne pas décider : au bout de 2 minutes, Mailpit reçoit une relance « Nouvelle candidature : … » pour l'entreprise, et le cas revient sur la tâche de décision.
- [ ] Remettre 7 jours.

## H. Livrables

- [ ] Export `.bos` enregistré dans `bpmn\Candidature-1.0.bos`.
- [ ] Captures du diagramme et du portail Bonita faites (voir `docs/CAPTURES_A_FAIRE.md`).
