# Scénarios de démonstration

Ces scénarios s'appuient sur les données chargées par `scripts\reset-db.ps1`. Les dates sont relatives au jour du chargement.
Les préconditions sont vérifiées par `scripts\verifier-socle.ps1`.

Chaque scénario a son propre étudiant : le parcours nominal fait passer Salma El Idrissi `EN_STAGE`, elle ne peut donc plus servir aux autres démonstrations (règle R1).

**Jouez les scénarios dans l'ordre du tableau** (c'est aussi l'ordre de la collection Postman). Par exemple, « R8 – annulation acceptée » annule la candidature de Youssef : jouée avant « R5 – doublon », elle lui permettrait de recandidater et le rejet R5 n'aurait plus lieu. En cas de doute : `.\scripts\reset-db.ps1`.

Mots de passe : `Etudiant@2026` pour les étudiants, `Entreprise@2026` pour les entreprises, `Responsable@2026` pour le responsable (`responsable@inpt.ac.ma`).

| Scénario | Compte étudiant | Offre | Résultat attendu |
|---|---|---|---|
| Parcours nominal | Salma El Idrissi (n° 1) `salma.elidrissi@inpt.ac.ma` | 1 – Stage AMOA, NovaPay Maghreb | Candidature enregistrée, entreprise notifiée |
| R1 – étudiant en stage | Ilyas Zemmouri (n° 16) `ilyas.zemmouri@inpt.ac.ma` | 1 | Rejet : statut `EN_STAGE` |
| R1 – étudiant suspendu | Sara Bouzidi (n° 17) `sara.bouzidi@inpt.ac.ma` | 1 | Rejet : statut `SUSPENDU` |
| R2 – offre non active | Aïcha Tahiri (n° 20) `aicha.tahiri@inpt.ac.ma` | 11 – Sécurité des SI (`BROUILLON`) | Rejet : offre non active |
| R2 – entreprise inactive | Aïcha Tahiri (n° 20) `aicha.tahiri@inpt.ac.ma` | 12 – Médina Systèmes (`INACTIVE`) | Rejet : entreprise non active |
| R3 – date limite passée | Aïcha Tahiri (n° 20) `aicha.tahiri@inpt.ac.ma` | 13 – Consultant décisionnel | Rejet : date limite dépassée |
| R4 – plus de place | Aïcha Tahiri (n° 20) `aicha.tahiri@inpt.ac.ma` | 14 – Chef de projet junior | Rejet : aucune place disponible |
| R5 – doublon | Youssef Benjelloun (n° 2) `youssef.benjelloun@inpt.ac.ma` | 2 – Analyste fonctionnel | Rejet : candidature déjà déposée |
| R6 – 5 candidatures actives | Imane Alaoui (n° 3) `imane.alaoui@inpt.ac.ma` | 1 | Rejet : limite de 5 atteinte |
| R7 – niveau insuffisant | Nour Chraibi (n° 5) `nour.chraibi@inpt.ac.ma` | 1 | Rejet : niveau 1A, 2A exigé |
| R7 – filière non acceptée | Anas Ouazzani (n° 6) `anas.ouazzani@inpt.ac.ma` | 1 | Rejet : filière ICCN non acceptée |
| R8 – annulation refusée | Rania Sefrioui (n° 13) `rania.sefrioui@inpt.ac.ma` | Candidature 9 (`EN_COURS_EVALUATION`) | Annulation refusée |
| R8 – annulation acceptée | Youssef Benjelloun (n° 2) | Candidature 1 (`DEPOSEE`) | Candidature annulée |
| R11 – demande de complément | Mehdi Tazi (n° 4) `mehdi.tazi@inpt.ac.ma` | 4 – Ingénieur données | L'étudiant est notifié, la décision revient à l'entreprise |
| R10 – offre pourvue | Anas Ouazzani (n° 6) puis Hamza Amrani (n° 8) | 6 – Réseaux 5G (1 place) | Anas accepté, offre `POURVUE` ; accepter Hamza est refusé (409) |
| Rappels | Offres 5 et 6 (date limite dans 2 et 3 jours) | – | Courriel aux étudiants actifs compatibles qui n'ont pas candidaté |

## Comptes entreprises

| Entreprise | Compte | Utilité |
|---|---|---|
| NovaPay Maghreb (n° 1) | `contact@novapay-maghreb.ma` | Décision du parcours nominal |
| Zellige Conseil (n° 3) | `contact@zellige-conseil.ma` | Candidatures 1 et 9 |
| Détroit Data Logistique (n° 7) | `contact@detroit-data.ma` | Demande de complément |

## Stages déjà présents

| Stage | Étudiant | Statut | Contenu |
|---|---|---|---|
| 1 | Ilyas Zemmouri | `EN_COURS` | Un compte rendu |
| 2 | Hajar Filali | `TERMINE` | Un compte rendu et une évaluation (16,50/20) |
| 3 | Reda Cherkaoui | `PLANIFIE` | Aucun |
