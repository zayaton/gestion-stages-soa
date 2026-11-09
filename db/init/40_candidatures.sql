-- S4 Gestion des candidatures : schéma et données de démonstration.
SET NAMES utf8mb4;
USE soa_candidatures;

-- Une soumission existe dès le dépôt. La candidature n'est enregistrée qu'après la passerelle de
-- décision (étape 5 de l'énoncé) : la soumission conserve donc le motif d'un rejet.
CREATE TABLE soumission (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  etudiant_id       BIGINT NOT NULL,
  offre_id          BIGINT NOT NULL,
  lettre_motivation TEXT NOT NULL,
  statut            ENUM('EN_TRAITEMENT','ENREGISTREE','REJETEE','ERREUR') NOT NULL DEFAULT 'EN_TRAITEMENT',
  regle_rejetee     VARCHAR(5) NULL COMMENT 'R1 à R7 lorsque la soumission est rejetée',
  motif_rejet       VARCHAR(500) NULL,
  bonita_case_id    BIGINT NULL,
  cree_le           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX idx_soumission_etudiant ON soumission (etudiant_id);

CREATE TABLE candidature (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  soumission_id     BIGINT NULL UNIQUE,
  etudiant_id       BIGINT NOT NULL,
  offre_id          BIGINT NOT NULL,
  entreprise_id     BIGINT NOT NULL,
  lettre_motivation TEXT NOT NULL,
  score_competences TINYINT UNSIGNED NULL COMMENT 'Indicatif uniquement (R7)',
  statut            ENUM('DEPOSEE','EN_COURS_EVALUATION','ACCEPTEE','REFUSEE','ANNULEE') NOT NULL DEFAULT 'DEPOSEE',
  cree_le           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- R5 : 1 tant que la candidature n'est pas annulée, NULL sinon. MySQL autorise plusieurs NULL dans
  -- un index unique, donc on peut recandidater après annulation, mais jamais en parallèle.
  non_annulee       TINYINT GENERATED ALWAYS AS (IF(statut <> 'ANNULEE', 1, NULL)) STORED,
  CONSTRAINT uq_candidature_r5 UNIQUE (etudiant_id, offre_id, non_annulee),
  CONSTRAINT fk_candidature_soumission FOREIGN KEY (soumission_id) REFERENCES soumission (id),
  CONSTRAINT ck_candidature_score CHECK (score_competences IS NULL OR score_competences <= 100)
);
CREATE INDEX idx_candidature_etudiant_statut ON candidature (etudiant_id, statut);
CREATE INDEX idx_candidature_offre ON candidature (offre_id);
CREATE INDEX idx_candidature_entreprise_statut ON candidature (entreprise_id, statut);

CREATE TABLE candidature_historique (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidature_id  BIGINT NOT NULL,
  ancien_statut   VARCHAR(25) NULL,
  nouveau_statut  VARCHAR(25) NOT NULL,
  date_changement DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acteur          VARCHAR(120) NOT NULL,
  commentaire     VARCHAR(1000) NULL,
  CONSTRAINT fk_historique_candidature FOREIGN KEY (candidature_id) REFERENCES candidature (id) ON DELETE CASCADE
);
CREATE INDEX idx_historique_candidature ON candidature_historique (candidature_id, date_changement);

-- Journal des étapes du processus, par soumission : alimente la vue BPMN en direct du portail.
-- En mode direct, S4 l'écrit lui-même ; en mode bonita, le processus l'alimente par POST /soumissions/{id}/etapes.
CREATE TABLE etape_processus (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  soumission_id BIGINT NOT NULL,
  etape         VARCHAR(40) NOT NULL,
  resultat      ENUM('OK','REJET','ECHEC','INFO') NOT NULL,
  detail        VARCHAR(500) NULL,
  date_etape    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  acteur        VARCHAR(120) NOT NULL,
  CONSTRAINT fk_etape_soumission FOREIGN KEY (soumission_id) REFERENCES soumission (id) ON DELETE CASCADE
);
CREATE INDEX idx_etape_soumission ON etape_processus (soumission_id, date_etape);

-- Scénarios (voir docs/SCENARIOS_DEMO.md) :
-- candidature 1 prépare le rejet R5 ; candidatures 2 à 6 donnent 5 candidatures actives pour le rejet R6.
INSERT INTO candidature (id, etudiant_id, offre_id, entreprise_id, lettre_motivation, score_competences, statut, cree_le) VALUES
( 1, 2, 2, 3,'Madame, Monsieur, votre mission de refonte du SI correspond à ma formation en AMOA.',67,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 3 DAY)),
( 2, 3, 3, 1,'Passionnée par le développement Java, je souhaite rejoindre votre équipe.',100,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 9 DAY)),
( 3, 3, 7, 4,'Je souhaite mettre mes compétences au service de votre plateforme.',33,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 8 DAY)),
( 4, 3, 8, 2,'L''automatisation des déploiements est un domaine qui me motive.',33,'EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 7 DAY)),
( 5, 3, 9, 5,'Votre projet de supervision solaire m''intéresse particulièrement.',0,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 6 DAY)),
( 6, 3,10, 6,'Je souhaite contribuer à la modernisation des services publics.',0,'EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 5 DAY)),
( 7, 9, 7, 4,'Votre plateforme correspond à mon projet professionnel.',67,'REFUSEE',DATE_SUB(NOW(), INTERVAL 12 DAY)),
( 8,11, 3, 1,'Je souhaite rejoindre votre équipe de développement.',50,'ANNULEE',DATE_SUB(NOW(), INTERVAL 10 DAY)),
( 9,13, 2, 3,'Mon parcours en SESNum m''a préparée à ce type de mission.',67,'EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 4 DAY)),
(10,16,15, 3,'Ma spécialisation AMOA correspond à vos besoins en conformité.',100,'ACCEPTEE',DATE_SUB(NOW(), INTERVAL 60 DAY)),
(11,18,16, 7,'Je souhaite appliquer mes travaux de prévision à la logistique.',50,'ACCEPTEE',DATE_SUB(NOW(), INTERVAL 320 DAY)),
(12,19, 8, 2,'L''exploitation de plateformes Kubernetes est au cœur de mon parcours.',100,'ACCEPTEE',DATE_SUB(NOW(), INTERVAL 15 DAY));

INSERT INTO candidature_historique (candidature_id, ancien_statut, nouveau_statut, date_changement, acteur, commentaire) VALUES
( 1,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 3 DAY),'etudiant:2','Candidature enregistrée'),
( 2,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 9 DAY),'etudiant:3','Candidature enregistrée'),
( 3,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 8 DAY),'etudiant:3','Candidature enregistrée'),
( 4,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 7 DAY),'etudiant:3','Candidature enregistrée'),
( 4,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 6 DAY),'entreprise:2','Évaluation démarrée'),
( 5,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 6 DAY),'etudiant:3','Candidature enregistrée'),
( 6,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 5 DAY),'etudiant:3','Candidature enregistrée'),
( 6,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 4 DAY),'entreprise:6','Évaluation démarrée'),
( 7,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 12 DAY),'etudiant:9','Candidature enregistrée'),
( 7,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 11 DAY),'entreprise:4','Évaluation démarrée'),
( 7,'EN_COURS_EVALUATION','REFUSEE',DATE_SUB(NOW(), INTERVAL 10 DAY),'entreprise:4','Profil trop éloigné des besoins de la mission'),
( 8,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 10 DAY),'etudiant:11','Candidature enregistrée'),
( 8,'DEPOSEE','ANNULEE',DATE_SUB(NOW(), INTERVAL 9 DAY),'etudiant:11','Annulée par l''étudiante'),
( 9,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 4 DAY),'etudiant:13','Candidature enregistrée'),
( 9,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 3 DAY),'entreprise:3','Évaluation démarrée'),
(10,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 60 DAY),'etudiant:16','Candidature enregistrée'),
(10,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 58 DAY),'entreprise:3','Évaluation démarrée'),
(10,'EN_COURS_EVALUATION','ACCEPTEE',DATE_SUB(NOW(), INTERVAL 50 DAY),'entreprise:3','Profil retenu'),
(11,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 320 DAY),'etudiant:18','Candidature enregistrée'),
(11,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 318 DAY),'entreprise:7','Évaluation démarrée'),
(11,'EN_COURS_EVALUATION','ACCEPTEE',DATE_SUB(NOW(), INTERVAL 310 DAY),'entreprise:7','Profil retenu'),
(12,NULL,'DEPOSEE',DATE_SUB(NOW(), INTERVAL 15 DAY),'etudiant:19','Candidature enregistrée'),
(12,'DEPOSEE','EN_COURS_EVALUATION',DATE_SUB(NOW(), INTERVAL 14 DAY),'entreprise:2','Évaluation démarrée'),
(12,'EN_COURS_EVALUATION','ACCEPTEE',DATE_SUB(NOW(), INTERVAL 10 DAY),'entreprise:2','Profil retenu');

