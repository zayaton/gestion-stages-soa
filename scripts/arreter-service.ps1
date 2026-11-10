# Arrête un seul service, pour montrer une panne pendant la démonstration.
# Exemple : .\scripts\arreter-service.ps1 S3     (puis .\scripts\start-all.ps1 pour le relancer, même mode d'orchestration)
param([Parameter(Mandatory = $true)][ValidateSet('S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'Portail')][string]$Service)
. "$PSScriptRoot\env.ps1"

$modules = @{ S1 = 'soap-etudiants'; S2 = 'soap-entreprises'; S3 = 'soap-offres'; S4 = 'rest-candidatures'
              S5 = 'rest-notifications'; S6 = 'rest-suivi'; Portail = 'portal-gateway' }
$processus = @(Get-ProcessusService $modules[$Service])
if ($processus.Count -eq 0) {
    Write-Host "$Service ne tourne pas."
    exit 0
}
$processus | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
Write-Host "$Service arrêté. Pour le relancer : .\scripts\start-all.ps1"
