# Démarre l'infrastructure Docker puis tous les services, et attend qu'ils soient en bonne santé.
# -Rebuild : reconstruit les jars avant le démarrage (sinon seulement s'ils manquent).
# -Orchestration bonita|direct : mode de S4 (par défaut celui de rest-candidatures/application.properties, soit bonita).
#   S4 est redémarré s'il tourne dans l'autre mode. Exemple : .\scripts\start-all.ps1 -Orchestration direct
param([switch]$Rebuild, [int]$DelaiSecondes = 90, [ValidateSet('', 'bonita', 'direct')][string]$Orchestration = '')
. "$PSScriptRoot\env.ps1"
try {
    Use-Jdk17
    Assert-Docker

    $jarsManquants = @($Services | Where-Object { -not (Test-Path -LiteralPath (Get-CheminJar $_.Module)) })
    if ($Rebuild -or $jarsManquants.Count -gt 0) {
        Write-Host '== Construction des services =='
        & mvn -q -f "$RacineProjet\pom.xml" clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'La construction Maven a échoué.' }
    }

    # Un port occupé par un autre programme : on le signale avant de démarrer quoi que ce soit.
    foreach ($s in $Services) {
        $occupe = Get-NetTCPConnection -LocalPort $s.Port -State Listen -ErrorAction SilentlyContinue
        if ($occupe -and -not (Get-ProcessusService $s.Module)) {
            throw "Le port $($s.Port) ($($s.Nom)) est déjà utilisé par le processus $($occupe[0].OwningProcess)."
        }
    }

    if ($Orchestration) {
        $env:ORCHESTRATION_MODE = $Orchestration
        $actuel = Get-ModeOrchestration
        if ($actuel -and $actuel -ne $Orchestration) {
            Write-Host "S4 tourne en mode $actuel : redémarrage en mode $Orchestration"
            Get-ProcessusService 'rest-candidatures' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
            Start-Sleep -Seconds 1
        }
    }

    Write-Host '== Infrastructure Docker (MySQL, Mailpit, Adminer) =='
    Invoke-Compose up -d --wait --wait-timeout 180

    Write-Host '== Services =='
    New-Item -ItemType Directory -Force -Path "$DossierLogs" | Out-Null
    foreach ($s in $Services) {
        if (Get-ProcessusService $s.Module) { Write-Host "$($s.Nom) : déjà démarré"; continue }
        $jar = Get-CheminJar $s.Module
        $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
        $sortie = Join-Path $DossierLogs "$($s.Module).log"
        $erreurs = Join-Path $DossierLogs "$($s.Module).err.log"
        # Lancement via cmd sans -Redirect* : le service n'hérite pas des flux de l'appelant, sinon un
        # appelant qui lit notre sortie (script de test, CI) attendrait indéfiniment la fin du service.
        # cmd /c retire la première et la dernière guillemet : la commande entière reste entre guillemets.
        $commande = "/c `"`"$java`" -Dfile.encoding=UTF-8 -jar `"$jar`" > `"$sortie`" 2> `"$erreurs`"`""
        Start-Process -FilePath 'cmd.exe' -ArgumentList $commande -WorkingDirectory "$RacineProjet" -WindowStyle Hidden
        Write-Host "$($s.Nom) : lancé"
    }

    $limite = (Get-Date).AddSeconds($DelaiSecondes)
    $enAttente = @($Services)
    while ($enAttente.Count -gt 0 -and (Get-Date) -lt $limite) {
        $enAttente = @($enAttente | Where-Object { (Get-Sante $_.Port).Code -ne 200 })
        if ($enAttente.Count -gt 0) { Start-Sleep -Seconds 2 }
    }

    Write-Host ''
    Write-Host '== État =='
    foreach ($s in $Services) {
        $code = (Get-Sante $s.Port).Code
        Write-Host ('{0,-18} http://localhost:{1}/health  ->  {2}' -f $s.Nom, $s.Port, $code)
    }
    if ($enAttente.Count -gt 0) {
        throw "Services non prêts : $(($enAttente | ForEach-Object { $_.Nom }) -join ', '). Consultez le dossier logs."
    }
    $mode = Get-ModeOrchestration
    $urlBonita = if ($env:BONITA_URL) { $env:BONITA_URL } else { 'http://localhost:8080/bonita' }
    Write-Host ''
    if ($mode -eq 'bonita') {
        Write-Host "Orchestration : bonita (processus Candidature dans Bonita, $urlBonita)"
        $bonitaRepond = try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 -Uri "$urlBonita/").StatusCode -lt 500 } catch { [bool]$_.Exception.Response }
        if (-not $bonitaRepond) {
            Write-Host "ATTENTION : Bonita ne répond pas sur $urlBonita. Les dépôts de candidature répondront 503." -ForegroundColor Yellow
            Write-Host '            Démarrez Bonita Studio (processus déployé), ou relancez avec -Orchestration direct.' -ForegroundColor Yellow
        }
    } else {
        Write-Host 'Orchestration : direct (étapes du processus exécutées par S4, secours de développement et de test)'
    }
    Write-Host "Mailpit : http://localhost:$(Get-PortConfigure 'MAILPIT_UI_PORT' 8025)   |   Adminer : http://localhost:$(Get-PortConfigure 'ADMINER_PORT' 8088)"
    Write-Host 'Tout est démarré.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
