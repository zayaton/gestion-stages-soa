# Arrête les services puis les conteneurs. Les données MySQL sont conservées.
# -ServicesSeuls : laisse tourner MySQL, Mailpit et Adminer.
param([switch]$ServicesSeuls)
. "$PSScriptRoot\env.ps1"
try {
    foreach ($s in $Services) {
        $processus = @(Get-ProcessusService $s.Module)
        if ($processus.Count -eq 0) { Write-Host "$($s.Nom) : déjà arrêté"; continue }
        foreach ($p in $processus) { Stop-Process -Id $p.ProcessId -Force -Confirm:$false }
        Write-Host "$($s.Nom) : arrêté"
    }
    if (-not $ServicesSeuls) {
        Assert-Docker
        Invoke-Compose stop
    }
    Write-Host 'Arrêt terminé.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