-- Chaque candidature de démonstration a sa soumission et ses étapes, comme si elle était passée par le processus.
INSERT INTO soumission (id, etudiant_id, offre_id, lettre_motivation, statut, cree_le)
SELECT id, etudiant_id, offre_id, lettre_motivation, 'ENREGISTREE', cree_le FROM candidature ORDER BY id;
UPDATE candidature SET soumission_id = id;

INSERT INTO etape_processus (soumission_id, etape, resultat, detail, date_etape, acteur)
SELECT c.id, e.etape, 'OK', e.detail, DATE_ADD(c.cree_le, INTERVAL e.ordre SECOND), 'processus'
FROM candidature c
CROSS JOIN (
  SELECT 1 AS ordre, 'RECEPTION' AS etape, 'Candidature reçue' AS detail UNION ALL
  SELECT 2, 'VERIFICATION_ETUDIANT', 'Étudiant actif (R1)' UNION ALL
  SELECT 3, 'VERIFICATION_OFFRE', 'Offre active, entreprise active, profil compatible (R2, R7)' UNION ALL
  SELECT 4, 'CONTROLE_CANDIDATURE', 'Date limite, places, doublon et limite de 5 respectés (R3 à R6)' UNION ALL
  SELECT 5, 'PASSERELLE_DECISION', 'Conditions satisfaites' UNION ALL
  SELECT 6, 'ENREGISTREMENT', 'Candidature enregistrée' UNION ALL
  SELECT 7, 'NOTIFICATION_DEPOT', 'Confirmation envoyée à l''étudiant' UNION ALL
  SELECT 8, 'TRANSMISSION_ENTREPRISE', 'Entreprise informée'
) e;

