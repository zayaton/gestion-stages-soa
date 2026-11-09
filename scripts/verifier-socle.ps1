# Vérifie le socle (phase 1) sur la pile réellement démarrée : santé des services, isolation des
# schémas, données de démonstration et préconditions de chaque scénario.
# Prérequis : .\scripts\reset-db.ps1 puis .\scripts\start-all.ps1
. "$PSScriptRoot\env.ps1"
Assert-Docker

$script:echecs = 0
$script:total = 0

function Test-Egal([string]$Nom, $Attendu, $Obtenu) {
    $script:total++
    if ("$Attendu" -ceq "$Obtenu") {
        Write-Host "  OK    $Nom"
    } else {
        $script:echecs++
        Write-Host "  ECHEC $Nom : attendu [$Attendu], obtenu [$Obtenu]" -ForegroundColor Red
    }
}

$motDePasseRoot = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { 'soa_root_2026' }
$motDePasseSvc = if ($env:SOA_DB_PASSWORD) { $env:SOA_DB_PASSWORD } else { 'soa_dev_2026' }

function Invoke-Sql([string]$Requete) {
    $r = & docker exec -e "MYSQL_PWD=$motDePasseRoot" soa-mysql mysql --default-character-set=utf8mb4 -uroot -N -B -e $Requete 2>$null
    return (($r -join ' ') -replace "`t", ' ')
}

# Exécute une requête avec un compte de service. Renvoie 0 si elle réussit, sinon le code d'erreur
# MySQL : un refus doit être le bon refus (1142 droits, 1062 doublon, 3819 contrainte CHECK).
function Test-AccesCompte([string]$Compte, [string]$Requete) {
    $sortie = (& docker exec -e "MYSQL_PWD=$motDePasseSvc" soa-mysql mysql "-u$Compte" -N -B -e $Requete 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0) { return 0 }
    if ($sortie -match 'ERROR (\d+)') { return [int]$Matches[1] }
    return -1
}

Write-Host 'Santé des services'
foreach ($s in $Services) {
    $sante = Get-Sante $s.Port
    Test-Egal "$($s.Nom) répond 200 sur /health" 200 $sante.Code
    Test-Egal "$($s.Nom) voit sa base" $true ($sante.Corps -match '"baseDeDonnees":"OK"')
}

Write-Host 'Infrastructure'
foreach ($p in @(@{ Nom = 'Mailpit'; Url = 'http://127.0.0.1:8025/api/v1/info' }, @{ Nom = 'Adminer'; Url = 'http://127.0.0.1:8088/' })) {
    $code = try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri $p.Url).StatusCode } catch { 0 }
    Test-Egal "$($p.Nom) répond" 200 $code
}
$envoi = New-Object Net.Sockets.TcpClient
try { $envoi.Connect('127.0.0.1', 1025); Test-Egal 'Mailpit accepte SMTP sur 1025' $true $envoi.Connected } catch { Test-Egal 'Mailpit accepte SMTP sur 1025' $true $false } finally { $envoi.Close() }

Write-Host 'Isolation : un schéma par service'
$noms = 'etudiants', 'entreprises', 'offres', 'candidatures', 'notifications', 'suivi', 'portail'
$table = @{ etudiants = 'etudiant'; entreprises = 'entreprise'; offres = 'offre'; candidatures = 'candidature'; notifications = 'notification'; suivi = 'stage'; portail = 'compte' }
foreach ($n in $noms) {
    Test-Egal "svc_$n lit son schéma" 0 (Test-AccesCompte "svc_$n" "SELECT COUNT(*) FROM soa_$n.$($table[$n])")
    $autre = $noms[($noms.IndexOf($n) + 1) % $noms.Count]
    Test-Egal "svc_$n ne lit pas soa_$autre" 1142 (Test-AccesCompte "svc_$n" "SELECT COUNT(*) FROM soa_$autre.$($table[$autre])")
}
Test-Egal 'Aucun compte de service ne peut modifier une structure' 1142 (Test-AccesCompte 'svc_etudiants' 'DROP TABLE soa_etudiants.etudiant')

