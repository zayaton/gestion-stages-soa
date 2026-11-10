"""Enregistre de vrais échanges avec les services pour les exemples du rapport.

Prérequis : bases réinitialisées et pile démarrée en mode direct
  .\\scripts\\reset-db.ps1 ; .\\scripts\\start-all.ps1 -Orchestration direct
Usage : python rapport/outils/capturer_exemples.py

Chaque fichier de rapport/generes/exemples/ contient la requête envoyée et la réponse reçue, sans retouche
autre que la mise en forme (indentation) et le raccourcissement des corps HTML de courriel. Le script s'arrête
si un résultat ne correspond pas à ce que le rapport décrit (statut HTTP, règle de rejet…).
"""
import json
import re
import sys
import urllib.error
import urllib.request
import xml.dom.minidom
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "rapport" / "generes" / "exemples"
CLE = "cle-postman-2026-Rb3WsE"
CORRELATION = "rapport-0001"
# Identifiant propre au dossier suivi de bout en bout (dépôt, évaluation, décision) : la trace ne montre que lui.
# Unique à chaque exécution : les journaux survivent à reset-db.ps1, une exécution précédente ne doit pas s'y mêler.
DOSSIER = "dossier-" + __import__("datetime").datetime.now().strftime("%Y%m%d-%H%M%S")

S1 = "http://localhost:8081/ws/etudiants"
S4 = "http://localhost:8084/api/v1"
S5 = "http://localhost:8085/api/v1"
S6 = "http://localhost:8086/api/v1"


def appel(methode, url, corps=None, entetes=None):
    donnees = None
    entetes = dict(entetes or {})
    if corps is not None and not isinstance(corps, (bytes, str)):
        donnees = json.dumps(corps, ensure_ascii=False).encode("utf-8")
        entetes.setdefault("Content-Type", "application/json")
    elif corps is not None:
        donnees = corps.encode("utf-8") if isinstance(corps, str) else corps
    requete = urllib.request.Request(url, data=donnees, method=methode, headers=entetes)
    try:
        with urllib.request.urlopen(requete, timeout=30) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def _compact(valeur, niveau=0):
    """JSON indenté, mais chaque objet ne contenant que des valeurs simples tient sur une ligne (lisible dans le rapport)."""
    marge = "  " * niveau
    simple = lambda v: not isinstance(v, (dict, list))
    if isinstance(valeur, dict):
        if all(simple(v) for v in valeur.values()) and niveau > 0:
            return json.dumps(valeur, ensure_ascii=False)
        lignes = [f'{marge}  {json.dumps(k, ensure_ascii=False)}: {_compact(v, niveau + 1)}' for k, v in valeur.items()]
        return "{\n" + ",\n".join(lignes) + f"\n{marge}}}"
    if isinstance(valeur, list):
        if not valeur or all(simple(v) for v in valeur):
            return json.dumps(valeur, ensure_ascii=False)
        return "[\n" + ",\n".join(f"{marge}  {_compact(v, niveau + 1)}" for v in valeur) + f"\n{marge}]"
    return json.dumps(valeur, ensure_ascii=False)


def json_joli(texte):
    return _compact(json.loads(texte)) if texte else ""


def xml_joli(texte):
    joli = xml.dom.minidom.parseString(texte.encode("utf-8")).toprettyxml(indent="  ")
    return "\n".join(l for l in joli.splitlines() if l.strip() and not l.startswith("<?xml"))


def exiger(condition, message):
    if not condition:
        sys.exit(f"ÉCHEC : {message}")


def ecrire(nom, contenu):
    (SORTIE / nom).write_text(contenu.rstrip() + "\n", encoding="utf-8")
    print(f"  {nom}")


def rest(nom, methode, chemin_affiche, url, corps=None, cle=True, attendu=None, acteur=None, correlation=CORRELATION):
    entetes = {"X-Correlation-Id": correlation}
    if cle:
        entetes["X-API-Key"] = CLE
    if acteur:
        entetes["X-Acteur"] = acteur
    statut, reponse = appel(methode, url, corps, entetes)
    if attendu is not None:
        exiger(statut == attendu, f"{methode} {chemin_affiche} : {statut} au lieu de {attendu} : {reponse[:300]}")
    lignes = [f"{methode} {chemin_affiche}"]
    lignes += [f"{k}: {v}" for k, v in entetes.items()]
    if corps is not None:
        lignes += ["", json.dumps(corps, ensure_ascii=False, indent=2)]
    lignes += ["", f"--> HTTP {statut}", json_joli(reponse)]
    ecrire(nom, "\n".join(lignes))
    return statut, (json.loads(reponse) if reponse else None)


ENTETE_WSSE = """<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>soapui</wsse:Username>
        <wsse:Password>SoapUI#Ws2026</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>"""


