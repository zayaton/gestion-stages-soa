# Captures d'écran

## Captures automatiques (rien à faire)

| Fichiers | Produits par |
|---|---|
| `rapport/figures/ui-*.png` (interface, 28 écrans) | `.\scripts\captures-ui.ps1` |
| `rapport/figures/bonita-*.png` (processus déployé, tâches, cas, panne) | `.\scripts\captures-bonita.ps1` (Bonita Studio ouvert) |
| `rapport/figures/swagger-s4.png`, `newman-rapport.png` | `node tests\ui\captures-outils.mjs` (S4 démarré, après `tests-postman.ps1`) |
| `rapport/figures/bpmn-processus.png` | `node rapport\outils\exporter_bpmn.mjs` |

## Captures faites à la main (3, faites le 10/11)

SoapUI et Postman sont des logiciels de bureau : ces trois captures ne sont pas automatisées. Elles sont faites et
intégrées au rapport ; pour les refaire, suivre le tableau ci-dessous. Chacune a un emplacement
dans le rapport (chapitre 9), marqué `% TODO capture :` dans les sources LaTeX et affiché dans le PDF par un cadre
« CAPTURE À FAIRE ».

**Comment faire.** Enregistrer chaque capture en PNG dans `rapport/figures/`, **exactement au nom indiqué**, puis recompiler :
`.\rapport\compiler.ps1`. Le cadre est alors remplacé par l'image, sans rien modifier dans le LaTeX.
Conseils : fenêtre en plein écran, zoom à 100 %, pas d'information personnelle visible.

Préparation :
```powershell
.\scripts\reset-db.ps1
.\scripts\start-all.ps1 -Orchestration direct
```

| Fichier | Contenu | Comment |
|---|---|---|
| `soapui-suite-s1.png` | Projet ouvert, suite « S1 Étudiants » exécutée, tous les cas au vert | SoapUI > File > Import Project > `tests\soapui\Stages-SOA-soapui-project.xml` ; double-clic sur la suite « S1 Étudiants », bouton Run |
| `soapui-requete.png` | Requête `verifierEtudiant` avec l'en-tête WS-Security, et sa réponse | Dans la suite S1, ouvrir une étape `verifierEtudiant`, l'exécuter (flèche verte) ; requête à gauche, réponse à droite |
| `postman-runner.png` | Résultat du Runner : toutes les requêtes et assertions au vert | Postman > Import : `tests\postman\Stages-SOA.postman_collection.json` et `Stages-SOA-local.postman_environment.json` ; choisir l'environnement « Stages SOA - local » ; clic droit sur la collection > Run collection > Run. Base fraîche et mode direct obligatoires : `reset-db.ps1` puis `start-all.ps1 -Orchestration direct` |