Write-Host 'Volumes de données'
Test-Egal '20 étudiants' 20 (Invoke-Sql 'SELECT COUNT(*) FROM soa_etudiants.etudiant')
Test-Egal 'Les 7 filières INPT sont représentées' 7 (Invoke-Sql 'SELECT COUNT(DISTINCT filiere) FROM soa_etudiants.etudiant')
Test-Egal 'Les 3 niveaux sont représentés' 3 (Invoke-Sql 'SELECT COUNT(DISTINCT niveau) FROM soa_etudiants.etudiant')
Test-Egal '8 entreprises' 8 (Invoke-Sql 'SELECT COUNT(*) FROM soa_entreprises.entreprise')
Test-Egal '16 offres' 16 (Invoke-Sql 'SELECT COUNT(*) FROM soa_offres.offre')
Test-Egal '29 comptes' 29 (Invoke-Sql 'SELECT COUNT(*) FROM soa_portail.compte')
Test-Egal 'Comptes par rôle' '1 20 8' (Invoke-Sql "SELECT SUM(role='RESPONSABLE'), SUM(role='ETUDIANT'), SUM(role='ENTREPRISE') FROM soa_portail.compte")
Test-Egal 'Hachage BCrypt pour tous les comptes' 29 (Invoke-Sql "SELECT COUNT(*) FROM soa_portail.compte WHERE mot_de_passe_hash LIKE '`$2a`$10`$%'")
Test-Egal 'Accents conservés (UTF-8)' 'Aïcha Fès' (Invoke-Sql "SELECT (SELECT prenom FROM soa_etudiants.etudiant WHERE id=20), (SELECT ville FROM soa_etudiants.etudiant WHERE id=3)")

Write-Host 'Cohérence entre schémas'
Test-Egal 'Toute offre référence une entreprise existante' 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_offres.offre o LEFT JOIN soa_entreprises.entreprise e ON e.id=o.entreprise_id WHERE e.id IS NULL')
Test-Egal 'Toute offre a au moins une filière' 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_offres.offre o LEFT JOIN soa_offres.offre_filiere f ON f.offre_id=o.id WHERE f.offre_id IS NULL')
Test-Egal 'Toute candidature référence un étudiant et une offre existants' 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_candidatures.candidature c LEFT JOIN soa_etudiants.etudiant e ON e.id=c.etudiant_id LEFT JOIN soa_offres.offre o ON o.id=c.offre_id WHERE e.id IS NULL OR o.id IS NULL')
Test-Egal "L'entreprise de chaque candidature est celle de l'offre" 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_candidatures.candidature c JOIN soa_offres.offre o ON o.id=c.offre_id WHERE o.entreprise_id<>c.entreprise_id')
Test-Egal 'Chaque candidature respecte filière et niveau (R7)' 0 (Invoke-Sql "SELECT COUNT(*) FROM soa_candidatures.candidature c JOIN soa_etudiants.etudiant e ON e.id=c.etudiant_id JOIN soa_offres.offre o ON o.id=c.offre_id LEFT JOIN soa_offres.offre_filiere f ON f.offre_id=o.id AND f.filiere=e.filiere WHERE f.offre_id IS NULL OR e.niveau<o.niveau_minimum")
Test-Egal 'Le dernier statut historisé est le statut courant' 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_candidatures.candidature c WHERE c.statut <> (SELECT h.nouveau_statut FROM soa_candidatures.candidature_historique h WHERE h.candidature_id=c.id ORDER BY h.date_changement DESC, h.id DESC LIMIT 1)')
Test-Egal 'Chaque stage correspond à une candidature acceptée du même étudiant' 0 (Invoke-Sql "SELECT COUNT(*) FROM soa_suivi.stage s LEFT JOIN soa_candidatures.candidature c ON c.id=s.candidature_id AND c.etudiant_id=s.etudiant_id AND c.offre_id=s.offre_id AND c.statut='ACCEPTEE' WHERE c.id IS NULL")
Test-Egal 'Le score indiqué correspond aux compétences communes' 0 (Invoke-Sql 'SELECT COUNT(*) FROM soa_candidatures.candidature c WHERE c.score_competences <> (SELECT ROUND(100 * COUNT(ec.competence) / COUNT(*)) FROM soa_offres.offre_competence oc LEFT JOIN soa_etudiants.etudiant_competence ec ON ec.competence=oc.competence AND ec.etudiant_id=c.etudiant_id WHERE oc.offre_id=c.offre_id)')

