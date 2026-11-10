#!/usr/bin/env python3
"""Génère docs/bonita/processus-candidature.bpmn (BPMN 2.0 avec sa mise en page).

Les identifiants des nœuds sont les noms d'étapes que S4 journalise (table etape_processus) : la vue BPMN du portail
colore un nœud dès que son étape apparaît dans le journal d'une candidature. Le fichier produit est versionné ;
ce script ne sert qu'à le régénérer si le dessin change.
"""
import os
from xml.sax.saxutils import escape, quoteattr

ICI = os.path.dirname(os.path.abspath(__file__))
TACHE = (120, 60)
PASSERELLE = (50, 50)
EVENEMENT = (36, 36)

# Couloirs : (id, nom, y, hauteur)
COULOIRS = [("COULOIR_ETUDIANT", "Étudiant", 0, 180), ("COULOIR_SYSTEME", "Système", 180, 420), ("COULOIR_ENTREPRISE", "Entreprise", 600, 240)]
LARGEUR = 2820

# Nœuds : id -> (type, nom, couloir, centre x, centre y, extra)
N = {}


def noeud(id_, type_, nom, couloir, x, y, **extra):
    N[id_] = dict(type=type_, nom=nom, couloir=couloir, x=x, y=y, **extra)


A, B = 250, 380  # rangées du couloir Système
noeud("RECEPTION", "startEvent", "Candidature déposée", "COULOIR_ETUDIANT", 110, 90)
noeud("VERIFICATION_ETUDIANT", "serviceTask", "Vérification de l'étudiant", "COULOIR_SYSTEME", 260, A)
noeud("G_ETUDIANT", "exclusiveGateway", "Étudiant éligible ?", "COULOIR_SYSTEME", 410, A, defaut="F_ETUDIANT_NON", etiquette=(350, 196, 120))
noeud("VERIFICATION_OFFRE", "serviceTask", "Vérification de l'offre", "COULOIR_SYSTEME", 540, A)
noeud("G_OFFRE", "exclusiveGateway", "Offre existante et active ?", "COULOIR_SYSTEME", 690, A, defaut="F_OFFRE_NON")
noeud("VERIFICATION_ENTREPRISE", "serviceTask", "Vérification de l'entreprise et du profil", "COULOIR_SYSTEME", 830, A)
noeud("CONTROLE_CANDIDATURE", "serviceTask", "Contrôle de la candidature", "COULOIR_SYSTEME", 990, A)
noeud("PASSERELLE_DECISION", "exclusiveGateway", "Conditions satisfaites ?", "COULOIR_SYSTEME", 1140, A, defaut="F_PASSERELLE_NON", etiquette=(1030, 282, 100))
noeud("ENREGISTREMENT", "serviceTask", "Enregistrement de la candidature", "COULOIR_SYSTEME", 1280, A)
noeud("NOTIFICATION_DEPOT", "serviceTask", "Confirmation du dépôt", "COULOIR_SYSTEME", 1430, A)
noeud("TRANSMISSION_ENTREPRISE", "serviceTask", "Transmission à l'entreprise", "COULOIR_SYSTEME", 1580, A)
noeud("ENREGISTREMENT_REJET", "serviceTask", "Enregistrement du rejet", "COULOIR_SYSTEME", 1280, B)
noeud("NOTIFICATION_REJET", "serviceTask", "Notification du rejet", "COULOIR_SYSTEME", 1430, B)
noeud("FIN_REJET", "endEvent", "Candidature rejetée", "COULOIR_SYSTEME", 1560, B)
noeud("DECISION_ENTREPRISE", "userTask", "Décision de l'entreprise", "COULOIR_ENTREPRISE", 1740, 690)
noeud("RELANCE_ENTREPRISE", "serviceTask", "Relance de l'entreprise", "COULOIR_ENTREPRISE", 1620, 790)
noeud("G_DECISION", "exclusiveGateway", "Décision ?", "COULOIR_ENTREPRISE", 1890, 690, etiquette=(1898, 712, 70))
noeud("DEMANDE_COMPLEMENT", "serviceTask", "Demande de complément", "COULOIR_SYSTEME", 2030, 520)
noeud("MISE_A_JOUR_PLACES", "serviceTask", "Réservation de la place", "COULOIR_SYSTEME", 2030, A)
noeud("ENREGISTREMENT_ACCEPTATION", "serviceTask", "Enregistrement de l'acceptation", "COULOIR_SYSTEME", 2180, A)
noeud("NOTIFICATION_RESULTAT", "serviceTask", "Notification du résultat", "COULOIR_SYSTEME", 2330, A)
noeud("CREATION_STAGE", "serviceTask", "Création du dossier de stage", "COULOIR_SYSTEME", 2480, A)
noeud("MISE_A_JOUR_ETUDIANT", "serviceTask", "Mise à jour du statut de l'étudiant", "COULOIR_SYSTEME", 2630, A)
noeud("FIN_ACCEPTEE", "endEvent", "Candidature acceptée", "COULOIR_SYSTEME", 2750, A)
noeud("ENREGISTREMENT_REFUS", "serviceTask", "Enregistrement du refus", "COULOIR_SYSTEME", 2030, B)
noeud("NOTIFICATION_REFUS", "serviceTask", "Notification du refus", "COULOIR_SYSTEME", 2180, B)
noeud("FIN_REFUSEE", "endEvent", "Candidature refusée", "COULOIR_SYSTEME", 2310, B)

