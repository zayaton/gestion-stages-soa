"""Génère les tableaux de référence du rapport à partir des contrats du dépôt.

- SOAP : un tableau par service (opération, entrée, sortie, fautes attendues), lu dans les WSDL de contracts/.
- REST : un tableau par service (méthode, URI, paramètres, corps, réponses), lu dans les openapi.yaml.
- Un extrait du WSDL de S1 (opération verifierEtudiant) pour le chapitre SOAP.

Les tableaux ne sont jamais écrits à la main : relancer ce script après toute modification d'un contrat.
Usage : python rapport/outils/generer_tables.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "rapport" / "generes"
NS = {"wsdl": "http://schemas.xmlsoap.org/wsdl/", "xs": "http://www.w3.org/2001/XMLSchema"}

SOAP = [
    ("etudiants", "S1 Étudiants", "8081"),
    ("entreprises", "S2 Entreprises", "8082"),
    ("offres", "S3 Offres", "8083"),
]
REST = [
    ("rest-candidatures", "candidatures", "S4 Candidatures"),
    ("rest-notifications", "notifications", "S5 Notifications"),
    ("rest-suivi", "suivi", "S6 Suivi des stages"),
]


def tex(texte):
    """Échappe un texte pour LaTeX."""
    if texte is None:
        return ""
    remplacements = {
        "\\": r"\textbackslash{}", "&": r"\&", "%": r"\%", "$": r"\$", "#": r"\#", "_": r"\_",
        "{": r"\{", "}": r"\}", "~": r"\textasciitilde{}", "^": r"\textasciicircum{}",
        "→": r"$\rightarrow$", "−": "-", "≥": r"$\geq$", "≤": r"$\leq$",
    }
    return "".join(remplacements.get(c, c) for c in str(texte))


def code(texte):
    """Texte en police à chasse fixe, coupable aux barres obliques et aux points."""
    t = tex(texte)
    t = t.replace("/", r"/\allowbreak{}").replace(".", r".\allowbreak{}")
    return r"\texttt{" + t + "}"


# ---------------------------------------------------------------------------------------------------------------
# SOAP


def type_lisible(element):
    t = element.get("type", "")
    if not t:
        # Type anonyme (énumération déclarée sur place) : on donne la liste des valeurs.
        valeurs = [e.get("value") for e in element.iter(f"{{{NS['xs']}}}enumeration")]
        t = " | ".join(valeurs) if valeurs else "complexe"
    t = t.split(":")[-1] if "|" not in t else t
    if element.get("maxOccurs") == "unbounded":
        t += "[]"
    if element.get("minOccurs") == "0":
        t += ", facultatif"
    return t


def champs(schema, nom_element):
    el = schema.find(f"xs:element[@name='{nom_element}']", NS)
    if el is None:
        return []
    return [(e.get("name"), type_lisible(e)) for e in el.iter(f"{{{NS['xs']}}}element") if e is not el]


def tableau_soap(fichier, titre, port):
    racine = ET.parse(fichier).getroot()
    schema = racine.find("wsdl:types/xs:schema", NS)
    port_type = racine.find("wsdl:portType", NS)
    lignes = []
    for op in port_type.findall("wsdl:operation", NS):
        nom = op.get("name")
        doc = (op.findtext("wsdl:documentation", "", NS) or "").strip()
        attendues = re.search(r"Fautes attendues\s*:\s*(.*?)\.?\s*$", doc)
        fautes = [f.strip() for f in attendues.group(1).split(",")] if attendues else []
        fautes = [f for f in fautes if f and f.lower() != "aucune"]
        description = re.sub(r"\s*Fautes attendues\s*:.*$", "", doc).strip()
        entree = champs(schema, nom)
        sortie = champs(schema, nom + "Response")
        fmt = lambda c: r" \newline ".join(code(n) + " : " + tex(t) for n, t in c) or "–"
        lignes.append(
            r"\textbf{" + code(nom) + r"}\newline {\footnotesize " + tex(description) + "}"
            + " & " + fmt(entree)
            + " & " + fmt(sortie)
            + " & " + (r" \newline ".join(code(f.replace("Fault", "")) for f in fautes) or "–")
            + r" \\ \midrule"
        )
    nb = len(lignes)
    corps = "\n".join(lignes).removesuffix(r" \midrule")
    return nb, rf"""% Généré par rapport/outils/generer_tables.py à partir de contracts/src/main/resources/wsdl/{fichier.name}. Ne pas modifier.
