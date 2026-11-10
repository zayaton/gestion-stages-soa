# Construit et déploie le processus « Candidature » 1.0 dans Bonita, sans passer par les écrans du Studio.
# Le processus reprend le diagramme de docs/bonita/processus-candidature.bpmn en « mode secours » du guide (une opération
# Groovy par tâche, classe inpt.SoaProcessus livrée dans l'archive) : aucun connecteur de la Marketplace n'est nécessaire.
# Une version 1.0 déjà déployée est remplacée (ses cas sont supprimés). L'archive produite est bpmn\Candidature--1.0.bar.
# Prérequis : Bonita Studio ouvert (il démarre le serveur Bonita sur le port 8080).
param([string]$Url = 'http://localhost:8080/bonita', [string]$Utilisateur = 'walter.bates', [string]$MotDePasse = 'bpm')
. "$PSScriptRoot\env.ps1"

$dossier = Join-Path $RacineProjet 'docs\bonita\deploiement'
try {
    Use-Jdk17
    try {
        $null = Invoke-WebRequest -UseBasicParsing "$Url/loginservice" -Method Post -TimeoutSec 5 `
            -Body "username=$Utilisateur&password=$MotDePasse&redirect=false"
    } catch {
        if (-not $_.Exception.Response) { throw "Bonita ne répond pas sur $Url. Ouvrez Bonita Studio et attendez la fin de son démarrage." }
    }
    Push-Location -LiteralPath $dossier
    try {
        Write-Host '== Compilation de l''outil de déploiement =='
        & mvn -q compile dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'La compilation de l''outil de déploiement a échoué.' }
        $classpath = 'target\classes;' + (Get-Content 'target\classpath.txt' -Raw).Trim()
        Write-Host '== Construction et déploiement du processus =='
        & java -cp $classpath ma.inpt.stages.bonita.Deploiement $RacineProjet $Url $Utilisateur $MotDePasse
        if ($LASTEXITCODE -ne 0) { throw 'Le déploiement a échoué (détail ci-dessus).' }
    } finally { Pop-Location }
    Write-Host "Ensuite : .\scripts\start-all.ps1 -Orchestration bonita"
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