-- Étapes postérieures, reprises de l'historique des statuts.
INSERT INTO etape_processus (soumission_id, etape, resultat, detail, date_etape, acteur)
SELECT h.candidature_id,
       CASE h.nouveau_statut WHEN 'EN_COURS_EVALUATION' THEN 'DEBUT_EVALUATION' WHEN 'ANNULEE' THEN 'ANNULATION' ELSE 'DECISION_ENTREPRISE' END,
       IF(h.nouveau_statut = 'ANNULEE', 'INFO', 'OK'), h.commentaire, h.date_changement, h.acteur
FROM candidature_historique h WHERE h.ancien_statut IS NOT NULL;

INSERT INTO etape_processus (soumission_id, etape, resultat, detail, date_etape, acteur)
SELECT h.candidature_id, e.etape, 'OK', e.detail, DATE_ADD(h.date_changement, INTERVAL e.ordre SECOND), 'processus'
FROM candidature_historique h
JOIN (
  SELECT 'REFUSEE' AS statut, 1 AS ordre, 'NOTIFICATION_RESULTAT' AS etape, 'Refus notifié à l''étudiant' AS detail UNION ALL
  SELECT 'ACCEPTEE', 1, 'MISE_A_JOUR_PLACES', 'Place retirée de l''offre (R10)' UNION ALL
  SELECT 'ACCEPTEE', 2, 'NOTIFICATION_RESULTAT', 'Acceptation notifiée à l''étudiant' UNION ALL
  SELECT 'ACCEPTEE', 3, 'CREATION_STAGE', 'Dossier de stage créé (R10)' UNION ALL
  SELECT 'ACCEPTEE', 4, 'MISE_A_JOUR_ETUDIANT', 'Étudiant passé EN_STAGE (R10)'
) e ON e.statut = h.nouveau_statut;