\begin{{longtable}}{{@{{}}>{{\raggedright\arraybackslash}}p{{0.27\linewidth}}>{{\raggedright\arraybackslash}}p{{0.22\linewidth}}>{{\raggedright\arraybackslash}}p{{0.21\linewidth}}>{{\raggedright\arraybackslash\footnotesize}}p{{0.22\linewidth}}@{{}}}}
\caption{{Opérations SOAP de {tex(titre)} (port {port}, {nb} opérations)}}\label{{tab:soap-{fichier.stem}}}\\
\toprule \textbf{{Opération}} & \textbf{{Entrée}} & \textbf{{Sortie}} & \textbf{{Fautes métier}} \\ \midrule
\endfirsthead
\toprule \textbf{{Opération}} & \textbf{{Entrée}} & \textbf{{Sortie}} & \textbf{{Fautes métier}} \\ \midrule
\endhead
\bottomrule
\endlastfoot
{corps}
\end{{longtable}}
"""


def extrait_wsdl():
    """Extrait du WSDL de S1 : messages, type de réponse et opération verifierEtudiant."""
    lignes = (RACINE / "contracts/src/main/resources/wsdl/etudiants.wsdl").read_text(encoding="utf-8").splitlines()
    def bloc(debut, fin_motif):
        i = next(n for n, l in enumerate(lignes) if debut in l)
        j = next(n for n in range(i, len(lignes)) if re.search(fin_motif, lignes[n]))
        largeur = len(lignes[i]) - len(lignes[i].lstrip())
        return [l[largeur:] for l in lignes[i:j + 1]]
    morceaux = [
        "<!-- Type de la réponse (extrait de wsdl:types) -->",
        *bloc('<xs:complexType name="VerificationEtudiant">', r"^\s*</xs:complexType>"),
        "",
        "<!-- Opération dans le portType -->",
        *bloc('<wsdl:operation name="verifierEtudiant">', r"^\s*</wsdl:operation>"),
    ]
    # Les annotations alourdissent l'extrait : on les retire.
    texte = "\n".join(morceaux)
    texte = re.sub(r"\n\s*<xs:annotation>.*?</xs:annotation>", "", texte, flags=re.S)
    texte = re.sub(r"(<xs:element[^>]*?)>\s*</xs:element>", r"\1/>", texte)
    return texte + "\n"


# ---------------------------------------------------------------------------------------------------------------
# REST


def resoudre(contrat, objet):
    while isinstance(objet, dict) and "$ref" in objet:
        chemin = objet["$ref"].lstrip("#/").split("/")
        objet = contrat
        for morceau in chemin:
            objet = objet[morceau]
    return objet


def tableau_rest(dossier, nom, titre):
    contrat = yaml.safe_load((RACINE / dossier / "src/main/resources/openapi.yaml").read_text(encoding="utf-8"))
    lignes = []
    for chemin, operations in contrat["paths"].items():
        communs = operations.get("parameters", [])
        for methode, op in operations.items():
            if methode not in ("get", "post", "put", "patch", "delete"):
                continue
            params = []
            for p in communs + op.get("parameters", []):
                p = resoudre(contrat, p)
                if p.get("in") == "header":
                    continue
                params.append(p["name"] + ("" if p.get("required") or p.get("in") == "path" else "?"))
            corps = "–"
            if "requestBody" in op:
                schema = op["requestBody"]["content"].get("application/json", {}).get("schema", {})
                corps = schema.get("$ref", "").split("/")[-1] or "JSON"
            codes = sorted(op.get("responses", {}).keys())
            uri = chemin.replace("/api/v1", "")
            lignes.append(
                r"\textsc{" + methode.lower() + "} & "
                + code(uri) + r"\newline{\footnotesize " + tex(op.get("summary", "")) + "}"
                + " & " + (", ".join(code(p) for p in params) or "–")
                + " & " + (code(corps) if corps != "–" else "–")
                + " & " + ", ".join(codes)
                + r" \\ \midrule"
            )
    nb = len(lignes)
    corps = "\n".join(lignes).removesuffix(r" \midrule")
    return nb, rf"""% Généré par rapport/outils/generer_tables.py à partir de {dossier}/src/main/resources/openapi.yaml. Ne pas modifier.
\begin{{longtable}}{{@{{}}>{{\raggedright\arraybackslash}}p{{0.06\linewidth}}>{{\raggedright\arraybackslash}}p{{0.40\linewidth}}>{{\raggedright\arraybackslash}}p{{0.16\linewidth}}>{{\raggedright\arraybackslash}}p{{0.14\linewidth}}>{{\raggedright\arraybackslash}}p{{0.12\linewidth}}@{{}}}}
\caption{{Points d'accès REST de {tex(titre)} (base \texttt{{/api/v1}}, {nb} opérations)}}\label{{tab:rest-{nom}}}\\
\toprule \textbf{{Verbe}} & \textbf{{URI et rôle}} & \textbf{{Paramètres}} & \textbf{{Corps}} & \textbf{{Codes}} \\ \midrule
\endfirsthead
\toprule \textbf{{Verbe}} & \textbf{{URI et rôle}} & \textbf{{Paramètres}} & \textbf{{Corps}} & \textbf{{Codes}} \\ \midrule
\endhead
\bottomrule
\endlastfoot
{corps}
\end{{longtable}}
"""


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    bilan = []
    for stem, titre, port in SOAP:
        nb, contenu = tableau_soap(RACINE / "contracts/src/main/resources/wsdl" / f"{stem}.wsdl", titre, port)
        (SORTIE / f"soap-{stem}.tex").write_text(contenu, encoding="utf-8")
        bilan.append(f"{titre} : {nb} opérations SOAP")
    for dossier, nom, titre in REST:
        nb, contenu = tableau_rest(dossier, nom, titre)
        (SORTIE / f"rest-{nom}.tex").write_text(contenu, encoding="utf-8")
        bilan.append(f"{titre} : {nb} opérations REST")
    (SORTIE / "extrait-wsdl-etudiants.xml").write_text(extrait_wsdl(), encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(bilan))


if __name__ == "__main__":
    sys.exit(main())
