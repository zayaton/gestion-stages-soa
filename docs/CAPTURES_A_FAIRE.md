# Captures d'écran à faire soi-même

Ces douze captures montrent des logiciels de bureau (Bonita Studio, SoapUI, Postman) ou une page à mettre en forme à la main :
elles ne sont pas automatisées. Chacune a un emplacement dans le rapport, marqué `% TODO capture :` dans les sources LaTeX
et affiché dans le PDF par un cadre « CAPTURE À FAIRE ».

**Comment faire.** Enregistrer chaque capture en PNG dans `rapport/figures/`, **exactement au nom indiqué**, puis recompiler :
`.\rapport\compiler.ps1`. Le cadre est alors remplacé par l'image, sans rien modifier dans le LaTeX.
Conseils : fenêtre en plein écran, zoom à 100 %, pas d'information personnelle visible (onglets, notifications).

Les captures de l'interface web (`rapport/figures/ui-*.png`) et le diagramme exporté (`bpmn-processus.png`) sont produits
automatiquement : `.\scripts\captures-ui.ps1` et `node rapport\outils\exporter_bpmn.mjs`.

## Préparation

```powershell
.\scripts\reset-db.ps1
.\scripts\start-all.ps1 -Orchestration bonita     # pour les captures Bonita (processus déployé)
.\scripts\start-all.ps1 -Orchestration direct     # pour SoapUI, Postman et Swagger
```

## Bonita (chapitre 7 du rapport)

| Fichier | Contenu | Quand |
|---|---|---|
| `bonita-diagramme.png` | Le diagramme complet du processus « Candidature » dans Bonita Studio (zoom ajusté, trois couloirs visibles) | Après la section 3 du guide |
| `bonita-contrat.png` | Onglet Contrat du bassin : soumissionId, etudiantId, offreId, lettreMotivation | Section 5 du guide |
| `bonita-connecteur-soap.png` | Configuration du connecteur SOAP de « Vérification de l'étudiant » (enveloppe visible) | Section 7 du guide |
| `bonita-connecteur-rest.png` | Configuration d'un connecteur REST (en-têtes X-API-Key visibles) | Section 7 du guide |
| `bonita-sous-processus-erreur.png` | Le sous-processus « Gestion des erreurs techniques » | Section 10 du guide |
| `bonita-portail-cas.png` | Portail Bonita : un cas en attente sur « Décision de l'entreprise » | Liste de contrôle, C.2 |
| `bonita-cas-termine.png` | Portail Bonita : un cas archivé terminé par « Candidature acceptée » | Liste de contrôle, C.9 |

## Swagger UI (chapitre 5)

| Fichier | Contenu | Comment |
|---|---|---|
| `swagger-s4.png` | Swagger UI de S4, groupe « Processus » déplié | Ouvrir http://localhost:8084/docs/ dans le navigateur, déplier « Processus » |

## SoapUI (chapitre 9)

| Fichier | Contenu | Comment |
|---|---|---|
| `soapui-suite-s1.png` | Projet ouvert, suite « S1 Étudiants » exécutée, tous les cas au vert | SoapUI > File > Import Project > `tests\soapui\Stages-SOA-soapui-project.xml` ; double-clic sur la suite « S1 Étudiants », bouton Run (base fraîche : `reset-db.ps1` avant) |
| `soapui-requete.png` | Requête `verifierEtudiant` avec l'en-tête WS-Security, et sa réponse | Dans la suite S1, ouvrir une étape `verifierEtudiant`, l'exécuter (flèche verte) ; requête à gauche, réponse à droite |

## Postman et Newman (chapitre 9)

| Fichier | Contenu | Comment |
|---|---|---|
| `postman-runner.png` | Résultat du Runner : toutes les requêtes et assertions au vert | Postman > Import : `tests\postman\Stages-SOA.postman_collection.json` et `Stages-SOA-local.postman_environment.json` ; choisir l'environnement « Stages SOA - local » ; clic droit sur la collection > Run collection > Run. Base fraîche et mode direct obligatoires : `reset-db.ps1` puis `start-all.ps1 -Orchestration direct` |
| `newman-rapport.png` | Page de synthèse du rapport HTML de Newman | Lancer `.\scripts\tests-postman.ps1`, puis ouvrir `tests\postman\rapports\rapport-newman.html` dans le navigateur |
