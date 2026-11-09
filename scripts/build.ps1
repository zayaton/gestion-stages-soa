# Construit le projet avec le JDK 17. Les options sont transmises à Maven.
# Exemples : .\scripts\build.ps1               (compile, teste, package)
#            .\scripts\build.ps1 -DskipTests
#            .\scripts\build.ps1 test -pl common
. "$PSScriptRoot\env.ps1"
Use-Jdk17
# Un service démarré verrouille ses jars : Maven ne pourrait pas les remplacer.
$enCours = @($Services | Where-Object { Get-ProcessusService $_.Module })
if ($enCours.Count -gt 0) {
    Write-Host "Arrêt des services démarrés avant la construction : $(($enCours | ForEach-Object { $_.Nom }) -join ', ')"
    & "$PSScriptRoot\stop-all.ps1" -ServicesSeuls | Out-Null
}
$objectifs = if ($args.Count -gt 0 -and $args[0] -notlike '-*') { @() } else { @('clean', 'package') }
& mvn -f "$RacineProjet\pom.xml" @objectifs @args
exit $LASTEXITCODE