# Sous-processus d'erreur (événementiel), dans le couloir Système
SP = dict(id="SP_ERREUR", nom="Gestion des erreurs techniques", x=220, y=460, w=600, h=120)
SP_NOEUDS = {
    "ERREUR_TECHNIQUE": ("startEvent", "Service indisponible", 270, 520),
    "SIGNALER_ERREUR": ("serviceTask", "Signaler l'interruption", 410, 520),
    "ALERTE_RESPONSABLE": ("serviceTask", "Alerter le responsable", 570, 520),
    "FIN_ERREUR": ("endEvent", "Traitement interrompu", 720, 520),
}
MINUTEUR = dict(id="MINUTEUR_RELANCE", nom="7 jours sans décision", x=1760, y=720, etiquette=(1632, 726, 104))

# Flux : id -> (source, cible, nom, condition, points)
F = {}


def flux(id_, source, cible, points, nom=None, condition=None, etiquette=None):
    F[id_] = dict(source=source, cible=cible, nom=nom, condition=condition, points=points, etiquette=etiquette)


C = "inpt.SoaProcessus."
flux("F_RECEPTION", "RECEPTION", "VERIFICATION_ETUDIANT", [(128, 90), (260, 90), (260, 220)])
flux("F_ETUDIANT", "VERIFICATION_ETUDIANT", "G_ETUDIANT", [(320, A), (385, A)])
flux("F_ETUDIANT_OUI", "G_ETUDIANT", "VERIFICATION_OFFRE", [(435, A), (480, A)], "oui", "dossier.etudiantEligible == true", (440, 232, 30))
flux("F_ETUDIANT_NON", "G_ETUDIANT", "PASSERELLE_DECISION", [(410, 275), (410, 330), (1140, 330), (1140, 275)], "non", etiquette=(416, 292, 30))
flux("F_OFFRE", "VERIFICATION_OFFRE", "G_OFFRE", [(600, A), (665, A)])
flux("F_OFFRE_OUI", "G_OFFRE", "VERIFICATION_ENTREPRISE", [(715, A), (770, A)], "oui", C + "offreValable(dossier)", (722, 232, 30))
flux("F_OFFRE_NON", "G_OFFRE", "PASSERELLE_DECISION", [(690, 225), (690, 196), (1140, 196), (1140, 225)], "non", etiquette=(698, 200, 30))
flux("F_ENTREPRISE", "VERIFICATION_ENTREPRISE", "CONTROLE_CANDIDATURE", [(890, A), (930, A)])
flux("F_CONTROLE", "CONTROLE_CANDIDATURE", "PASSERELLE_DECISION", [(1050, A), (1115, A)])
flux("F_PASSERELLE_OUI", "PASSERELLE_DECISION", "ENREGISTREMENT", [(1165, A), (1220, A)], "oui", C + "conditionsSatisfaites(dossier)", (1170, 232, 30))
flux("F_PASSERELLE_NON", "PASSERELLE_DECISION", "ENREGISTREMENT_REJET", [(1165, A), (1190, A), (1190, B), (1220, B)], "non", etiquette=(1196, 300, 30))
flux("F_ENREGISTREMENT", "ENREGISTREMENT", "NOTIFICATION_DEPOT", [(1340, A), (1370, A)])
flux("F_DEPOT", "NOTIFICATION_DEPOT", "TRANSMISSION_ENTREPRISE", [(1490, A), (1520, A)])
flux("F_TRANSMISSION", "TRANSMISSION_ENTREPRISE", "DECISION_ENTREPRISE", [(1640, A), (1740, A), (1740, 660)])
flux("F_REJET", "ENREGISTREMENT_REJET", "NOTIFICATION_REJET", [(1340, B), (1370, B)])
flux("F_NOTIF_REJET", "NOTIFICATION_REJET", "FIN_REJET", [(1490, B), (1542, B)])
flux("F_DECISION", "DECISION_ENTREPRISE", "G_DECISION", [(1800, 690), (1865, 690)])
flux("F_ACCEPTER", "G_DECISION", "MISE_A_JOUR_PLACES", [(1890, 665), (1890, A), (1970, A)], "ACCEPTER", C + "decisionEst(dossier, 'ACCEPTER')", (1826, 560, 60))
flux("F_REFUSER", "G_DECISION", "ENREGISTREMENT_REFUS", [(1915, 690), (1940, 690), (1940, B), (1970, B)], "REFUSER", C + "decisionEst(dossier, 'REFUSER')", (1946, 610, 60))
flux("F_COMPLEMENT", "G_DECISION", "DEMANDE_COMPLEMENT", [(1890, 715), (1890, 745), (2030, 745), (2030, 550)], "DEMANDER_COMPLEMENT",
     C + "decisionEst(dossier, 'DEMANDER_COMPLEMENT')", (1900, 750, 160))
