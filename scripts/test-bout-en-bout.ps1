# Test de bout en bout par le portail : tous les scénarios (parcours nominal, R1 à R7, R8, R11, espace responsable),
# puis une vraie panne (S3 arrêté). Vérifie les courriels dans Mailpit et la corrélation dans les journaux.
# Prérequis : .\scripts\start-all.ps1. Le script réinitialise les bases et Mailpit avant de commencer.
# -Orchestration :
#   direct     (défaut) S4 exécute les étapes du processus ;
#   simulation S4 en mode bonita face au processus simulé (tests\bonita\SimulationBonita.groovy) : vérifie le pont S4-Bonita
#              et les scripts Groovy livrés, sans Bonita Studio ;
#   bonita     S4 face au vrai Bonita (processus déployé, voir docs\bonita\GUIDE_BONITA.md).
# -Secours (avec simulation) : les tâches simulées utilisent le mode secours du guide (aucun connecteur).
param([int]$DelaiSecondes = 300, [ValidateSet('direct', 'simulation', 'bonita')][string]$Orchestration = 'direct', [switch]$Secours)
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

    $simulation = $null
    if ($Orchestration -eq 'simulation') {
        Write-Host '== Bonita simulé (port 8099) =='
        Use-Jdk17
        $classpath = Get-ClasspathGroovy
        $env:SIMULATION_SECOURS = if ($Secours) { '1' } else { '0' }
        $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
        $journalSimulation = Join-Path $rapports 'simulation.log'
        $scriptSimulation = Join-Path $RacineProjet 'tests\bonita\SimulationBonita.groovy'
        $simulation = Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8', '-cp', "`"$classpath`"", 'groovy.ui.GroovyMain', "`"$scriptSimulation`"", '8099') `
            -WorkingDirectory $RacineProjet -WindowStyle Hidden -PassThru -RedirectStandardOutput $journalSimulation -RedirectStandardError "$journalSimulation.err"
        $limite = (Get-Date).AddSeconds(60)
        while (-not (Select-String -LiteralPath $journalSimulation -Pattern 'Bonita simulé' -Quiet -ErrorAction SilentlyContinue) -and (Get-Date) -lt $limite) { Start-Sleep -Seconds 1 }
        if (-not (Select-String -LiteralPath $journalSimulation -Pattern 'Bonita simulé' -Quiet -ErrorAction SilentlyContinue)) {
            throw "La simulation de Bonita n'a pas démarré : $journalSimulation.err"
        }
        $env:BONITA_URL = 'http://localhost:8099/bonita'
        Get-ProcessusService 'rest-candidatures' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
    }
    $modeS4 = if ($Orchestration -eq 'direct') { 'direct' } else { 'bonita' }
    & "$PSScriptRoot\start-all.ps1" -Orchestration $modeS4 *> $null
    Wait-Pile
    if ((Get-ModeOrchestration) -ne $modeS4) { throw "S4 n'est pas en mode $modeS4." }
    $env:ORCHESTRATION = $modeS4
    Write-Host "== Orchestration : $Orchestration =="

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
        if ($simulation) {
            # S4 quitte la simulation : il reprend l'adresse de Bonita de sa configuration.
            Stop-Process -Id $simulation.Id -Force -Confirm:$false -ErrorAction SilentlyContinue
            Remove-Item Env:BONITA_URL -ErrorAction SilentlyContinue
            Get-ProcessusService 'rest-candidatures' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
            & "$PSScriptRoot\start-all.ps1" *> $null
        }
    }

    Write-Host ''
    if ($codeScenarios -ne 0 -or $codePanne -ne 0) { throw 'Des vérifications de bout en bout ont échoué.' }
    Write-Host 'Test de bout en bout réussi. Les données ont changé : .\scripts\reset-db.ps1 avant une démonstration.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
