-- S6 Suivi des stages : schéma et données de démonstration.
SET NAMES utf8mb4;
USE soa_suivi;

CREATE TABLE stage (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidature_id        BIGINT NOT NULL UNIQUE COMMENT 'Unique : relancer la création de stage (R10) ne crée pas de doublon',
  etudiant_id           BIGINT NOT NULL,
  offre_id              BIGINT NOT NULL,
  entreprise_id         BIGINT NOT NULL,
  date_debut            DATE NOT NULL,
  date_fin              DATE NOT NULL,
  encadrant_entreprise  VARCHAR(120) NULL,
  encadrant_pedagogique VARCHAR(120) NULL,
  statut                ENUM('PLANIFIE','EN_COURS','TERMINE','INTERROMPU') NOT NULL DEFAULT 'PLANIFIE',
  cree_le               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_stage_dates CHECK (date_fin > date_debut)
);
CREATE INDEX idx_stage_etudiant ON stage (etudiant_id);
CREATE INDEX idx_stage_statut ON stage (statut);

CREATE TABLE stage_historique (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  stage_id        BIGINT NOT NULL,
  operation       VARCHAR(40) NOT NULL,
  detail          VARCHAR(500) NULL,
  date_operation  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acteur          VARCHAR(120) NOT NULL,
  CONSTRAINT fk_historique_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE CASCADE
);
CREATE INDEX idx_stage_historique ON stage_historique (stage_id, date_operation);

CREATE TABLE compte_rendu (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  stage_id    BIGINT NOT NULL,
  periode     VARCHAR(60) NOT NULL,
  contenu     TEXT NOT NULL,
  auteur      VARCHAR(120) NOT NULL,
  cree_le     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_compte_rendu_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE CASCADE
);
CREATE INDEX idx_compte_rendu_stage ON compte_rendu (stage_id, cree_le);

CREATE TABLE evaluation (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  stage_id    BIGINT NOT NULL,
  note        DECIMAL(4,2) NOT NULL,
  commentaire VARCHAR(1000) NULL,
  evaluateur  VARCHAR(120) NOT NULL,
  cree_le     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_evaluation_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE CASCADE,
  CONSTRAINT ck_evaluation_note CHECK (note >= 0 AND note <= 20)
);
CREATE INDEX idx_evaluation_stage ON evaluation (stage_id);

INSERT INTO stage (id, candidature_id, etudiant_id, offre_id, entreprise_id, date_debut, date_fin, encadrant_entreprise, encadrant_pedagogique, statut) VALUES
(1,10,16,15,3,DATE_SUB(CURDATE(), INTERVAL 30 DAY),DATE_ADD(CURDATE(), INTERVAL 138 DAY),'Samira Hakam','Pr. Nabil Essaadi','EN_COURS'),
(2,11,18,16,7,DATE_SUB(CURDATE(), INTERVAL 280 DAY),DATE_SUB(CURDATE(), INTERVAL 112 DAY),'Nadia Temsamani','Pr. Leila Ghazali','TERMINE'),
(3,12,19, 8,2,DATE_ADD(CURDATE(), INTERVAL 10 DAY),DATE_ADD(CURDATE(), INTERVAL 122 DAY),'Driss Lamrani','Pr. Nabil Essaadi','PLANIFIE');

INSERT INTO stage_historique (stage_id, operation, detail, date_operation, acteur) VALUES
(1,'CREATION','Dossier de stage créé après acceptation',DATE_SUB(NOW(), INTERVAL 50 DAY),'processus'),
(1,'CHANGEMENT_STATUT','PLANIFIE → EN_COURS',DATE_SUB(NOW(), INTERVAL 30 DAY),'responsable'),
(1,'COMPTE_RENDU','Compte rendu déposé : semaines 1 à 4',DATE_SUB(NOW(), INTERVAL 2 DAY),'Pr. Nabil Essaadi'),
(2,'CREATION','Dossier de stage créé après acceptation',DATE_SUB(NOW(), INTERVAL 310 DAY),'processus'),
(2,'CHANGEMENT_STATUT','PLANIFIE → EN_COURS',DATE_SUB(NOW(), INTERVAL 280 DAY),'responsable'),
(2,'COMPTE_RENDU','Compte rendu déposé : mi-parcours',DATE_SUB(NOW(), INTERVAL 196 DAY),'Pr. Leila Ghazali'),
(2,'CHANGEMENT_STATUT','EN_COURS → TERMINE',DATE_SUB(NOW(), INTERVAL 112 DAY),'responsable'),
(2,'EVALUATION','Note attribuée : 16,50/20',DATE_SUB(NOW(), INTERVAL 110 DAY),'Pr. Leila Ghazali'),
(3,'CREATION','Dossier de stage créé après acceptation',DATE_SUB(NOW(), INTERVAL 10 DAY),'processus');

INSERT INTO compte_rendu (stage_id, periode, contenu, auteur, cree_le) VALUES
(1,'Semaines 1 à 4','Prise en main du périmètre de conformité et ateliers de recueil des besoins avec les équipes métier.','Pr. Nabil Essaadi',DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2,'Mi-parcours','Premier modèle de prévision livré ; amélioration de 12 % par rapport à la méthode de référence.','Pr. Leila Ghazali',DATE_SUB(NOW(), INTERVAL 196 DAY));

INSERT INTO evaluation (stage_id, note, commentaire, evaluateur, cree_le) VALUES
(2,16.50,'Très bon stage : autonomie, rigueur et résultats exploitables par l''entreprise.','Pr. Leila Ghazali',DATE_SUB(NOW(), INTERVAL 110 DAY));