flux("F_RETOUR_DECISION", "DEMANDE_COMPLEMENT", "DECISION_ENTREPRISE", [(1970, 520), (1760, 520), (1760, 660)], "R11 : retour à la décision", etiquette=(1766, 494, 116))
flux("F_MINUTEUR", "MINUTEUR_RELANCE", "RELANCE_ENTREPRISE", [(1760, 738), (1760, 760), (1680, 790)])
flux("F_RELANCE", "RELANCE_ENTREPRISE", "DECISION_ENTREPRISE", [(1620, 760), (1620, 690), (1680, 690)])
flux("F_PLACES", "MISE_A_JOUR_PLACES", "ENREGISTREMENT_ACCEPTATION", [(2090, A), (2120, A)])
flux("F_ACCEPTATION", "ENREGISTREMENT_ACCEPTATION", "NOTIFICATION_RESULTAT", [(2240, A), (2270, A)])
flux("F_RESULTAT", "NOTIFICATION_RESULTAT", "CREATION_STAGE", [(2390, A), (2420, A)])
flux("F_STAGE", "CREATION_STAGE", "MISE_A_JOUR_ETUDIANT", [(2540, A), (2570, A)])
flux("F_EN_STAGE", "MISE_A_JOUR_ETUDIANT", "FIN_ACCEPTEE", [(2690, A), (2732, A)])
flux("F_REFUS", "ENREGISTREMENT_REFUS", "NOTIFICATION_REFUS", [(2090, B), (2120, B)])
flux("F_NOTIF_REFUS", "NOTIFICATION_REFUS", "FIN_REFUSEE", [(2240, B), (2292, B)])
F_SP = {
    "F_ERREUR_1": ("ERREUR_TECHNIQUE", "SIGNALER_ERREUR", [(288, 520), (350, 520)]),
    "F_ERREUR_2": ("SIGNALER_ERREUR", "ALERTE_RESPONSABLE", [(470, 520), (510, 520)]),
    "F_ERREUR_3": ("ALERTE_RESPONSABLE", "FIN_ERREUR", [(630, 520), (702, 520)]),
}

