#!/usr/bin/env python3
"""Génère la collection Postman (Stages-SOA.postman_collection.json) et son environnement.

Les fichiers produits sont versionnés : Postman ou Newman suffisent pour les lancer ; Python ne sert qu'à les régénérer.
La collection se joue dans l'ordre, sur des données fraîches (scripts\\tests-postman.ps1 réinitialise la base avant).
"""
import json
import os
import uuid

ICI = os.path.dirname(os.path.abspath(__file__))
LETTRE = "Je souhaite rejoindre votre équipe : ce stage correspond à mon projet professionnel."


def requete(nom, methode, service, chemin, tests, corps=None, cle="postman", entetes=None, avant=None):
    h = [{"key": "Content-Type", "value": "application/json"}, {"key": "X-Acteur", "value": "postman"}]
    if cle:
        h.append({"key": "X-API-Key", "value": "{{cle_" + cle + "}}"})
    for k, v in (entetes or {}).items():
        h.append({"key": k, "value": v})
    url = "{{url_" + service + "}}" + chemin if service != "mailpit" else "{{url_mailpit}}" + chemin
    item = {
        "name": nom,
        "request": {"method": methode, "header": h, "url": url},
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": tests.strip().split("\n")}}],
    }
    if corps is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(corps, ensure_ascii=False, indent=2),
                                   "options": {"raw": {"language": "json"}}}
    if avant:
        item["event"].append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": avant.strip().split("\n")}})
    return item


def dossier(nom, description, items):
    return {"name": nom, "description": description, "item": items}


def statut(code):
    return f'pm.test("Code HTTP {code}", () => pm.response.to.have.status({code}));\n'


ERREUR = '''pm.test("Corps d'erreur uniforme", () => {
  const e = pm.response.json();
  ["code", "message", "details", "timestamp", "path"].forEach(c => pm.expect(e).to.have.property(c));
  pm.expect(e.message).to.be.a("string").and.not.empty;
});
'''


def erreur(code_http, code):
    return statut(code_http) + ERREUR + f'pm.test("Code {code}", () => pm.expect(pm.response.json().code).to.eql("{code}"));\n'


def rejet(regle, extrait):
    return statut(202) + f'''pm.test("Soumission rejetée par la règle {regle}", () => {{
  const s = pm.response.json();
  pm.expect(s.statut).to.eql("REJETEE");
  pm.expect(s.regleRejetee).to.eql("{regle}");
  pm.expect(s.candidatureId).to.be.undefined;
  pm.expect(s.motifRejet).to.include("{extrait}");
}});
pm.test("Le rejet passe par la passerelle de décision puis notifie l'étudiant", () => {{
  const etapes = pm.response.json().etapes.map(e => e.etape + ":" + e.resultat);
  pm.expect(etapes).to.include("PASSERELLE_DECISION:REJET");
  pm.expect(etapes).to.include("NOTIFICATION_REJET:OK");
  pm.expect(etapes).to.not.include("ENREGISTREMENT:OK");
}});
'''


def depot(etudiant, offre):
    return {"etudiantId": etudiant, "offreId": offre, "lettreMotivation": LETTRE}


def enregistre(variable):
    return statut(202) + f'''pm.test("Candidature enregistrée", () => {{
  const s = pm.response.json();
  pm.expect(s.statut).to.eql("ENREGISTREE");
  pm.expect(s.candidatureId).to.be.a("number");
  pm.collectionVariables.set("{variable}", s.candidatureId);
}});
'''


ITEMS = []

# 1. Sécurité
ITEMS.append(dossier("1. Sécurité des API REST", "Clé d'API par client (401) et droits par opération (403).", [
    requete("Sans clé d'API : 401", "GET", "candidatures", "/candidatures/1", erreur(401, "NON_AUTHENTIFIE")
            + 'pm.test("En-tête WWW-Authenticate", () => pm.response.to.have.header("WWW-Authenticate"));', cle=None),
    requete("Clé inconnue : 401", "GET", "suivi", "/stages", erreur(401, "NON_AUTHENTIFIE"), cle=None, entetes={"X-API-Key": "cle-inventee"}),
    requete("Le portail ne fait pas envoyer de courriel : 403", "POST", "notifications", "/notifications", erreur(403, "ACCES_INTERDIT"), cle="portail",
            corps={"type": "REFUS", "destinataire": "x@inpt.ac.ma", "variables": {}}),
    requete("Bonita ne dépose pas à la place de l'étudiant : 403", "POST", "candidatures", "/soumissions", erreur(403, "ACCES_INTERDIT"), cle="bonita",
            corps=depot(1, 1)),
    requete("La corrélation est renvoyée", "GET", "candidatures", "/candidatures/1",
            statut(200) + 'pm.test("X-Correlation-Id renvoyé", () => pm.expect(pm.response.headers.get("X-Correlation-Id")).to.eql("postman-correlation"));',
            entetes={"X-Correlation-Id": "postman-correlation"}),
]))

