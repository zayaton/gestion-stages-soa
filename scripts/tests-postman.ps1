# Lance la collection Postman avec Newman sur la pile démarrée (.\scripts\start-all.ps1).
# La collection se joue dans l'ordre sur des données fraîches : le script réinitialise d'abord les bases et Mailpit.
# -SansReset : garde les données actuelles (la collection échouera si elle a déjà été jouée).
param([switch]$SansReset, [int]$DelaiSecondes = 300)
. "$PSScriptRoot\env.ps1"

$dossier = Join-Path $RacineProjet 'tests\postman'
$rapports = Join-Path $dossier 'rapports'

try {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Node.js est introuvable. Installez-le depuis https://nodejs.org/ puis relancez ce script.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $dossier 'node_modules\.bin\newman.cmd'))) {
        Write-Host '== Installation de Newman (dans tests\postman, une seule fois) =='
        Push-Location -LiteralPath $dossier
        try { & npm.cmd install --no-audit --no-fund | Out-Null } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "L'installation de Newman a échoué." }
    }
    if (-not $SansReset) {
        Write-Host '== Réinitialisation des bases et de Mailpit =='
        & "$PSScriptRoot\reset-db.ps1" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'La réinitialisation des bases a échoué.' }
    }
    # La collection vérifie les résultats immédiats du mode direct.
    & "$PSScriptRoot\start-all.ps1" -Orchestration direct *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Démarrage de la pile en mode direct impossible.' }
    # Après une réinitialisation, les services retrouvent leur base en quelques secondes.
    $limite = (Get-Date).AddSeconds(90)
    do {
        $enAttente = @($Services | Where-Object { (Get-Sante $_.Port).Code -ne 200 })
        if ($enAttente.Count -eq 0) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $limite)
    if ($enAttente.Count -gt 0) {
        throw "Services non disponibles : $(($enAttente | ForEach-Object { $_.Nom }) -join ', '). Lancez d'abord .\scripts\start-all.ps1"
    }

    if (Test-Path -LiteralPath $rapports) { Remove-Item -LiteralPath $rapports -Recurse -Force -Confirm:$false }
    New-Item -ItemType Directory -Force -Path "$rapports" | Out-Null
    Write-Host '== Collection Postman (Newman) =='
    $sortie = Join-Path $rapports 'newman.txt'
    $code = Invoke-ProcessusLimite -Programme (Join-Path $dossier 'node_modules\.bin\newman.cmd') -Arguments @(
        'run', (Join-Path $dossier 'Stages-SOA.postman_collection.json'),
        '-e', (Join-Path $dossier 'Stages-SOA-local.postman_environment.json'),
        '--reporters', 'cli,junit,htmlextra', '--color', 'off', '--timeout-request', '15000',
        '--reporter-junit-export', (Join-Path $rapports 'newman-junit.xml'),
        '--reporter-htmlextra-export', (Join-Path $rapports 'rapport-newman.html'),
        '--reporter-htmlextra-title', 'Stages SOA - tests des services REST'
    ) -Dossier $dossier -Sortie $sortie -DelaiSecondes $DelaiSecondes -ArbreComplet

    $lignes = @(Get-Content -LiteralPath $sortie -Encoding UTF8 -ErrorAction SilentlyContinue)
    # Tableau récapitulatif de Newman, puis détail des échecs éventuels.
    $debut = [Array]::FindIndex([string[]]$lignes, [Predicate[string]] { param($l) $l -match '^\S*\s*│\s+executed' })
    if ($debut -ge 1) { $lignes[($debut - 1)..([Math]::Min($lignes.Count - 1, $debut + 12))] | ForEach-Object { Write-Host $_ } }
    $lignes | Where-Object { $_ -match '^\s*\d+\.\s+(AssertionError|Error)' -or $_ -match '^\s+(expected|at assertion)' } |
        Select-Object -First 40 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    if ($code -eq -1) { throw "Newman a dépassé $DelaiSecondes s et a été arrêté. Détail : $sortie" }
    if ($code -ne 0) { throw "Des tests Postman ont échoué. Rapport : $rapports\rapport-newman.html" }
    Write-Host "Tous les tests Postman sont réussis. Rapport : $rapports\rapport-newman.html"
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