DOC = {
    "VERIFICATION_ETUDIANT": "Étape 2. SOAP S1 verifierEtudiant (règle R1).",
    "VERIFICATION_OFFRE": "Étape 3. SOAP S3 verifierDisponibilite (offre existante et ACTIVE, règle R2).",
    "VERIFICATION_ENTREPRISE": "Étape 3. SOAP S3 consulterOffre, SOAP S2 verifierEntreprise (R2), SOAP S3 verifierCompatibilite (R7).",
    "CONTROLE_CANDIDATURE": "Étape 4. R3 et R4 d'après S3 ; REST S4 vérification de doublon (R5) et compte des candidatures actives (R6).",
    "PASSERELLE_DECISION": "Étape 5. Passerelle de décision : candidature enregistrée si aucune règle n'est violée, sinon rejetée.",
    "ENREGISTREMENT": "Étape 5. REST S4 POST /candidatures.",
    "NOTIFICATION_DEPOT": "REST S5 : confirmation de dépôt à l'étudiant.",
    "TRANSMISSION_ENTREPRISE": "Étape 6. REST S5 : l'entreprise est informée de la nouvelle candidature.",
    "ENREGISTREMENT_REJET": "REST S4 POST /soumissions/{id}/rejet (règle et motif).",
    "NOTIFICATION_REJET": "REST S5 : rejet notifié à l'étudiant, avec le motif.",
    "DECISION_ENTREPRISE": "Étape 7. Tâche humaine, contrat {decision : ACCEPTER | REFUSER | DEMANDER_COMPLEMENT, commentaire}.",
    "RELANCE_ENTREPRISE": "REST S5 : l'entreprise est relancée ; la tâche de décision est recréée.",
    "DEMANDE_COMPLEMENT": "Règle R11. REST S5 : demande de complément à l'étudiant, puis retour à la décision.",
    "MISE_A_JOUR_PLACES": "Étape 9, règle R10. SOAP S3 decrementerPlaces (POURVUE à zéro).",
    "ENREGISTREMENT_ACCEPTATION": "REST S4 PATCH /candidatures/{id}/statut ACCEPTEE.",
    "NOTIFICATION_RESULTAT": "Étape 8. REST S5 : acceptation notifiée à l'étudiant.",
    "CREATION_STAGE": "Étape 9, règle R10. REST S6 POST /stages.",
    "MISE_A_JOUR_ETUDIANT": "Étape 9, règle R10. SOAP S1 changerStatutEtudiant EN_STAGE.",
    "ENREGISTREMENT_REFUS": "REST S4 PATCH /candidatures/{id}/statut REFUSEE.",
    "NOTIFICATION_REFUS": "Étape 8. REST S5 : refus notifié à l'étudiant.",
    "SP_ERREUR": "Tout connecteur en échec lève l'erreur SERVICE_INDISPONIBLE : S4 marque la soumission ERREUR, le responsable est alerté.",
}


def taille(type_):
    return TACHE if "Task" in type_ else PASSERELLE if "Gateway" in type_ else EVENEMENT


def entrees_sorties(id_):
    entrees = [f for f, v in F.items() if v["cible"] == id_]
    sorties = [f for f, v in F.items() if v["source"] == id_]
    return entrees, sorties