# 2. Parcours nominal
ITEMS.append(dossier("2. Parcours nominal : du dépôt au stage", "Salma El Idrissi candidate à l'offre 1 ; NovaPay Maghreb accepte (étapes 1 à 9).", [
    requete("Dépôt de la candidature (étapes 1 à 6)", "POST", "candidatures", "/soumissions", enregistre("candidature_nominale") + '''
pm.test("Les huit premières étapes sont passées dans l'ordre", () => {
  pm.expect(pm.response.json().etapes.map(e => e.etape)).to.eql(["RECEPTION", "VERIFICATION_ETUDIANT", "VERIFICATION_OFFRE",
    "CONTROLE_CANDIDATURE", "PASSERELLE_DECISION", "ENREGISTREMENT", "NOTIFICATION_DEPOT", "TRANSMISSION_ENTREPRISE"]);
  pm.expect(pm.response.json().etapes.every(e => e.resultat === "OK")).to.be.true;
});
pm.test("En-tête Location vers la soumission", () => pm.expect(pm.response.headers.get("Location")).to.match(/\\/api\\/v1\\/soumissions\\/\\d+$/));''',
            corps=depot(1, 1), cle="portail"),
    requete("La candidature est déposée, avec son score indicatif", "GET", "candidatures", "/candidatures/{{candidature_nominale}}", statut(200) + '''
pm.test("Statut DEPOSEE et score de compétences", () => {
  const c = pm.response.json();
  pm.expect(c.statut).to.eql("DEPOSEE");
  pm.expect(c.scoreCompetences).to.be.within(0, 100);
  pm.expect(c.entrepriseId).to.eql(1);
});'''),
    requete("Décider avant d'évaluer : 409", "POST", "candidatures", "/candidatures/{{candidature_nominale}}/decision",
            erreur(409, "DECISION_IMPOSSIBLE"), corps={"decision": "ACCEPTER"}, cle="portail"),
    requete("L'entreprise démarre l'évaluation", "POST", "candidatures", "/candidatures/{{candidature_nominale}}/evaluation", statut(200)
            + 'pm.test("EN_COURS_EVALUATION", () => pm.expect(pm.response.json().statut).to.eql("EN_COURS_EVALUATION"));', cle="portail"),
    requete("L'entreprise accepte (étapes 7 à 9, R10)", "POST", "candidatures", "/candidatures/{{candidature_nominale}}/decision", statut(200) + '''
pm.test("Candidature ACCEPTEE", () => pm.expect(pm.response.json().candidature.statut).to.eql("ACCEPTEE"));
pm.test("R10 : place retirée, résultat notifié, stage créé, étudiant EN_STAGE", () => {
  pm.expect(pm.response.json().etapes.map(e => e.etape + ":" + e.resultat)).to.eql(["DECISION_ENTREPRISE:OK", "MISE_A_JOUR_PLACES:OK",
    "NOTIFICATION_RESULTAT:OK", "CREATION_STAGE:OK", "MISE_A_JOUR_ETUDIANT:OK"]);
});''', corps={"decision": "ACCEPTER", "commentaire": "Profil retenu, bienvenue chez NovaPay."}, cle="portail"),
    requete("Historique : déposée, en évaluation, acceptée", "GET", "candidatures", "/candidatures/{{candidature_nominale}}/historique", statut(200) + '''
pm.test("Trois changements de statut, avec acteur et date", () => {
  const h = pm.response.json();
  pm.expect(h.map(x => x.nouveauStatut)).to.eql(["DEPOSEE", "EN_COURS_EVALUATION", "ACCEPTEE"]);
  h.forEach(x => { pm.expect(x.acteur).to.not.be.empty; pm.expect(x.date).to.match(/^\\d{4}-\\d{2}-\\d{2}T/); });
});'''),
    requete("S6 : le dossier de stage existe", "GET", "suivi", "/candidatures/{{candidature_nominale}}/stage", statut(200) + '''
pm.test("Stage PLANIFIE de l'étudiante 1", () => {
  const s = pm.response.json();
  pm.expect(s.statut).to.eql("PLANIFIE");
  pm.expect(s.etudiantId).to.eql(1);
  pm.expect(s.encadrantEntreprise).to.eql("Ghita Sentissi");
  pm.expect(new Date(s.dateFin) > new Date(s.dateDebut)).to.be.true;
});'''),
    requete("R8 : une candidature acceptée ne s'annule pas", "DELETE", "candidatures", "/candidatures/{{candidature_nominale}}",
            erreur(409, "ANNULATION_IMPOSSIBLE") + 'pm.test("La règle est citée", () => pm.expect(pm.response.json().message).to.include("R8"));', cle="portail"),
    requete("S5 : l'étudiante a reçu confirmation puis acceptation", "GET", "notifications",
            "/notifications?destinataire=salma.elidrissi@inpt.ac.ma", statut(200) + '''
pm.test("CONFIRMATION_DEPOT et ACCEPTATION envoyées", () => {
  const types = pm.response.json().filter(n => n.statut === "ENVOYEE").map(n => n.type);
  pm.expect(types).to.include("CONFIRMATION_DEPOT");
  pm.expect(types).to.include("ACCEPTATION");
});''', cle="portail"),
    requete("Mailpit : les courriels sont réellement arrivés", "GET", "mailpit", "/search?query=to:salma.elidrissi@inpt.ac.ma", statut(200) + '''
pm.test("Confirmation et acceptation dans la boîte", () => {
  const sujets = pm.response.json().messages.map(m => m.Subject);
  pm.expect(sujets.some(s => s.startsWith("Candidature enregistrée"))).to.be.true;
  pm.expect(sujets.some(s => s.startsWith("Candidature retenue"))).to.be.true;
});''', cle=None),
]))