Write-Host 'Préconditions des scénarios (docs/SCENARIOS_DEMO.md)'
Test-Egal 'R1 : étudiant 16 EN_STAGE, étudiant 17 SUSPENDU' 'EN_STAGE SUSPENDU' (Invoke-Sql "SELECT (SELECT statut FROM soa_etudiants.etudiant WHERE id=16),(SELECT statut FROM soa_etudiants.etudiant WHERE id=17)")
Test-Egal 'R2 : offre 11 BROUILLON' 'BROUILLON' (Invoke-Sql 'SELECT statut FROM soa_offres.offre WHERE id=11')
Test-Egal "R2 : offre 12 ACTIVE d'une entreprise INACTIVE" 'ACTIVE INACTIVE' (Invoke-Sql 'SELECT o.statut, e.statut FROM soa_offres.offre o JOIN soa_entreprises.entreprise e ON e.id=o.entreprise_id WHERE o.id=12')
Test-Egal 'R3 : offre 13 ACTIVE, date limite passée' 'ACTIVE 1' (Invoke-Sql 'SELECT statut, date_limite<CURDATE() FROM soa_offres.offre WHERE id=13')
Test-Egal 'R4 : offre 14 ACTIVE sans place' 'ACTIVE 0' (Invoke-Sql 'SELECT statut, nombre_places FROM soa_offres.offre WHERE id=14')
Test-Egal 'R5 : étudiant 2 a déjà candidaté à l''offre 2' 1 (Invoke-Sql "SELECT COUNT(*) FROM soa_candidatures.candidature WHERE etudiant_id=2 AND offre_id=2 AND statut<>'ANNULEE'")
Test-Egal 'R6 : étudiant 3 a 5 candidatures actives' 5 (Invoke-Sql "SELECT COUNT(*) FROM soa_candidatures.candidature WHERE etudiant_id=3 AND statut IN ('DEPOSEE','EN_COURS_EVALUATION')")
Test-Egal "R6 : l'offre 1 reste ouverte et compatible pour l'étudiant 3" 1 (Invoke-Sql "SELECT COUNT(*) FROM soa_offres.offre_filiere WHERE offre_id=1 AND filiere='ASEDS'")
Test-Egal 'R7 : étudiant 5 en 1A, offre 1 exige 2A' '1A 2A' (Invoke-Sql 'SELECT (SELECT niveau FROM soa_etudiants.etudiant WHERE id=5),(SELECT niveau_minimum FROM soa_offres.offre WHERE id=1)')
Test-Egal "R7 : la filière de l'étudiant 6 n'est pas acceptée par l'offre 1" 0 (Invoke-Sql "SELECT COUNT(*) FROM soa_offres.offre_filiere WHERE offre_id=1 AND filiere=(SELECT filiere FROM soa_etudiants.etudiant WHERE id=6)")
Test-Egal "Parcours nominal : étudiant 1 ACTIF, sans candidature, offre 1 ouverte" 'ACTIF 0 ACTIVE 1' (Invoke-Sql "SELECT (SELECT statut FROM soa_etudiants.etudiant WHERE id=1),(SELECT COUNT(*) FROM soa_candidatures.candidature WHERE etudiant_id=1),(SELECT statut FROM soa_offres.offre WHERE id=1),(SELECT date_limite>=CURDATE() AND nombre_places>0 FROM soa_offres.offre WHERE id=1)")
Test-Egal 'Complément : étudiant 4 ACTIF, compatible avec l''offre 4' 'ACTIF 1' (Invoke-Sql "SELECT e.statut, COUNT(f.filiere) FROM soa_etudiants.etudiant e LEFT JOIN soa_offres.offre_filiere f ON f.offre_id=4 AND f.filiere=e.filiere WHERE e.id=4 GROUP BY e.statut")
Test-Egal 'Rappels : 2 offres actives dans les 3 jours' 2 (Invoke-Sql "SELECT COUNT(*) FROM soa_offres.offre WHERE statut='ACTIVE' AND date_limite BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 3 DAY)")

Write-Host 'Contraintes de la base'
Test-Egal 'R5 : la base refuse un doublon non annulé' 1062 (Test-AccesCompte 'svc_candidatures' "INSERT INTO soa_candidatures.candidature (etudiant_id, offre_id, entreprise_id, lettre_motivation) VALUES (2,2,3,'doublon')")
Test-Egal 'Un matricule mal formé est refusé' 3819 (Test-AccesCompte 'svc_etudiants' "INSERT INTO soa_etudiants.etudiant (matricule,nom,prenom,filiere,niveau,email,telephone,ville) VALUES ('X1','a','b','AMOA','1A','a@b.ma','06','Rabat')")
Test-Egal 'Une note hors de 0 à 20 est refusée' 3819 (Test-AccesCompte 'svc_suivi' "INSERT INTO soa_suivi.evaluation (stage_id,note,evaluateur) VALUES (1,21,'x')")
$jourMaroc = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId((Get-Date), 'Morocco Standard Time').ToString('yyyy-MM-dd')
Test-Egal 'Fuseau horaire de MySQL : date du jour au Maroc' $jourMaroc (Invoke-Sql 'SELECT CURDATE()')

Write-Host ''
Write-Host "Résultat : $($script:total - $script:echecs)/$($script:total) vérifications réussies"
if ($script:echecs -gt 0) { exit 1 }