def soap(nom, corps_xml, securite=True):
    enveloppe = f"""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:etu="http://stages.inpt.ma/etudiants">
  <soapenv:Header>
    {ENTETE_WSSE if securite else ""}
    <cor:CorrelationId xmlns:cor="http://stages.inpt.ma/commun">{CORRELATION}</cor:CorrelationId>
  </soapenv:Header>
  <soapenv:Body>
    {corps_xml}
  </soapenv:Body>
</soapenv:Envelope>"""
    statut, reponse = appel("POST", S1, enveloppe, {"Content-Type": "text/xml; charset=utf-8", "SOAPAction": '""'})
    ecrire(nom + "-requete.xml", xml_joli(enveloppe))
    ecrire(nom + "-reponse.xml", f"<!-- HTTP {statut} -->\n" + xml_joli(reponse))
    return statut, reponse


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    SORTIE.mkdir(parents=True, exist_ok=True)
    statut, mode = appel("GET", f"{S4}/processus/mode", entetes={"X-API-Key": CLE})
    exiger(statut == 200 and json.loads(mode).get("mode") == "direct",
           "S4 doit tourner en mode direct : .\\scripts\\start-all.ps1 -Orchestration direct")

    print("SOAP (S1)")
    statut, r = soap("soap-verifier-suspendu", "<etu:verifierEtudiant><etu:id>17</etu:id></etu:verifierEtudiant>")
    exiger(statut == 200 and "<eligible>false</eligible>" in r.replace("ns2:", "").replace(" ", ""), "verifierEtudiant(17) devrait être non éligible")
    statut, r = soap("soap-consulter-inconnu", "<etu:consulterEtudiant><etu:id>9999</etu:id></etu:consulterEtudiant>")
    exiger(statut == 500 and "etudiantintrouvablefault" in r.lower(),"consulterEtudiant(9999) devrait renvoyer EtudiantIntrouvableFault")
    statut, r = soap("soap-sans-securite", "<etu:consulterEtudiant><etu:id>1</etu:id></etu:consulterEtudiant>", securite=False)
    exiger(statut == 500 and "InvalidSecurity" in r, "un appel sans WS-Security devrait être refusé")

    print("REST (S4, S5, S6)")
    _, depot = rest("rest-soumission-enregistree.txt", "POST", "/api/v1/soumissions", f"{S4}/soumissions",
                    {"etudiantId": 1, "offreId": 1, "lettreMotivation": "Étudiante en AMOA, je souhaite contribuer au recueil des besoins de votre application de paiement mobile."},
                    attendu=202, acteur="etudiant:1", correlation=DOSSIER)
    exiger(depot["statut"] == "ENREGISTREE", f"dépôt non enregistré : {depot}")
    cid = depot["candidatureId"]

    _, rejet = rest("rest-soumission-rejetee.txt", "POST", "/api/v1/soumissions", f"{S4}/soumissions",
                    {"etudiantId": 20, "offreId": 13, "lettreMotivation": "Je souhaite rejoindre votre équipe décisionnelle pour ce stage de première année."},
                    attendu=202, acteur="etudiant:20")
    exiger(rejet["statut"] == "REJETEE" and rejet["regleRejetee"] == "R3", f"rejet R3 attendu : {rejet}")

    rest("rest-candidature.txt", "GET", f"/api/v1/candidatures/{cid}", f"{S4}/candidatures/{cid}", attendu=200)
    rest("rest-erreur-404.txt", "GET", "/api/v1/candidatures/9999", f"{S4}/candidatures/9999", attendu=404)
    rest("rest-erreur-401.txt", "GET", "/api/v1/candidatures", f"{S4}/candidatures", cle=False, attendu=401)
    rest("rest-evaluation.txt", "POST", f"/api/v1/candidatures/{cid}/evaluation", f"{S4}/candidatures/{cid}/evaluation",
         attendu=200, acteur="entreprise:1", correlation=DOSSIER)
    rest("rest-erreur-409-r8.txt", "DELETE", f"/api/v1/candidatures/{cid}", f"{S4}/candidatures/{cid}", attendu=409, acteur="etudiant:1")
    rest("rest-decision.txt", "POST", f"/api/v1/candidatures/{cid}/decision", f"{S4}/candidatures/{cid}/decision",
         {"decision": "ACCEPTER", "commentaire": "Profil en adéquation avec la mission."}, attendu=200, acteur="entreprise:1", correlation=DOSSIER)
    rest("rest-stage.txt", "GET", f"/api/v1/candidatures/{cid}/stage", f"{S6}/candidatures/{cid}/stage", attendu=200)

    statut, liste = appel("GET", f"{S5}/notifications?destinataire=salma.elidrissi%40inpt.ac.ma", entetes={"X-API-Key": CLE})
    exiger(statut == 200 and len(json.loads(liste)) >= 2, "notifications de Salma attendues")
    premiere = json.loads(liste)[0]
    premiere["corps"] = premiere["corps"][:120] + " […]"
    ecrire("rest-notification.txt", "GET /api/v1/notifications?destinataire=salma.elidrissi@inpt.ac.ma\nX-API-Key: "
           + CLE + f"\n\n--> HTTP {statut} (premier élément ; corps HTML raccourci)\n" + json.dumps(premiere, ensure_ascii=False, indent=2))

    print("Trace de corrélation")
    lignes = []
    for journal in sorted((RACINE / "logs").glob("*.log")):
        if journal.name.endswith(".err.log"):
            continue
        for l in journal.read_text(encoding="utf-8", errors="replace").splitlines():
            if f"[{DOSSIER}]" in l and "Hibernate Validator" not in l:
                horodatage = l[11:23]
                message = re.sub(r"^\S+ +\S+ +\[[^\]]+\] +", "", l)
                lignes.append((l[:23], f"{horodatage} {journal.stem:<18} {message}"))
    lignes.sort()
    exiger(len({l[1].split()[1] for l in lignes}) >= 5, "la trace devrait traverser au moins cinq services")
    ecrire("trace-correlation.txt", "\n".join(l[1] for l in lignes))
    print(f"{len(lignes)} lignes de journal portent l'identifiant {DOSSIER}.")


if __name__ == "__main__":
    main()
