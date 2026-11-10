# Vidéo de démonstration : script minute par minute

Durée visée : **9 min 30**, en trois parties tournées séparément puis montées.
Narration en français, à voix posée ; une seule action à l'écran à la fois. Les phrases entre guillemets sont à dire (à adapter à votre façon de parler) ; les actions sont en liste.

| Partie | Qui | Durée | Contenu | Mode |
|---|---|---|---|---|
| A | Hiba Elamdaoui | 0:00 – 3:00 | Présentation, architecture, tests SoapUI et Postman | direct |
| B | Hibatollah Ait Ahmed | 3:00 – 6:30 | Processus Bonita de bout en bout, courriels, vue BPMN | bonita |
| C | Aya Zahri | 6:30 – 9:30 | Règles de rejet, complément, responsable, panne, conclusion | bonita |

Les parties A et B–C demandent un état différent (la collection Postman exige le mode direct et une base fraîche) : on les enregistre l'une après l'autre, avec la préparation indiquée au début de chacune.

---

## Liste de contrôle avant l'enregistrement

**Une fois**
- [ ] Bonita Studio : processus « Candidature » 1.0 construit et déployé (`docs/bonita/GUIDE_BONITA.md`), liste `docs/bonita/CHECKLIST_BONITA.md` passée. Pour la vidéo, minuteur de relance réglé sur 7 jours (pas 2 minutes).
- [ ] SoapUI : projet `tests\soapui\Stages-SOA-soapui-project.xml` importé.
- [ ] Postman : collection `Stages-SOA.postman_collection.json` et environnement `Stages-SOA-local.postman_environment.json` importés, environnement « Stages SOA - local » sélectionné.
- [ ] Rapport ouvert à la figure 3.1 (architecture) : `rapport\rapport.pdf`.

**Avant chaque partie**
- [ ] Notifications Windows coupées (Assistant de concentration), autres applications fermées.
- [ ] Écran en 1920 × 1080, zoom du navigateur à 110 %, police de SoapUI et Postman lisible.
- [ ] Trois fenêtres de navigation **privées** distinctes (une par rôle), pour ne pas mélanger les sessions : étudiant, entreprise, responsable.
- [ ] Onglet Mailpit ouvert : http://localhost:8025 (vide après `reset-db.ps1`).

**Partie A**
```powershell
.\scripts\reset-db.ps1
.\scripts\start-all.ps1 -Orchestration direct
.\scripts\check-health.ps1          # sept lignes à 200
```

