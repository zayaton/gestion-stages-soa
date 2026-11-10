# Compile le rapport (rapport\rapport.pdf) avec XeLaTeX, en deux passes (sommaire, listes, renvois).
# -Regenerer : régénère d'abord les tableaux tirés des contrats (generer_tables.py) et des rapports de test
#              (resultats_tests.py), ce qui suppose les tests joués sur des données fraîches.
# Prérequis : MiKTeX (ou TeX Live) avec XeLaTeX. Sur Overleaf : compilateur XeLaTeX, fichier principal rapport.tex.
param([switch]$Regenerer)
$ici = $PSScriptRoot
try {
    if (-not (Get-Command xelatex -ErrorAction SilentlyContinue)) {
        throw 'XeLaTeX est introuvable. Installez MiKTeX (https://miktex.org/download) ou compilez sur Overleaf (compilateur XeLaTeX).'
    }
    if ($Regenerer) {
        & python (Join-Path $ici 'outils\generer_tables.py'); if ($LASTEXITCODE -ne 0) { throw 'generer_tables.py a échoué.' }
        & python (Join-Path $ici 'outils\resultats_tests.py'); if ($LASTEXITCODE -ne 0) { throw 'resultats_tests.py a échoué.' }
    }
    Push-Location -LiteralPath $ici
    try {
        foreach ($passe in 1, 2) {
            Write-Host "== XeLaTeX, passe $passe =="
            # MiKTeX écrit des avertissements de mise à jour sur stderr : ignorés, seul le code de sortie compte.
            & xelatex -interaction=nonstopmode -halt-on-error rapport.tex 2>$null | Out-Null
            if ($LASTEXITCODE -ne 0) {
                Get-Content rapport.log -Encoding UTF8 | Select-String -Pattern '^!' -Context 0,4 | Select-Object -First 5 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
                throw "La compilation a échoué (détail : rapport\rapport.log)."
            }
        }
    } finally { Pop-Location }
    $pages = (Select-String -Path (Join-Path $ici 'rapport.log') -Pattern 'Output written on .* \((\d+) pages?' | Select-Object -Last 1).Matches.Groups[1].Value
    Write-Host "Rapport compilé : rapport\rapport.pdf ($pages pages)."
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
