# Affiche l'état de chaque service. Code de sortie 1 si l'un d'eux n'est pas en bonne santé.
. "$PSScriptRoot\env.ps1"
$echec = $false
foreach ($s in $Services) {
    $sante = Get-Sante $s.Port
    if ($sante.Code -ne 200) { $echec = $true }
    Write-Host ('{0,-18} port {1}  ->  {2}  {3}' -f $s.Nom, $s.Port, $sante.Code, $sante.Corps)
}
if ($echec) { exit 1 }