# 3. Rejets R1 à R7
ITEMS.append(dossier("3. Règles R1 à R7 : un rejet par règle", "Chaque cas de docs/SCENARIOS_DEMO.md, avec un étudiant différent de celui du parcours nominal (qui est désormais EN_STAGE). Réponse 202 : le dépôt est traité, la candidature n'est pas enregistrée.", [
    requete("R1 : étudiante suspendue (Sara Bouzidi)", "POST", "candidatures", "/soumissions", rejet("R1", "suspendu")
            + 'pm.test("Les contrôles suivants ne sont pas exécutés", () => pm.expect(pm.response.json().etapes.map(e => e.etape)).to.not.include("VERIFICATION_OFFRE"));',
            corps=depot(17, 1), cle="portail"),
    requete("R1 : étudiant déjà en stage (Ilyas Zemmouri)", "POST", "candidatures", "/soumissions", rejet("R1", "déjà en stage"), corps=depot(16, 1), cle="portail"),
    requete("R2 : offre en brouillon", "POST", "candidatures", "/soumissions", rejet("R2", "BROUILLON"), corps=depot(20, 11), cle="portail"),
    requete("R2 : entreprise inactive", "POST", "candidatures", "/soumissions", rejet("R2", "Médina Systèmes"), corps=depot(20, 12), cle="portail"),
    requete("R3 : date limite dépassée", "POST", "candidatures", "/soumissions", rejet("R3", "date limite"), corps=depot(20, 13), cle="portail"),
    requete("R4 : plus de place", "POST", "candidatures", "/soumissions", rejet("R4", "plus de place"), corps=depot(20, 14), cle="portail"),
    requete("R5 : candidature déjà déposée (Youssef Benjelloun)", "POST", "candidatures", "/soumissions", rejet("R5", "déjà une candidature"), corps=depot(2, 2), cle="portail"),
    requete("R6 : cinq candidatures actives (Imane Alaoui)", "POST", "candidatures", "/soumissions", rejet("R6", "5 candidatures actives"), corps=depot(3, 1), cle="portail"),
    requete("R7 : niveau insuffisant (Nour Chraibi, 1A)", "POST", "candidatures", "/soumissions", rejet("R7", "niveau 2A"), corps=depot(5, 1), cle="portail"),
    requete("R7 : filière non acceptée (Anas Ouazzani, ICCN)", "POST", "candidatures", "/soumissions", rejet("R7", "ICCN"), corps=depot(6, 1), cle="portail"),
    requete("Les rejets restent consultables par l'étudiante (Aïcha Tahiri)", "GET", "candidatures", "/etudiants/20/soumissions", statut(200) + '''
pm.test("Rejets R2, R2, R3 et R4, aucune candidature créée", () => {
  const s = pm.response.json();
  pm.expect(s.map(x => x.regleRejetee).sort()).to.eql(["R2", "R2", "R3", "R4"]);
  s.forEach(x => pm.expect(x.candidatureId).to.be.undefined);
});'''),
    requete("Mailpit : le rejet donne le motif à l'étudiante", "GET", "mailpit", "/search?query=to:sara.bouzidi@inpt.ac.ma", statut(200) + '''
pm.test("Courriel de rejet reçu, avec le titre de l'offre", () => {
  const sujets = pm.response.json().messages.map(m => m.Subject);
  pm.expect(sujets).to.include("Candidature non recevable : Stage AMOA – Paiement mobile");
});''', cle=None),
]))