**Parties B et C**
```powershell
.\scripts\reset-db.ps1
.\scripts\start-all.ps1 -Orchestration bonita   # doit afficher « Orchestration : bonita », sans avertissement
.\scripts\check-health.ps1
```
Bonita Studio ouvert sur le diagramme ; portail Bonita (http://localhost:8080/bonita, `walter.bates` / `bpm`) ouvert sur la liste des cas.

---

## Partie A — Hiba : architecture et tests des services (0:00 – 3:00)

**0:00 – 0:40 · Présentation**
- Écran : rapport, page de couverture, puis figure 3.1.
- « Bonjour, nous sommes Hiba Elamdaoui, Aya Zahri et Hibatollah Ait Ahmed, filière AMOA. Notre projet gère les offres de stage et les candidatures avec une architecture orientée services. »
- « Six services indépendants, chacun avec sa base : trois services SOAP pour les étudiants, les entreprises et les offres ; trois services REST pour les candidatures, les notifications et le suivi des stages. Un processus BPMN dans Bonita orchestre le traitement d'une candidature, et un portail sert l'interface. »

**0:40 – 1:40 · SoapUI**
- Ouvrir SoapUI, projet « Stages SOA ». Déplier la suite « S1 Étudiants ».
- Ouvrir une requête `verifierEtudiant` : montrer l'en-tête `wsse:Security`.
- « Chaque appel SOAP est authentifié par WS-Security, avec un compte de service. Les contrats WSDL ont été écrits avant le code. »
- Mettre l'identifiant 17 dans la requête, exécuter : montrer `eligible` à false, `regle` R1, le motif.
- « Sara est suspendue : le service répond qu'elle n'est pas éligible, règle R1. »
- Supprimer le bloc `wsse:Security`, exécuter : montrer la faute `InvalidSecurity`. Annuler la modification (Ctrl+Z).
- Lancer la suite « S1 Étudiants » (bouton Run) : tous les cas au vert.
- « La suite teste chaque opération, les fautes et la sécurité. Au total, 546 assertions, dont de vraies pannes de S1, de S3 et de la base. »

**1:40 – 2:40 · Postman**
- Ouvrir Postman, collection « Stages SOA - services REST ». Ouvrir « 2. Parcours nominal », requête de dépôt : montrer l'en-tête `X-API-Key` et le corps.
- « Côté REST, chaque client a sa clé d'API. Les codes HTTP suivent le résultat : 202 pour un dépôt traité, 409 pour une règle violée, 503 pour un service indisponible. »
- Clic droit sur la collection > Run collection > Run.
- Pendant l'exécution : « La collection rejoue tous les scénarios dans l'ordre : sécurité, parcours complet, un rejet par règle, annulation, complément, suivi du stage. »
- Montrer le résultat : 87 requêtes, 226 assertions, toutes réussies.

**2:40 – 3:00 · Transition**
- Ouvrir http://localhost:8084/docs/ (Swagger UI), déplier « Processus » quelques secondes.
- « Chaque service REST publie son contrat OpenAPI. Hibatollah va maintenant montrer le processus dans Bonita. »

---

## Partie B — Hibatollah : le processus Bonita de bout en bout (3:00 – 6:30)

**3:00 – 3:45 · Le diagramme**
- Bonita Studio, diagramme « Candidature » en plein écran.
- « Le processus suit les neuf étapes de l'énoncé, sur trois couloirs. Vérification de l'étudiant et de l'offre par les services SOAP, contrôle de la candidature, puis une passerelle de décision : rejet avec notification, ou enregistrement et transmission à l'entreprise. »
- Montrer la tâche humaine « Décision de l'entreprise », la passerelle à trois issues, la boucle de complément, le minuteur, puis le sous-processus d'erreur.
- « Si un service ne répond pas, le sous-processus d'erreur interrompt proprement le traitement et alerte le responsable. »

**3:45 – 4:45 · L'étudiante candidate**
- Fenêtre privée 1 : http://localhost:8090, se connecter avec `salma.elidrissi@inpt.ac.ma` / `Etudiant@2026`.
- Montrer la liste des offres et la colonne de compatibilité. Cocher « Compatibles avec mon profil ».
- Ouvrir « Stage AMOA – Paiement mobile » (NovaPay Maghreb). Écrire une lettre (au moins 30 caractères), « Envoyer ma candidature ».
- Attendre « Candidature enregistrée ». « Le dépôt a démarré un cas dans Bonita. »
- Portail Bonita : actualiser la liste des cas, montrer le nouveau cas en attente sur « Décision de l'entreprise ».
- Retour au portail du projet : « Suivre cette candidature » : montrer le diagramme, étapes en bleu, décision en attente en ocre.

**4:45 – 5:45 · L'entreprise décide**
- Fenêtre privée 2 : se connecter avec `contact@novapay-maghreb.ma` / `Entreprise@2026`.
- Ouvrir la candidature de Salma : montrer le courriel et le téléphone masqués. « L'entreprise ne voit pas les coordonnées complètes avant l'acceptation. »
- « Démarrer l'évaluation ». « À partir de maintenant, l'étudiante ne peut plus annuler : règle R8. »
- Choisir « Accepter », commentaire « Profil en adéquation avec la mission. », « Enregistrer la décision », confirmer.
- Montrer « Candidature acceptée » et le diagramme coloré jusqu'à la fin.
- Portail Bonita : le cas est terminé (cas archivés). « La décision a exécuté la tâche humaine ; Bonita a enchaîné : place retirée, notification, dossier de stage, statut de l'étudiante. »

**5:45 – 6:30 · Courriels et suivi**
- Onglet Mailpit : montrer les trois courriels (confirmation de dépôt, nouvelle candidature pour l'entreprise, acceptation) ; ouvrir l'acceptation.
- Fenêtre 1 (Salma), actualiser « Mon stage » : le stage planifié apparaît.
- « Les courriels sont réellement envoyés par SMTP, ici à un serveur de test. Aya va montrer les règles de gestion et l'espace du responsable. »

---

## Partie C — Aya : règles, responsable, panne et conclusion (6:30 – 9:30)

**6:30 – 7:30 · Rejets par les règles**
- Fenêtre 1 : se déconnecter, se connecter avec `sara.bouzidi@inpt.ac.ma` / `Etudiant@2026`. Candidater à l'offre « Stage AMOA – Paiement mobile ».
- Montrer « Candidature non recevable (règle R1) » et le motif.
- Se reconnecter avec `aicha.tahiri@inpt.ac.ma` ; dans l'adresse, aller à `#/etudiant/offres/13` ; candidater.
- Montrer le rejet R3, date limite dépassée.
- « Onze règles encadrent le traitement, chacune identifiée de R1 à R11 ; l'étudiant voit toujours laquelle s'applique. » Mailpit : montrer le courriel de rejet de Sara.

**7:30 – 8:05 · Demande de complément (R11)**
- Fenêtre 1 : `mehdi.tazi@inpt.ac.ma`, offre « Ingénieur données – Flux logistiques », candidater.
- Fenêtre 2 : se déconnecter, `contact@detroit-data.ma` / `Entreprise@2026`, ouvrir la candidature de Mehdi, « Démarrer l'évaluation », « Demander un complément », commentaire « Merci de préciser vos disponibilités. », enregistrer.
- « La candidature reste en évaluation et la décision revient à l'entreprise : c'est la boucle R11 du processus. »

**8:05 – 8:45 · Espace responsable et panne**
- Fenêtre 3 : `responsable@inpt.ac.ma` / `Responsable@2026`. Tableau de bord : indicateurs, état des six services.
- Fenêtre 1 : se connecter avec `aicha.tahiri@inpt.ac.ma`, aller à `#/etudiant/offres/10` et écrire la lettre, **sans envoyer** (la page d'une offre vient de S3 : elle doit être ouverte avant la panne).
- Dans PowerShell : `.\scripts\arreter-service.ps1 S3`. Fenêtre 3, actualiser : bandeau « Indicateurs incomplets », S3 indisponible dans l'état des services, les autres indicateurs toujours là.
- Fenêtre 1 : « Envoyer ma candidature » : après quelques secondes, « Traitement interrompu ».
- Mailpit : courriel d'alerte « Incident de traitement » reçu par le responsable.
- « Le connecteur a levé une erreur, le sous-processus a interrompu le cas et alerté le responsable. » Relancer : `.\scripts\start-all.ps1`.

**8:45 – 9:30 · Conclusion**
- Écran : rapport, tableau des résultats de test (chapitre 9).
- « Tout est vérifié automatiquement : 356 tests JUnit, 546 assertions SoapUI, 226 assertions Postman, et un test de bout en bout qui rejoue chaque scénario et contrôle les courriels. »
- « Le rapport détaille l'architecture, les contrats, les règles et le processus. Merci de votre attention. »

---

## Après l'enregistrement

```powershell
.\scripts\reset-db.ps1      # les données de la démonstration ont changé
```

## Si quelque chose se passe mal pendant la prise

| Problème | Solution |
|---|---|
| Dépôt en 503 pendant la partie B | Bonita n'est pas démarré ou le processus n'est pas déployé : `start-all.ps1` l'indique. Couper, vérifier, reprendre la partie B. |
| Rejet R1 inattendu pour Salma | Les données ont déjà servi : `reset-db.ps1`, puis reprendre la partie. |
| La collection Postman échoue | Elle exige une base fraîche et le mode direct : `.\scripts\tests-postman.ps1` le fait, ou `reset-db.ps1` puis `start-all.ps1 -Orchestration direct`. |
| Le cas Bonita n'apparaît pas | Actualiser la liste des cas du portail Bonita (elle ne se met pas à jour seule). |
