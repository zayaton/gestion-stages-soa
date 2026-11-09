# Parcours du portail dans un vrai navigateur (Playwright, Chromium) et captures d'écran pour le rapport.
# Réinitialise les bases, démarre la pile en mode direct, joue un cycle complet par l'interface (candidature,
# évaluation, acceptation), visite chaque écran des trois espaces et enregistre les captures dans rapport\figures\ui-*.png.
# Échoue si la console du navigateur affiche une erreur ou si le portail répond en 5xx.
# Navigateur : Microsoft Edge (présent sur Windows 10 et 11), piloté par Playwright ; rien à télécharger.
# -Chromium : utilise le Chromium de Playwright à la place (téléchargé une fois, environ 150 Mo).
# -SansReset : garde les données actuelles.
param([switch]$SansReset, [switch]$Chromium, [int]$DelaiSecondes = 300)
. "$PSScriptRoot\env.ps1"

$dossier = Join-Path $RacineProjet 'tests\ui'

try {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Node.js est introuvable. Installez-le depuis https://nodejs.org/ puis relancez ce script.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $dossier 'node_modules\playwright'))) {
        Write-Host '== Installation de Playwright (dans tests\ui, une seule fois) =='
        Push-Location -LiteralPath $dossier
        try {
            & npm.cmd install --no-audit --no-fund | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "L'installation de Playwright a échoué." }
        } finally { Pop-Location }
    }
    if (-not $SansReset) {
        Write-Host '== Réinitialisation des bases et de Mailpit =='
        & "$PSScriptRoot\reset-db.ps1" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'La réinitialisation des bases a échoué.' }
    }
    # Le parcours attend le résultat immédiat du dépôt : mode direct.
    & "$PSScriptRoot\start-all.ps1" -Orchestration direct *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Démarrage de la pile en mode direct impossible.' }
    $limite = (Get-Date).AddSeconds(90)
    do {
        $enAttente = @($Services | Where-Object { (Get-Sante $_.Port).Code -ne 200 })
        if ($enAttente.Count -eq 0) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $limite)
    if ($enAttente.Count -gt 0) { throw "Services non disponibles : $(($enAttente | ForEach-Object { $_.Nom }) -join ', ')." }

    Write-Host '== Parcours dans le navigateur et captures =='
    if ($Chromium) {
        Push-Location -LiteralPath $dossier
        try { & npx.cmd playwright install chromium | Out-Null } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw 'Le téléchargement de Chromium a échoué : relancez sans -Chromium pour utiliser Edge.' }
        Remove-Item Env:NAVIGATEUR_CANAL -ErrorAction SilentlyContinue
    } else { $env:NAVIGATEUR_CANAL = 'msedge' }
    $journal = Join-Path $dossier 'captures.log'
    $code = Invoke-ProcessusLimite -Programme 'node' -Arguments @('captures.mjs') -Dossier $dossier -Sortie $journal -DelaiSecondes $DelaiSecondes -ArbreComplet
    Get-Content -LiteralPath $journal, "$journal.err" -Encoding UTF8 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host $_ }
    if ($code -eq -1) { throw "Le parcours a dépassé $DelaiSecondes s et a été arrêté." }
    if ($code -ne 0) { throw 'Le parcours dans le navigateur a échoué (détail ci-dessus).' }
    Write-Host "Captures enregistrées dans $(Join-Path $RacineProjet 'rapport\figures')."
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