# 4. Contrôles R5, R6 côté S4 (enregistrement par le processus)
ITEMS.append(dossier("4. R5 et R6 à l'enregistrement", "Le processus enregistre la candidature : R5 et R6 y sont revérifiées de façon atomique (409).", [
    requete("Vérification de doublon (R5)", "GET", "candidatures", "/candidatures/verification-doublon?etudiantId=2&offreId=2", statut(200) + '''
pm.test("Doublon détecté : candidature 1", () => { const v = pm.response.json(); pm.expect(v.doublon).to.be.true; pm.expect(v.candidatureId).to.eql(1); });'''),
    requete("Compte des candidatures actives (R6)", "GET", "candidatures", "/etudiants/3/candidatures/actives/compte", statut(200) + '''
pm.test("5 sur 5 : limite atteinte", () => { const c = pm.response.json(); pm.expect(c.actives).to.eql(5); pm.expect(c.limiteAtteinte).to.be.true; });'''),
    requete("R5 : enregistrer un doublon : 409", "POST", "candidatures", "/candidatures", erreur(409, "CANDIDATURE_EN_DOUBLE"),
            corps={"etudiantId": 2, "offreId": 2, "entrepriseId": 3, "lettreMotivation": "Doublon", "scoreCompetences": 50}, cle="bonita"),
    requete("R6 : enregistrer une sixième candidature active : 409", "POST", "candidatures", "/candidatures", erreur(409, "LIMITE_CANDIDATURES_ATTEINTE"),
            corps={"etudiantId": 3, "offreId": 1, "entrepriseId": 1, "lettreMotivation": "Sixième", "scoreCompetences": 50}, cle="bonita"),
    requete("Le portail n'enregistre pas de candidature : 403", "POST", "candidatures", "/candidatures", erreur(403, "ACCES_INTERDIT"),
            corps={"etudiantId": 4, "offreId": 4, "entrepriseId": 7, "lettreMotivation": "x"}, cle="portail"),
]))

# 5. R8 et R9
ITEMS.append(dossier("5. R8 annulation et R9 transitions", "", [
    requete("R8 : annuler une candidature déposée (Youssef, candidature 1)", "DELETE", "candidatures", "/candidatures/1", statut(204), cle="portail"),
    requete("La candidature annulée reste consultable", "GET", "candidatures", "/candidatures/1", statut(200)
            + 'pm.test("ANNULEE", () => pm.expect(pm.response.json().statut).to.eql("ANNULEE"));'),
    requete("R8 : annuler deux fois : 409", "DELETE", "candidatures", "/candidatures/1", erreur(409, "ANNULATION_IMPOSSIBLE"), cle="portail"),
    requete("R8 : annuler une candidature en cours d'évaluation (Rania, candidature 9) : 409", "DELETE", "candidatures", "/candidatures/9",
            erreur(409, "ANNULATION_IMPOSSIBLE"), cle="portail"),
    requete("R5 : après annulation, Youssef peut recandidater", "POST", "candidatures", "/soumissions", enregistre("candidature_recandidature"),
            corps=depot(2, 2), cle="portail"),
    requete("R9 : DEPOSEE vers ACCEPTEE : 409", "PATCH", "candidatures", "/candidatures/2/statut",
            erreur(409, "TRANSITION_INTERDITE") + 'pm.test("La règle est citée", () => pm.expect(pm.response.json().message).to.include("R9"));',
            corps={"statut": "ACCEPTEE"}),
    requete("R9 : DEPOSEE vers EN_COURS_EVALUATION : 200", "PATCH", "candidatures", "/candidatures/2/statut", statut(200)
            + 'pm.test("EN_COURS_EVALUATION", () => pm.expect(pm.response.json().statut).to.eql("EN_COURS_EVALUATION"));',
            corps={"statut": "EN_COURS_EVALUATION", "commentaire": "Ouverture du dossier"}),
    requete("R9 : EN_COURS_EVALUATION vers DEPOSEE : 409", "PATCH", "candidatures", "/candidatures/2/statut", erreur(409, "TRANSITION_INTERDITE"),
            corps={"statut": "DEPOSEE"}),
    requete("R9 : ACCEPTEE vers REFUSEE : 409", "PATCH", "candidatures", "/candidatures/10/statut", erreur(409, "TRANSITION_INTERDITE"),
            corps={"statut": "REFUSEE"}),
    requete("R9 : statut inconnu : 422", "PATCH", "candidatures", "/candidatures/2/statut", erreur(422, "DONNEES_INVALIDES"), corps={"statut": "VALIDEE"}),
]))

