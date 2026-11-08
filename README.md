# Gestion Intelligente des Stages et des Offres de Recrutement

Projet du module SOA, filière AMOA, INPT, année universitaire 2026-2027.
Hiba Elamdaoui, Aya Zahri, Hibatollah Ait Ahmed.

> Ce README est complété à chaque phase. Sa version finale arrive en phase 9.

## Prérequis

- JDK 17, Maven 3.9, Docker Desktop (Windows, avec WSL 2).

## Démarrage

Depuis PowerShell, à la racine du projet :

```powershell
.\scripts\start-all.ps1          # construit si besoin, démarre Docker et les services
.\scripts\check-health.ps1       # état de chaque service
.\scripts\stop-all.ps1           # arrête tout, les données sont conservées
.\scripts\reset-db.ps1           # remet les bases et Mailpit à l'état initial
.\scripts\verifier-socle.ps1     # vérifie le socle et les données de démonstration
.\scripts\build.ps1              # compile et lance les tests unitaires
```

Si PowerShell refuse d'exécuter les scripts :
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, ou `powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1`.

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

## Comptes de démonstration

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Responsable | `responsable@inpt.ac.ma` | `Responsable@2026` |
| Étudiant | `salma.elidrissi@inpt.ac.ma` | `Etudiant@2026` |
| Entreprise | `contact@novapay-maghreb.ma` | `Entreprise@2026` |

Tous les étudiants et toutes les entreprises ont un compte : voir [docs/SCENARIOS_DEMO.md](docs/SCENARIOS_DEMO.md).
Toutes les données sont fictives.
