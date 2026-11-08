# Construit le projet avec le JDK 17. Les options sont transmises à Maven.
# Exemples : .\scripts\build.ps1               (compile, teste, package)
#            .\scripts\build.ps1 -DskipTests
#            .\scripts\build.ps1 test -pl common
. "$PSScriptRoot\env.ps1"
Use-Jdk17
$objectifs = if ($args | Where-Object { $_ -notlike '-*' }) { @() } else { @('clean', 'package') }
& mvn -f "$RacineProjet\pom.xml" @objectifs @args
exit $LASTEXITCODE