# 6. R10 : offre pourvue
ITEMS.append(dossier("6. R10 : la dernière place rend l'offre pourvue", "Offre 6 (une place) : Anas et Hamza candidatent, Anas est accepté, Hamza ne peut plus l'être.", [
    requete("Anas Ouazzani candidate à l'offre 6", "POST", "candidatures", "/soumissions", enregistre("candidature_anas"), corps=depot(6, 6), cle="portail"),
    requete("Hamza Amrani candidate à l'offre 6", "POST", "candidatures", "/soumissions", enregistre("candidature_hamza"), corps=depot(8, 6), cle="portail"),
    requete("Évaluation de la candidature d'Anas", "POST", "candidatures", "/candidatures/{{candidature_anas}}/evaluation", statut(200), cle="portail"),
    requete("Évaluation de la candidature de Hamza", "POST", "candidatures", "/candidatures/{{candidature_hamza}}/evaluation", statut(200), cle="portail"),
    requete("Anas est accepté : dernière place", "POST", "candidatures", "/candidatures/{{candidature_anas}}/decision", statut(200)
            + 'pm.test("ACCEPTEE", () => pm.expect(pm.response.json().candidature.statut).to.eql("ACCEPTEE"));',
            corps={"decision": "ACCEPTER"}, cle="portail"),
    requete("R10 : accepter Hamza sans place restante : 409, rien ne change", "POST", "candidatures", "/candidatures/{{candidature_hamza}}/decision",
            erreur(409, "PLUS_DE_PLACE"), corps={"decision": "ACCEPTER"}, cle="portail"),
    requete("Hamza reste en cours d'évaluation", "GET", "candidatures", "/candidatures/{{candidature_hamza}}", statut(200)
            + 'pm.test("EN_COURS_EVALUATION", () => pm.expect(pm.response.json().statut).to.eql("EN_COURS_EVALUATION"));'),
    requete("R10 : l'offre 6 est désormais POURVUE (nouveau dépôt rejeté par R2)", "POST", "candidatures", "/soumissions", rejet("R2", "POURVUE"),
            corps=depot(15, 6), cle="portail"),
]))