def xml():
    o = ['<?xml version="1.0" encoding="UTF-8"?>\n',
         '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" '
         'xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" '
         'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="Definitions_Candidature" targetNamespace="http://stages.inpt.ma/bpmn" '
         'exporter="generer_bpmn.py" exporterVersion="1.0">\n',
         '  <bpmn:error id="ERREUR_SERVICE" name="Service indisponible" errorCode="SERVICE_INDISPONIBLE"/>\n',
         '  <bpmn:collaboration id="Collaboration_Candidature">\n',
         '    <bpmn:participant id="POOL_CANDIDATURE" name="Traitement d\'une candidature" processRef="Candidature"/>\n',
         '  </bpmn:collaboration>\n',
         '  <bpmn:process id="Candidature" name="Candidature" isExecutable="true">\n',
         '    <bpmn:documentation>Processus « Candidature » version 1.0. Contrat d\'instanciation : soumissionId, etudiantId, offreId, lettreMotivation. '
         'Variable de processus unique : dossier (java.util.Map).</bpmn:documentation>\n',
         '    <bpmn:laneSet id="LaneSet_Candidature">\n']
    for cid, nom, _, _ in COULOIRS:
        o.append(f'      <bpmn:lane id="{cid}" name={quoteattr(nom)}>\n')
        membres = [i for i, n in N.items() if n["couloir"] == cid]
        if cid == "COULOIR_SYSTEME":
            membres.append("SP_ERREUR")
        if cid == "COULOIR_ENTREPRISE":
            membres.append(MINUTEUR["id"])
        for m in membres:
            o.append(f'        <bpmn:flowNodeRef>{m}</bpmn:flowNodeRef>\n')
        o.append('      </bpmn:lane>\n')
    o.append('    </bpmn:laneSet>\n')
    for i, n in N.items():
        entrees, sorties = entrees_sorties(i)
        attrs = f' id="{i}" name={quoteattr(n["nom"])}'
        if n.get("defaut"):
            attrs += f' default="{n["defaut"]}"'
        o.append(f'    <bpmn:{n["type"]}{attrs}>\n')
        if i in DOC:
            o.append(f'      <bpmn:documentation>{escape(DOC[i])}</bpmn:documentation>\n')
        for e in entrees:
            o.append(f'      <bpmn:incoming>{e}</bpmn:incoming>\n')
        for s in sorties:
            o.append(f'      <bpmn:outgoing>{s}</bpmn:outgoing>\n')
        o.append(f'    </bpmn:{n["type"]}>\n')
    o.append(f'    <bpmn:boundaryEvent id="{MINUTEUR["id"]}" name={quoteattr(MINUTEUR["nom"])} attachedToRef="DECISION_ENTREPRISE" cancelActivity="true">\n'
             '      <bpmn:outgoing>F_MINUTEUR</bpmn:outgoing>\n'
             '      <bpmn:timerEventDefinition id="Minuteur_7_jours">\n'
             '        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">P7D</bpmn:timeDuration>\n'
             '      </bpmn:timerEventDefinition>\n'
             '    </bpmn:boundaryEvent>\n')
    for i, f in F.items():
        nom = f' name={quoteattr(f["nom"])}' if f["nom"] else ""
        if f["condition"]:
            o.append(f'    <bpmn:sequenceFlow id="{i}"{nom} sourceRef="{f["source"]}" targetRef="{f["cible"]}">\n'
                     f'      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" language="groovy">{escape(f["condition"])}</bpmn:conditionExpression>\n'
                     '    </bpmn:sequenceFlow>\n')
        else:
            o.append(f'    <bpmn:sequenceFlow id="{i}"{nom} sourceRef="{f["source"]}" targetRef="{f["cible"]}"/>\n')
    o.append(f'    <bpmn:subProcess id="{SP["id"]}" name={quoteattr(SP["nom"])} triggeredByEvent="true">\n'
             f'      <bpmn:documentation>{escape(DOC["SP_ERREUR"])}</bpmn:documentation>\n')
    for i, (type_, nom, _, _) in SP_NOEUDS.items():
        o.append(f'      <bpmn:{type_} id="{i}" name={quoteattr(nom)}>\n')
        for f, (s, c, _) in F_SP.items():
            if c == i:
                o.append(f'        <bpmn:incoming>{f}</bpmn:incoming>\n')
            if s == i:
                o.append(f'        <bpmn:outgoing>{f}</bpmn:outgoing>\n')
        if i == "ERREUR_TECHNIQUE":
            o.append('        <bpmn:errorEventDefinition id="Capture_erreur_service" errorRef="ERREUR_SERVICE"/>\n')
        o.append(f'      </bpmn:{type_}>\n')
    for f, (s, c, _) in F_SP.items():
        o.append(f'      <bpmn:sequenceFlow id="{f}" sourceRef="{s}" targetRef="{c}"/>\n')
    o.append('    </bpmn:subProcess>\n  </bpmn:process>\n')

    # Mise en page
    o.append('  <bpmndi:BPMNDiagram id="Diagramme_Candidature">\n    <bpmndi:BPMNPlane id="Plan_Candidature" bpmnElement="Collaboration_Candidature">\n')
    hauteur = COULOIRS[-1][2] + COULOIRS[-1][3]
    o.append(f'      <bpmndi:BPMNShape id="POOL_CANDIDATURE_di" bpmnElement="POOL_CANDIDATURE" isHorizontal="true">'
             f'<dc:Bounds x="0" y="0" width="{LARGEUR}" height="{hauteur}"/></bpmndi:BPMNShape>\n')
    for cid, _, y, h in COULOIRS:
        o.append(f'      <bpmndi:BPMNShape id="{cid}_di" bpmnElement="{cid}" isHorizontal="true"><dc:Bounds x="30" y="{y}" width="{LARGEUR - 30}" height="{h}"/></bpmndi:BPMNShape>\n')

    def forme(i, type_, x, y, extra="", position=None):
        w, h = taille(type_)
        etiquette = ""
        if "Task" not in type_:
            # Position explicite pour les passerelles dont un flux part vers le bas (libellé déplacé pour ne pas le couvrir).
            ex, ey, ew = position or (x - 60, y + h // 2 + 4, 120)
            etiquette = f'<bpmndi:BPMNLabel><dc:Bounds x="{ex}" y="{ey}" width="{ew}" height="28"/></bpmndi:BPMNLabel>'
        return (f'      <bpmndi:BPMNShape id="{i}_di" bpmnElement="{i}"{extra}><dc:Bounds x="{x - w // 2}" y="{y - h // 2}" width="{w}" height="{h}"/>'
                f'{etiquette}</bpmndi:BPMNShape>\n')

    for i, n in N.items():
        o.append(forme(i, n["type"], n["x"], n["y"], ' isMarkerVisible="true"' if "Gateway" in n["type"] else "", n.get("etiquette")))
    o.append(forme(MINUTEUR["id"], "boundaryEvent", MINUTEUR["x"], MINUTEUR["y"], position=MINUTEUR["etiquette"]))
    o.append(f'      <bpmndi:BPMNShape id="SP_ERREUR_di" bpmnElement="SP_ERREUR" isExpanded="true"><dc:Bounds x="{SP["x"]}" y="{SP["y"]}" width="{SP["w"]}" height="{SP["h"]}"/></bpmndi:BPMNShape>\n')
    for i, (type_, _, x, y) in SP_NOEUDS.items():
        o.append(forme(i, type_, x, y))
    for i, f in list(F.items()) + [(k, dict(points=v[2], nom=None)) for k, v in F_SP.items()]:
        points = "".join(f'<di:waypoint x="{x}" y="{y}"/>' for x, y in f["points"])
        etiquette = ""
        if f.get("nom"):
            (x1, y1), (x2, y2) = f["points"][0], f["points"][1]
            ex, ey, ew = f.get("etiquette") or ((x1 + x2) // 2 - 30, (y1 + y2) // 2 - 18, 60)
            etiquette = f'<bpmndi:BPMNLabel><dc:Bounds x="{ex}" y="{ey}" width="{ew}" height="14"/></bpmndi:BPMNLabel>'
        o.append(f'      <bpmndi:BPMNEdge id="{i}_di" bpmnElement="{i}">{points}{etiquette}</bpmndi:BPMNEdge>\n')
    o.append('    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n</bpmn:definitions>\n')
    return "".join(o)


if __name__ == "__main__":
    with open(os.path.join(ICI, "processus-candidature.bpmn"), "w", encoding="utf-8", newline="\n") as f:
        f.write(xml())
    print(f"{len(N) + len(SP_NOEUDS) + 2} nœuds, {len(F) + len(F_SP)} flux")
