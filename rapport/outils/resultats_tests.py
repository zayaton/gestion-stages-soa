"""Reporte dans le rapport les résultats réels des tests, lus dans les rapports produits par les scripts.

Sources (à produire avant, sur des données fraîches) :
  - JUnit : */target/surefire-reports et failsafe-reports      (.\\scripts\\build.ps1 clean verify)
  - SoapUI : tests/soapui/rapports/*/sortie.txt               (.\\scripts\\tests-soapui.ps1)
  - Postman : tests/postman/rapports/newman-junit.xml         (.\\scripts\\tests-postman.ps1)
  - Bout en bout : tests/e2e/rapports/scenarios.txt, panne.txt (.\\scripts\\test-bout-en-bout.ps1)
Le script refuse d'écrire un tableau si une source manque ou contient un échec : le rapport ne montre que des
résultats obtenus. Usage : python rapport/outils/resultats_tests.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "rapport" / "generes"

MODULES = [
    ("common", "Socle commun (configuration, sécurité, erreurs)"),
    ("soap-etudiants", "S1 Étudiants (SOAP)"),
    ("soap-entreprises", "S2 Entreprises (SOAP)"),
    ("soap-offres", "S3 Offres (SOAP)"),
    ("rest-candidatures", "S4 Candidatures (REST)"),
    ("rest-notifications", "S5 Notifications (REST)"),
    ("rest-suivi", "S6 Suivi des stages (REST)"),
    ("portal-gateway", "Portail"),
]

# Scénarios du rapport : (scénario, résultat attendu, motif des vérifications du test de bout en bout qui le prouvent)
SCENARIOS = [
    ("Parcours nominal (Salma, offre 1)", "Candidature enregistrée, entreprise notifiée, acceptation, stage créé, étudiante EN\\_STAGE",
     r"^(Dépôt : 202|Acceptation :|R10 :|Mailpit : (confirmation|acceptation|l'entreprise))"),
    ("R1 : étudiant suspendu ou en stage", "Rejet R1, courriel de rejet", r"^R1 :"),
    ("R2 : offre en brouillon, entreprise inactive", "Rejet R2, courriel de rejet", r"^R2 :"),
    ("R3 : date limite dépassée", "Rejet R3, courriel de rejet", r"^R3 :"),
    ("R4 : plus aucune place", "Rejet R4, courriel de rejet", r"^R4 :"),
    ("R5 : candidature en double", "Rejet R5, courriel de rejet", r"^R5 :"),
    ("R6 : cinq candidatures actives", "Rejet R6, courriel de rejet", r"^R6 :"),
    ("R7 : niveau ou filière incompatible", "Rejet R7, courriel de rejet", r"^R7 :"),
    ("R8 : annulation", "Refusée (409) pendant l'évaluation, acceptée (204) si déposée", r"^(R8 :|Youssef annule|Elle apparaît ANNULEE)"),
    ("R11 : demande de complément", "Candidature EN\\_COURS\\_EVALUATION, étudiant notifié, nouvelle décision possible",
     r"^(R11 :|La décision revient|L'étudiant reçoit la réponse)"),
    ("Masquage des coordonnées", "L'entreprise ne reçoit ni courriel ni téléphone complets", r"(masqué|aucune coordonnée)"),
    ("Contrôle d'accès", "401 sans jeton, 403 hors de son espace, 404 sur le dossier d'un autre", r"(: 401|: 403|: 404)"),
    ("Rappels de date limite", "Courriel aux étudiants actifs compatibles", r"^(Rappels|Mailpit : un étudiant compatible)"),
    ("Panne de S3", "Soumission en ERREUR, réponse 503, responsable alerté", r".", "panne"),
]


def tex(t):
    return (t.replace("\\", r"\textbackslash{}").replace("&", r"\&").replace("%", r"\%").replace("_", r"\_")
            .replace("#", r"\#").replace("{", r"\{").replace("}", r"\}"))


def echec(message):
    sys.exit(f"ÉCHEC : {message}")


def compter_junit(dossier):
    total = echecs = 0
    for f in dossier.glob("TEST-*.xml"):
        r = ET.parse(f).getroot()
        total += int(r.get("tests", 0))
        echecs += int(r.get("failures", 0)) + int(r.get("errors", 0))
    return total, echecs


def junit():
    lignes, tu, ti = [], 0, 0
    for module, nom in MODULES:
        u, eu = compter_junit(RACINE / module / "target" / "surefire-reports")
        i, ei = compter_junit(RACINE / module / "target" / "failsafe-reports")
        if eu or ei:
            echec(f"{module} : {eu + ei} test(s) en échec")
        if u == 0:
            echec(f"{module} : aucun rapport JUnit (lancer .\\scripts\\build.ps1 clean verify)")
        tu, ti = tu + u, ti + i
        lignes.append(f"{tex(nom)} & {u} & {i if i else '–'} & 0 \\\\")
    lignes.append(r"\midrule")
    lignes.append(rf"\textbf{{Total}} & \textbf{{{tu}}} & \textbf{{{ti}}} & \textbf{{0}} \\")
    return tu + ti, ti, "\n".join(lignes)


def soapui():
    lignes, cas, assertions = [], 0, 0
    noms = {"S1": "S1 Étudiants", "S2": "S2 Entreprises", "S3": "S3 Offres", "Panne_S1": "Panne : S1 arrêté",
            "Panne_S3": "Panne : S3 arrêté", "Panne_base": "Panne : MySQL arrêté"}
    dossiers = sorted((RACINE / "tests/soapui/rapports").glob("*/sortie.txt"))
    if len(dossiers) < 6:
        echec("rapports SoapUI incomplets (lancer .\\scripts\\tests-soapui.ps1)")
    for f in dossiers:
        texte = f.read_text(encoding="utf-8", errors="replace")
        tc = re.findall(r"Total TestCases: (\d+) \((\d+) failed\)", texte)
        ra = re.findall(r"Total Request Assertions: (\d+)", texte)
        fa = re.findall(r"Total Failed Assertions: (\d+)", texte)
        if not (tc and ra and fa):
            echec(f"résumé SoapUI introuvable dans {f}")
        if int(tc[-1][1]) or int(fa[-1]):
            echec(f"échec SoapUI dans {f}")
        nom = next(v for k, v in noms.items() if f.parent.name.startswith(k))
        cas, assertions = cas + int(tc[-1][0]), assertions + int(ra[-1])
        lignes.append(f"{tex(nom)} & {tc[-1][0]} & {ra[-1]} & 0 \\\\")
    ordre = list(noms.values())
    lignes.sort(key=lambda l: ordre.index(l.split(" & ")[0].replace("\\_", "_")))
    lignes.append(r"\midrule")
    lignes.append(rf"\textbf{{Total}} & \textbf{{{cas}}} & \textbf{{{assertions}}} & \textbf{{0}} \\")
    return assertions, "\n".join(lignes)


def postman():
    r = ET.parse(RACINE / "tests/postman/rapports/newman-junit.xml").getroot()
    suites = r.findall("testsuite")
    tests = sum(int(s.get("tests", 0)) for s in suites)
    echecs = sum(int(s.get("failures", 0)) + int(s.get("errors", 0)) for s in suites)
    if echecs:
        echec(f"Postman : {echecs} échec(s)")
    texte = (RACINE / "tests/postman/rapports/newman.txt").read_text(encoding="utf-8", errors="replace")
    requetes = re.search(r"requests\s*│\s*(\d+)\s*│\s*(\d+)", texte)
    return len(suites), int(requetes.group(1)), tests


def bout_en_bout():
    verifs = {}
    for nom in ("scenarios", "panne"):
        texte = (RACINE / f"tests/e2e/rapports/{nom}.txt").read_text(encoding="utf-8", errors="replace")
        lignes = re.findall(r"^\s+(OK|ÉCHEC|ECHEC)\s+(.*)$", texte, re.M)
        if not lignes or any(r != "OK" for r, _ in lignes):
            echec(f"bout en bout ({nom}) : vérification en échec ou absente")
        mode = re.search(r"orchestration (\w+)", texte)
        verifs[nom] = ([l for _, l in lignes], mode.group(1) if mode else "?")
    tableau = []
    for scenario in SCENARIOS:
        nom, attendu, motif = scenario[:3]
        source = scenario[3] if len(scenario) > 3 else "scenarios"
        trouvees = [l for l in verifs[source][0] if re.search(motif, l)]
        if not trouvees:
            echec(f"aucune vérification ne couvre le scénario « {nom} »")
        tableau.append(f"{tex(nom)} & {attendu} & Conforme ({len(trouvees)} vérif.) \\\\ \\midrule")
    total = sum(len(v[0]) for v in verifs.values())
    return total, verifs["scenarios"][1], "\n".join(tableau).removesuffix(" \\midrule")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    SORTIE.mkdir(parents=True, exist_ok=True)
    nb_junit, nb_it, t_junit = junit()
    nb_soapui, t_soapui = soapui()
    nb_requetes_dossiers, nb_requetes, nb_assertions = postman()
    nb_e2e, mode_e2e, t_scenarios = bout_en_bout()
    (SORTIE / "tests-junit.tex").write_text(t_junit + "\n", encoding="utf-8")
    (SORTIE / "tests-soapui.tex").write_text(t_soapui + "\n", encoding="utf-8")
    (SORTIE / "tests-scenarios.tex").write_text(t_scenarios + "\n", encoding="utf-8")
    (SORTIE / "tests-chiffres.tex").write_text(
        "% Généré par rapport/outils/resultats_tests.py à partir des rapports de test. Ne pas modifier.\n"
        f"\\newcommand{{\\nbJunit}}{{{nb_junit}}}\n\\newcommand{{\\nbJunitIT}}{{{nb_it}}}\n"
        f"\\newcommand{{\\nbSoapUI}}{{{nb_soapui}}}\n\\newcommand{{\\nbPostmanRequetes}}{{{nb_requetes}}}\n"
        f"\\newcommand{{\\nbPostman}}{{{nb_assertions}}}\n\\newcommand{{\\nbBoutEnBout}}{{{nb_e2e}}}\n"
        f"\\newcommand{{\\modeBoutEnBout}}{{{mode_e2e}}}\n", encoding="utf-8")
    print(f"JUnit {nb_junit} (dont {nb_it} sur MySQL), SoapUI {nb_soapui} assertions, Postman {nb_assertions} assertions "
          f"sur {nb_requetes} requêtes, bout en bout {nb_e2e} vérifications (mode {mode_e2e}).")


if __name__ == "__main__":
    main()