# 7. R11 : complément
ITEMS.append(dossier("7. R11 : demande de complément", "Mehdi Tazi candidate à l'offre 4 ; Détroit Data demande un complément, puis refuse.", [
    requete("Mehdi Tazi candidate à l'offre 4", "POST", "candidatures", "/soumissions", enregistre("candidature_mehdi"), corps=depot(4, 4), cle="portail"),
    requete("Évaluation démarrée", "POST", "candidatures", "/candidatures/{{candidature_mehdi}}/evaluation", statut(200), cle="portail"),
    requete("R11 : complément sans précision : 422", "POST", "candidatures", "/candidatures/{{candidature_mehdi}}/decision",
            erreur(422, "DONNEES_INVALIDES"), corps={"decision": "DEMANDER_COMPLEMENT"}, cle="portail"),
    requete("R11 : complément demandé, la candidature reste en évaluation", "POST", "candidatures", "/candidatures/{{candidature_mehdi}}/decision",
            statut(200) + '''
pm.test("Toujours EN_COURS_EVALUATION", () => pm.expect(pm.response.json().candidature.statut).to.eql("EN_COURS_EVALUATION"));
pm.test("Étudiant notifié", () => pm.expect(pm.response.json().etapes.map(e => e.etape + ":" + e.resultat)).to.include("NOTIFICATION_RESULTAT:OK"));''',
            corps={"decision": "DEMANDER_COMPLEMENT", "commentaire": "Pouvez-vous préciser vos disponibilités à partir de février ?"}, cle="portail"),
    requete("Mailpit : la demande de complément est arrivée", "GET", "mailpit", "/search?query=to:mehdi.tazi@inpt.ac.ma", statut(200) + '''
pm.test("Courriel de complément reçu", () => pm.expect(pm.response.json().messages.map(m => m.Subject)).to.include("Complément d'information demandé : Ingénieur données – Flux logistiques"));''',
            cle=None),
    requete("La décision revient à l'entreprise : refus", "POST", "candidatures", "/candidatures/{{candidature_mehdi}}/decision", statut(200)
            + 'pm.test("REFUSEE", () => pm.expect(pm.response.json().candidature.statut).to.eql("REFUSEE"));',
            corps={"decision": "REFUSER", "commentaire": "Disponibilités incompatibles avec le calendrier du projet."}, cle="portail"),
    requete("L'historique garde la trace du complément", "GET", "candidatures", "/candidatures/{{candidature_mehdi}}/historique", statut(200) + '''
pm.test("Complément puis refus", () => {
  const h = pm.response.json();
  pm.expect(h.some(x => x.commentaire && x.commentaire.startsWith("Demande de complément"))).to.be.true;
  pm.expect(h[h.length - 1].nouveauStatut).to.eql("REFUSEE");
});'''),
]))

# 8. S4 erreurs et consultation
ITEMS.append(dossier("8. S4 : validation, consultation et erreurs", "", [
    requete("Dépôt incomplet : 422 avec chaque champ", "POST", "candidatures", "/soumissions", erreur(422, "DONNEES_INVALIDES")
            + 'pm.test("Deux champs en cause", () => pm.expect(pm.response.json().details).to.have.lengthOf(2));', corps={"etudiantId": 1}, cle="portail"),
    requete("JSON mal typé : 400", "POST", "candidatures", "/soumissions", erreur(400, "REQUETE_INVALIDE"),
            corps={"etudiantId": "un", "offreId": 1, "lettreMotivation": LETTRE}, cle="portail"),
    requete("Champ inconnu : 400", "POST", "candidatures", "/soumissions", erreur(400, "REQUETE_INVALIDE")
            + 'pm.test("Le champ est nommé", () => pm.expect(pm.response.json().details[0]).to.include("offre"));',
            corps={"etudiantId": 1, "offre": 1, "lettreMotivation": LETTRE}, cle="portail"),
    requete("Candidature inconnue : 404", "GET", "candidatures", "/candidatures/99999", erreur(404, "CANDIDATURE_INTROUVABLE")),
    requete("Filtre de statut inconnu : 400", "GET", "candidatures", "/candidatures?statut=FINIE", erreur(400, "REQUETE_INVALIDE")),
    requete("Candidatures reçues par Zellige Conseil", "GET", "candidatures", "/entreprises/3/candidatures", statut(200)
            + 'pm.test("Toutes de l\'entreprise 3", () => pm.response.json().forEach(c => pm.expect(c.entrepriseId).to.eql(3)));'),
    requete("Étapes d'une candidature (vue BPMN)", "GET", "candidatures", "/candidatures/{{candidature_nominale}}/etapes", statut(200)
            + 'pm.test("Au moins 13 étapes", () => pm.expect(pm.response.json().length).to.be.at.least(13));'),
]))

# 9. S5
NOTIF = {"type": "DEMANDE_COMPLEMENT", "destinataire": "aicha.tahiri@inpt.ac.ma",
         "variables": {"nomEtudiant": "Aïcha Tahiri", "titreOffre": "Transformation numérique", "entreprise": "Agence Numérique du Bouregreg",
                       "commentaire": "Merci de joindre votre relevé de notes."}}
