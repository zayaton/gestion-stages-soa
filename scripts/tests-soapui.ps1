# Lance le projet SoapUI sur la pile démarrée et propre (.\scripts\reset-db.ps1 puis .\scripts\start-all.ps1).
# Suites fonctionnelles, puis pannes réelles : le script arrête S3, S1 ou MySQL, vérifie la faute, puis redémarre tout.
# -SansPannes : suites fonctionnelles seulement.
# Chaque étape externe (SoapUI, redémarrage des services) a une durée maximale : au-delà, elle est
# arrêtée et compte comme un échec. Le script ne peut donc pas rester bloqué.
param([switch]$SansPannes, [int]$DelaiSuiteSecondes = 300, [int]$DelaiRedemarrageSecondes = 300)
. "$PSScriptRoot\env.ps1"

$projet = Join-Path $RacineProjet 'tests\soapui\Stages-SOA-soapui-project.xml'
$rapports = Join-Path $RacineProjet 'tests\soapui\rapports'

function Find-TestRunner {
    if ($env:SOAPUI_HOME) { $candidats = @(Join-Path $env:SOAPUI_HOME 'bin\testrunner.bat') } else { $candidats = @() }
    $candidats += Get-ChildItem 'C:\Program Files\SmartBear\SoapUI-*\bin\testrunner.bat' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    foreach ($c in $candidats) { if (Test-Path -LiteralPath $c) { return $c } }
    throw 'SoapUI est introuvable. Installez-le depuis https://www.soapui.org/downloads/soapui/ ou définissez SOAPUI_HOME.'
}

$script:echecs = 0

function Invoke-Suite([string]$Suite) {
    $dossier = Join-Path $rapports ($Suite -replace '[^A-Za-z0-9]', '_')
    New-Item -ItemType Directory -Force -Path "$dossier" | Out-Null
    $fichierSortie = Join-Path $dossier 'sortie.txt'
    # Dossier de travail = dossier du rapport : SoapUI y écrit ses propres journaux.
    $code = Invoke-ProcessusLimite -Programme $runner -Arguments @("-s$Suite", '-r', '-j', "-f$dossier", $projet) `
        -Dossier $dossier -Sortie $fichierSortie -DelaiSecondes $DelaiSuiteSecondes -ArbreComplet
    $sortie = @(Get-Content -LiteralPath $fichierSortie -ErrorAction SilentlyContinue) + @(Get-Content -LiteralPath "$fichierSortie.err" -ErrorAction SilentlyContinue)
    $resume = @($sortie | Where-Object { $_ -match 'Total TestCases:|Total Request Assertions:|Total Failed Assertions:' }) | ForEach-Object { ($_ -replace '^.*(Total )', '$1').Trim() }
    Write-Host ("[{0}] {1}" -f $Suite, ($resume -join ' | '))
    if ($code -eq -1) {
        $script:echecs++
        Write-Host "  ECHEC : la suite a dépassé $DelaiSuiteSecondes s et a été arrêtée. Détail : $fichierSortie" -ForegroundColor Red
        return
    }
    $echouees = @($sortie | Where-Object { $_ -match 'ASSERTION FAILED|failed, exporting' } | ForEach-Object { $_ -replace '^.*\] ', '' } | Select-Object -Unique)
    if ($code -ne 0 -or $resume.Count -eq 0) {
        $script:echecs++
        Write-Host "  ECHEC : code de sortie $code. Détail : $fichierSortie" -ForegroundColor Red
        $echouees | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
}

# Relance les services arrêtés par un test de panne (start-all ignore ceux qui tournent déjà).
function Restart-Services([string]$Etape) {
    $journal = Join-Path $rapports "redemarrage-$Etape.txt"
    $code = Invoke-ProcessusLimite -Programme 'powershell.exe' `
        -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "$PSScriptRoot\start-all.ps1") `
        -Dossier $RacineProjet -Sortie $journal -DelaiSecondes $DelaiRedemarrageSecondes
    if ($code -eq -1) { throw "Le redémarrage des services a dépassé $DelaiRedemarrageSecondes s. Détail : $journal" }
    if ($code -ne 0) { throw "Le redémarrage des services a échoué (code $code). Détail : $journal" }
}

function Stop-Module([string]$Module) {
    Get-ProcessusService $Module | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
}

function Wait-Module([string]$Module) {
    $service = $Services | Where-Object { $_.Module -eq $Module }
    $limite = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $limite) {
        if ((Get-Sante $service.Port).Code -eq 200) { return }
        Start-Sleep -Seconds 2
    }
    throw "$Module n'est pas revenu en bonne santé."
}

try {
    Use-Jdk17
    Assert-Docker
    $runner = Find-TestRunner
    foreach ($s in $Services) {
        if ((Get-Sante $s.Port).Code -ne 200) { throw "$($s.Nom) ne répond pas : lancez d'abord .\scripts\start-all.ps1" }
    }
    if (Test-Path -LiteralPath $rapports) { Remove-Item -LiteralPath $rapports -Recurse -Force -Confirm:$false }
    New-Item -ItemType Directory -Force -Path "$rapports" | Out-Null

    Write-Host '== Suites fonctionnelles =='
    foreach ($suite in 'S1 Étudiants', 'S2 Entreprises', 'S3 Offres') { Invoke-Suite $suite }

    if (-not $SansPannes) {
        Write-Host '== Pannes : S3 arrêté =='
        Stop-Module 'soap-offres'
        Invoke-Suite 'Panne S3 arrêté'

        Write-Host '== Pannes : S1 arrêté =='
        Restart-Services 'apres-panne-S3'
        Wait-Module 'soap-offres'
        Stop-Module 'soap-etudiants'
        Invoke-Suite 'Panne S1 arrêté'

        Write-Host '== Pannes : MySQL arrêté =='
        Restart-Services 'apres-panne-S1'
        Wait-Module 'soap-etudiants'
        & docker stop soa-mysql | Out-Null
        try {
            Invoke-Suite 'Panne base arrêtée'
        } finally {
            & docker start soa-mysql | Out-Null
        }
        foreach ($m in 'soap-etudiants', 'soap-entreprises', 'soap-offres') { Wait-Module $m }
    }

    Write-Host ''
    if ($script:echecs -gt 0) { Write-Host "$($script:echecs) suite(s) en échec." -ForegroundColor Red; exit 1 }
    Write-Host 'Toutes les suites SoapUI sont réussies.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
