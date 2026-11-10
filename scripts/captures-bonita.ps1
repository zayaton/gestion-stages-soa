# Captures du portail Bonita pour le rapport (rapport\figures\bonita-*.png), sur de vrais cas du processus.
# Redéploie le processus (aucun ancien cas), réinitialise les bases, démarre la pile en mode bonita, joue trois cas
# (acceptation, attente de décision, panne de S3) puis capture les listes du portail d'administration de Bonita.
# Prérequis : Bonita Studio ouvert ; Playwright installé dans tests\ui (scripts\captures-ui.ps1 l'installe).
. "$PSScriptRoot\env.ps1"
try {
    & "$PSScriptRoot\deployer-bonita.ps1"
    if ($LASTEXITCODE -ne 0) { throw 'Le déploiement du processus a échoué.' }
    & "$PSScriptRoot\reset-db.ps1" | Out-Null
    & "$PSScriptRoot\start-all.ps1" -Orchestration bonita *> $null
    if ((Get-ModeOrchestration) -ne 'bonita') { throw 'S4 ne tourne pas en mode bonita.' }
    Push-Location -LiteralPath (Join-Path $RacineProjet 'tests\ui')
    try {
        & node captures-bonita.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Les captures Bonita ont échoué (détail ci-dessus).' }
    } finally { Pop-Location }
    Write-Host 'Les données de démonstration ont changé : .\scripts\reset-db.ps1 avant une démonstration.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