ITEMS.append(dossier("9. S5 Notifications", "Envoi réel vers Mailpit, consultation, validation, rappels de date limite.", [
    requete("Envoyer une notification : 201", "POST", "notifications", "/notifications", statut(201) + '''
pm.test("Envoyée, avec sujet et corps HTML", () => {
  const n = pm.response.json();
  pm.expect(n.statut).to.eql("ENVOYEE");
  pm.expect(n.sujet).to.eql("Complément d'information demandé : Transformation numérique");
  pm.expect(n.corps).to.include("relevé de notes");
  pm.collectionVariables.set("notification", n.id);
});''', corps=NOTIF),
    requete("Consulter la notification", "GET", "notifications", "/notifications/{{notification}}", statut(200)
            + 'pm.test("Même destinataire", () => pm.expect(pm.response.json().destinataire).to.eql("aicha.tahiri@inpt.ac.ma"));'),
    requete("Variable obligatoire manquante : 422", "POST", "notifications", "/notifications", erreur(422, "DONNEES_INVALIDES")
            + 'pm.test("Trois variables manquantes", () => pm.expect(pm.response.json().details).to.have.lengthOf(3));',
            corps={"type": "REFUS", "destinataire": "x@inpt.ac.ma", "variables": {}}),
    requete("Type inconnu : 422", "POST", "notifications", "/notifications", erreur(422, "DONNEES_INVALIDES"),
            corps={"type": "SMS", "destinataire": "x@inpt.ac.ma", "variables": {}}),
    requete("Adresse invalide : 422", "POST", "notifications", "/notifications", erreur(422, "DONNEES_INVALIDES"),
            corps=dict(NOTIF, destinataire="pas-une-adresse")),
    requete("Notification inconnue : 404", "GET", "notifications", "/notifications/99999", erreur(404, "NOTIFICATION_INTROUVABLE")),
    requete("Rappels de date limite : premier passage", "POST", "notifications", "/rappels/executer?jours=3", statut(200) + '''
pm.test("Offre 5 concernée, rappels envoyés", () => {
  const b = pm.response.json();
  pm.expect(b.offresConcernees).to.be.at.least(1);
  pm.expect(b.rappelsEnvoyes).to.be.at.least(1);
  pm.expect(b.echecs).to.eql(0);
  pm.expect(b.details.map(d => d.offreId)).to.include(5);
});
pm.test("Seuls les étudiants actifs compatibles : Omar oui, Salma (EN_STAGE depuis son acceptation) et Sara (suspendue) non", () => {
  const dest = pm.response.json().details.map(d => d.destinataire);
  pm.expect(dest).to.include("omar.bennani@inpt.ac.ma");
  pm.expect(dest).to.not.include("salma.elidrissi@inpt.ac.ma");
  pm.expect(dest).to.not.include("sara.bouzidi@inpt.ac.ma");
});''', cle="portail"),
    requete("Rappels : un second passage ne renvoie rien", "POST", "notifications", "/rappels/executer?jours=3", statut(200) + '''
pm.test("Aucun nouvel envoi, tous déjà envoyés", () => {
  const b = pm.response.json();
  pm.expect(b.rappelsEnvoyes).to.eql(0);
  pm.expect(b.dejaEnvoyes).to.be.at.least(1);
});''', cle="portail"),
    requete("Rappels : nombre de jours hors bornes : 400", "POST", "notifications", "/rappels/executer?jours=99", erreur(400, "REQUETE_INVALIDE"), cle="portail"),
]))

# 10. S6
STAGE = {"candidatureId": 900, "etudiantId": 7, "offreId": 8, "entrepriseId": 2, "dateDebut": "2027-02-01", "dateFin": "2027-05-24",
         "encadrantEntreprise": "Driss Lamrani", "encadrantPedagogique": "Pr. Nabil Essaadi"}
