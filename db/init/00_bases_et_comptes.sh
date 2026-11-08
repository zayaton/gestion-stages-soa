#!/bin/bash
# Crée un schéma et un compte MySQL par service. Chaque compte n'a de droits que sur son schéma :
# la règle « une base par service » est donc garantie par MySQL, pas seulement par convention.
mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
SET NAMES utf8mb4;
$(for s in etudiants entreprises offres candidatures notifications suivi portail; do cat <<UNITE
CREATE DATABASE soa_$s CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'svc_$s'@'%' IDENTIFIED BY '$SOA_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON soa_$s.* TO 'svc_$s'@'%';
UNITE
done)
FLUSH PRIVILEGES;
SQL
