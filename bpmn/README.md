# Processus BPMN

| Fichier | Contenu |
|---|---|
| `processus-candidature.bpmn` | Processus « Candidature » en BPMN 2.0 (copie de `docs/bonita/processus-candidature.bpmn`, qui fait foi) |
| `Candidature--1.0.bar` | Archive Bonita du processus, telle que déployée dans Bonita Community 2025.2 (mode secours, classe `inpt.SoaProcessus` incluse). Reproduite par `scripts\deployer-bonita.ps1` |

Déploiement : Bonita Studio ouvert, puis `.\scripts\deployer-bonita.ps1`.
L'archive peut aussi s'installer à la main dans l'application d'administration de Bonita (BPM > Processus > Installer),
puis il faut associer les trois acteurs à un utilisateur et activer le processus.

Construction manuelle avec connecteurs dans Bonita Studio : `docs/bonita/GUIDE_BONITA.md`. Vérification : `docs/bonita/CHECKLIST_BONITA.md`.