ITEMS.append(dossier("10. S6 Suivi des stages", "Création idempotente, statuts, comptes rendus, évaluations, historique.", [
    requete("Créer un stage : 201", "POST", "suivi", "/stages", statut(201) + '''
pm.test("PLANIFIE", () => { pm.expect(pm.response.json().statut).to.eql("PLANIFIE"); pm.collectionVariables.set("stage", pm.response.json().id); });''',
            corps=STAGE),
    requete("Rejouer la création : 200, même stage", "POST", "suivi", "/stages", statut(200)
            + 'pm.test("Même identifiant", () => pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get("stage")));', corps=STAGE),
    requete("Autre stage pour la même candidature : 409", "POST", "suivi", "/stages", erreur(409, "STAGE_EXISTANT"), corps=dict(STAGE, etudiantId=8)),
    requete("Dates incohérentes : 422", "POST", "suivi", "/stages", erreur(422, "DONNEES_INVALIDES"), corps=dict(STAGE, candidatureId=901, dateFin="2027-01-01")),
    requete("Compte rendu avant le début : 409", "POST", "suivi", "/stages/{{stage}}/comptes-rendus", erreur(409, "STAGE_NON_COMMENCE"),
            corps={"periode": "Semaine 1", "contenu": "x", "auteur": "Pr. Essaadi"}),
    requete("PLANIFIE vers TERMINE : 409", "PATCH", "suivi", "/stages/{{stage}}/statut", erreur(409, "TRANSITION_INTERDITE"), corps={"statut": "TERMINE"}),
    requete("Démarrer le stage", "PATCH", "suivi", "/stages/{{stage}}/statut", statut(200)
            + 'pm.test("EN_COURS", () => pm.expect(pm.response.json().statut).to.eql("EN_COURS"));', corps={"statut": "EN_COURS", "commentaire": "Arrivée dans l'entreprise"}),
    requete("Compte rendu périodique : 201", "POST", "suivi", "/stages/{{stage}}/comptes-rendus", statut(201)
            + 'pm.test("Période enregistrée", () => pm.expect(pm.response.json().periode).to.eql("Semaines 1 à 4"));',
            corps={"periode": "Semaines 1 à 4", "contenu": "Mise en place de la supervision des déploiements.", "auteur": "Pr. Nabil Essaadi"}),
    requete("Note hors de 0 à 20 : 422", "POST", "suivi", "/stages/{{stage}}/evaluations", erreur(422, "DONNEES_INVALIDES"),
            corps={"note": 21, "evaluateur": "Pr. Essaadi"}),
    requete("Évaluation : 201", "POST", "suivi", "/stages/{{stage}}/evaluations", statut(201)
            + 'pm.test("Note 15,5", () => pm.expect(pm.response.json().note).to.eql(15.5));',
            corps={"note": 15.5, "commentaire": "Travail rigoureux et autonome.", "evaluateur": "Pr. Nabil Essaadi"}),
    requete("Historique du stage", "GET", "suivi", "/stages/{{stage}}/historique", statut(200) + '''
pm.test("Création, statut, compte rendu, évaluation", () => pm.expect(pm.response.json().map(o => o.operation)).to.eql(["CREATION", "CHANGEMENT_STATUT", "COMPTE_RENDU", "EVALUATION"]));'''),
    requete("Historique des stages d'un étudiant", "GET", "suivi", "/etudiants/18/stages", statut(200)
            + 'pm.test("Un stage terminé", () => { pm.expect(pm.response.json()).to.have.lengthOf(1); pm.expect(pm.response.json()[0].statut).to.eql("TERMINE"); });'),
    requete("Stages en cours", "GET", "suivi", "/stages?statut=EN_COURS", statut(200)
            + 'pm.test("Tous EN_COURS", () => pm.response.json().forEach(s => pm.expect(s.statut).to.eql("EN_COURS")));'),
    requete("Stage inconnu : 404", "GET", "suivi", "/stages/99999", erreur(404, "STAGE_INTROUVABLE")),
]))

COLLECTION = {
    "info": {"_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "inpt-stages-soa")), "name": "Stages SOA - services REST",
             "description": "Tests des services REST S4, S5 et S6. À jouer dans l'ordre sur des données fraîches : scripts\\tests-postman.ps1.",
             "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
    "item": ITEMS,
    "variable": [{"key": "candidature_nominale", "value": ""}, {"key": "notification", "value": ""}, {"key": "stage", "value": ""}],
}

ENVIRONNEMENT = {
    "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "inpt-stages-soa-local")), "name": "Stages SOA - local",
    "values": [{"key": k, "value": v, "enabled": True} for k, v in [
        ("url_candidatures", "http://localhost:8084/api/v1"), ("url_notifications", "http://localhost:8085/api/v1"),
        ("url_suivi", "http://localhost:8086/api/v1"), ("url_mailpit", "http://localhost:8025/api/v1"),
        ("cle_postman", "cle-postman-2026-Rb3WsE"), ("cle_portail", "cle-portail-2026-7Hq2Vd"), ("cle_bonita", "cle-bonita-2026-Xp4MzN")]],
}

if __name__ == "__main__":
    with open(os.path.join(ICI, "Stages-SOA.postman_collection.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(COLLECTION, f, ensure_ascii=False, indent=2)
    with open(os.path.join(ICI, "Stages-SOA-local.postman_environment.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(ENVIRONNEMENT, f, ensure_ascii=False, indent=2)
    n = sum(len(d["item"]) for d in ITEMS)
    print(f"{len(ITEMS)} dossiers, {n} requêtes")
