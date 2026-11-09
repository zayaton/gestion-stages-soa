# Test de bout en bout par le portail : tous les scénarios (parcours nominal, R1 à R7, R8, R11, espace responsable),
# puis une vraie panne (S3 arrêté). Vérifie les courriels dans Mailpit et la corrélation dans les journaux.
# Prérequis : .\scripts\start-all.ps1. Le script réinitialise les bases et Mailpit avant de commencer.
param([int]$DelaiSecondes = 240)
. "$PSScriptRoot\env.ps1"

$script = Join-Path $RacineProjet 'tests\e2e\bout-en-bout.mjs'
$rapports = Join-Path $RacineProjet 'tests\e2e\rapports'

function Wait-Pile {
    $limite = (Get-Date).AddSeconds(90)
    do {
        $enAttente = @($Services | Where-Object { (Get-Sante $_.Port).Code -ne 200 })
        if ($enAttente.Count -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $limite)
    throw "Services non disponibles : $(($enAttente | ForEach-Object { $_.Nom }) -join ', '). Lancez d'abord .\scripts\start-all.ps1"
}

function Invoke-Node([string]$Mode) {
    $sortie = Join-Path $rapports "$Mode.txt"
    $code = Invoke-ProcessusLimite -Programme 'node.exe' -Arguments @($script, $Mode) -Dossier $RacineProjet -Sortie $sortie -DelaiSecondes $DelaiSecondes
    Get-Content -LiteralPath $sortie -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s+ÉCHEC') { Write-Host $_ -ForegroundColor Red } else { Write-Host $_ }
    }
    if ($code -eq -1) { throw "Le test ($Mode) a dépassé $DelaiSecondes s et a été arrêté." }
    return $code
}

try {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js est introuvable : https://nodejs.org/' }
    if (Test-Path -LiteralPath $rapports) { Remove-Item -LiteralPath $rapports -Recurse -Force -Confirm:$false }
    New-Item -ItemType Directory -Force -Path "$rapports" | Out-Null

    Write-Host '== Réinitialisation des bases et de Mailpit =='
    & "$PSScriptRoot\reset-db.ps1" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'La réinitialisation des bases a échoué.' }
    Wait-Pile

    $codeScenarios = Invoke-Node 'scenarios'

    Write-Host ''
    Write-Host '== Panne : arrêt de S3 =='
    Get-ProcessusService 'soap-offres' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
    try {
        $codePanne = Invoke-Node 'panne'
    } finally {
        Write-Host '== Redémarrage de S3 =='
        $relance = Invoke-ProcessusLimite -Programme 'powershell.exe' -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "$PSScriptRoot\start-all.ps1") `
            -Dossier $RacineProjet -Sortie (Join-Path $rapports 'redemarrage.txt') -DelaiSecondes 180
        if ($relance -ne 0) { Write-Host "Redémarrage incomplet : voir $rapports\redemarrage.txt" -ForegroundColor Red }
    }

    Write-Host ''
    if ($codeScenarios -ne 0 -or $codePanne -ne 0) { throw 'Des vérifications de bout en bout ont échoué.' }
    Write-Host 'Test de bout en bout réussi. Les données ont changé : .\scripts\reset-db.ps1 avant une démonstration.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
