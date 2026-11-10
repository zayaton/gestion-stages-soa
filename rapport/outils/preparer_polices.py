"""Prépare les polices du rapport à partir de celles de l'interface (mêmes familles : Source Serif 4 et Public Sans).

Les fichiers woff2 du portail (sous-ensembles latin et latin-ext) sont décompressés et fusionnés en un TTF par style,
lisible par XeLaTeX (fontspec). Résultat dans rapport/polices/, avec les licences OFL.
Prérequis : pip install fonttools brotli
Usage : python rapport/outils/preparer_polices.py
"""
import shutil
from pathlib import Path

from fontTools.merge import Merger
from fontTools.ttLib import TTFont

RACINE = Path(__file__).resolve().parents[2]
SOURCE = RACINE / "portal-gateway/src/main/resources/public/fonts"
SORTIE = RACINE / "rapport/polices"

STYLES = {
    "SourceSerif4-Regular": "source-serif-4-{}-400-normal",
    "SourceSerif4-SemiBold": "source-serif-4-{}-600-normal",
    "SourceSerif4-Bold": "source-serif-4-{}-700-normal",
    "SourceSerif4-Italic": "source-serif-4-{}-400-italic",
    "PublicSans-Regular": "public-sans-{}-400-normal",
    "PublicSans-Medium": "public-sans-{}-500-normal",
    "PublicSans-SemiBold": "public-sans-{}-600-normal",
    "PublicSans-Bold": "public-sans-{}-700-normal",
}


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    temporaire = SORTIE / "_tmp"
    temporaire.mkdir(exist_ok=True)
    for nom, motif in STYLES.items():
        morceaux = []
        for sous_ensemble in ("latin", "latin-ext"):
            police = TTFont(SOURCE / (motif.format(sous_ensemble) + ".woff2"))
            police.flavor = None
            chemin = temporaire / f"{nom}-{sous_ensemble}.ttf"
            police.save(chemin)
            morceaux.append(str(chemin))
        fusion = Merger().merge(morceaux)
        fusion.save(SORTIE / f"{nom}.ttf")
        print(f"  {nom}.ttf ({len(fusion.getBestCmap())} caractères)")
    shutil.rmtree(temporaire)
    for licence in SOURCE.glob("LICENCE-*.txt"):
        shutil.copy(licence, SORTIE / licence.name)


if __name__ == "__main__":
    main()
