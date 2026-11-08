# Environnement partagé par les scripts du projet. À charger avec : . "$PSScriptRoot\env.ps1"
# Tous les chemins sont entre guillemets : le dossier du projet contient un espace.

$OutputEncoding = [Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$RacineProjet = Split-Path -Parent $PSScriptRoot
$FichierCompose = Join-Path $RacineProjet 'docker-compose.yml'
$DossierLogs = Join-Path $RacineProjet 'logs'

function Get-PortConfigure([string]$Variable, [int]$ParDefaut) {
    $valeur = [Environment]::GetEnvironmentVariable($Variable)
    if ($valeur) { return [int]$valeur }
    return $ParDefaut
}

$Services = @(
    @{ Module = 'soap-etudiants';     Nom = 'S1 Étudiants';      Port = (Get-PortConfigure 'ETUDIANTS_PORT' 8081) }
    @{ Module = 'soap-entreprises';   Nom = 'S2 Entreprises';    Port = (Get-PortConfigure 'ENTREPRISES_PORT' 8082) }
    @{ Module = 'soap-offres';        Nom = 'S3 Offres';         Port = (Get-PortConfigure 'OFFRES_PORT' 8083) }
    @{ Module = 'rest-candidatures';  Nom = 'S4 Candidatures';   Port = (Get-PortConfigure 'CANDIDATURES_PORT' 8084) }
    @{ Module = 'rest-notifications'; Nom = 'S5 Notifications';  Port = (Get-PortConfigure 'NOTIFICATIONS_PORT' 8085) }
    @{ Module = 'rest-suivi';         Nom = 'S6 Suivi';          Port = (Get-PortConfigure 'SUIVI_PORT' 8086) }
    @{ Module = 'portal-gateway';     Nom = 'Portail';           Port = (Get-PortConfigure 'PORTAIL_PORT' 8090) }
)

function Get-MajeureJdk([string]$Dossier) {
    $release = Join-Path $Dossier 'release'
    if (-not (Test-Path -LiteralPath $release)) { return $null }
    $ligne = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($ligne) { return [int]$ligne.Matches[0].Groups[1].Value }
    return $null
}

# Sélectionne le JDK 17 pour cette session, sans modifier la configuration de la machine.
function Use-Jdk17 {
    $candidats = @($env:JAVA_HOME, [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine'))
    foreach ($racine in 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft') {
        if (Test-Path -LiteralPath $racine) {
            $candidats += Get-ChildItem -LiteralPath $racine -Directory | Where-Object { $_.Name -match '17' } | ForEach-Object { $_.FullName }
        }
    }
    foreach ($dossier in $candidats) {
        if ($dossier -and (Get-MajeureJdk $dossier.TrimEnd('\')) -eq 17) {
            $env:JAVA_HOME = $dossier.TrimEnd('\')
            $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
            return
        }
    }
    throw "JDK 17 introuvable. Installez-le depuis https://adoptium.net/temurin/releases/?version=17"
}

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        $bin = 'C:\Program Files\Docker\Docker\resources\bin'
        if (Test-Path -LiteralPath $bin) { $env:Path += ";$bin" }
        else { throw "Docker est introuvable. Installez Docker Desktop : https://www.docker.com/products/docker-desktop/" }
    }
    cmd /c "docker info >nul 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "Le moteur Docker ne répond pas. Démarrez Docker Desktop puis relancez ce script." }
}

function Invoke-Compose {
    & docker compose -f "$FichierCompose" @args
    if ($LASTEXITCODE -ne 0) { throw "Échec de : docker compose $args" }
}

# Renvoie le code HTTP de /health (0 si le service ne répond pas) et le corps de la réponse.
function Get-Sante([int]$Port) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri "http://127.0.0.1:$Port/health"
        return [pscustomobject]@{ Code = [int]$r.StatusCode; Corps = [string]$r.Content }
    } catch {
        $reponse = $_.Exception.Response
        if ($reponse) { return [pscustomobject]@{ Code = [int]$reponse.StatusCode; Corps = '' } }
        return [pscustomobject]@{ Code = 0; Corps = '' }
    }
}

function Get-CheminJar([string]$Module) {
    return Join-Path $RacineProjet "$Module\target\$Module.jar"
}

# Retrouve le processus d'un service par son jar : ne touche jamais à un autre programme Java.
function Get-ProcessusService([string]$Module) {
    $jar = Get-CheminJar $Module
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jar) }
}
