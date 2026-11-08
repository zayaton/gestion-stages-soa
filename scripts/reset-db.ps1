# Remet les bases dans leur état initial : supprime le volume MySQL et rejoue db\init.
# Les dates de démonstration sont recalculées par rapport à aujourd'hui.
# La boîte Mailpit est vidée aussi, car son conteneur est recréé.
. "$PSScriptRoot\env.ps1"
try {
    Assert-Docker
    Write-Host '== Suppression des conteneurs et du volume de données =='
    Invoke-Compose down -v
    Write-Host '== Recréation des bases et chargement des données =='
    Invoke-Compose up -d --wait --wait-timeout 180
    Write-Host 'Bases réinitialisées.'
} catch {
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
