-- S3 Gestion des offres : schéma et données de démonstration.
-- Les dates sont relatives au jour de chargement : les scénarios restent valables après reset-db.ps1.
SET NAMES utf8mb4;
USE soa_offres;

CREATE TABLE offre (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  titre           VARCHAR(150) NOT NULL,
  description     TEXT NOT NULL,
  duree_semaines  INT NOT NULL,
  date_limite     DATE NOT NULL,
  nombre_places   INT NOT NULL,
  niveau_minimum  ENUM('1A','2A','3A') NOT NULL,
  ville           VARCHAR(60) NOT NULL,
  entreprise_id   BIGINT NOT NULL COMMENT 'Identifiant S2 : pas de clé étrangère entre schémas',
  statut          ENUM('BROUILLON','ACTIVE','POURVUE','CLOTUREE') NOT NULL DEFAULT 'BROUILLON',
  cree_le         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_offre_places CHECK (nombre_places >= 0),
  CONSTRAINT ck_offre_duree CHECK (duree_semaines > 0)
);
CREATE INDEX idx_offre_entreprise ON offre (entreprise_id);
CREATE INDEX idx_offre_statut_limite ON offre (statut, date_limite);

CREATE TABLE offre_competence (
  offre_id   BIGINT NOT NULL,
  competence VARCHAR(60) NOT NULL,
  PRIMARY KEY (offre_id, competence),
  CONSTRAINT fk_competence_offre FOREIGN KEY (offre_id) REFERENCES offre (id) ON DELETE CASCADE
);

CREATE TABLE offre_filiere (
  offre_id BIGINT NOT NULL,
  filiere  VARCHAR(30) NOT NULL,
  PRIMARY KEY (offre_id, filiere),
  CONSTRAINT fk_filiere_offre FOREIGN KEY (offre_id) REFERENCES offre (id) ON DELETE CASCADE
);

INSERT INTO offre (id, titre, description, duree_semaines, date_limite, nombre_places, niveau_minimum, ville, entreprise_id, statut) VALUES
( 1,'Stage AMOA – Paiement mobile','Recueil des besoins, rédaction des spécifications et recette d''une application de paiement mobile.',12,DATE_ADD(CURDATE(), INTERVAL 30 DAY),2,'2A','Casablanca',1,'ACTIVE'),
( 2,'Analyste fonctionnel – Refonte SI','Accompagnement d''un client dans la refonte de son système d''information : cartographie et modélisation des processus.',16,DATE_ADD(CURDATE(), INTERVAL 25 DAY),3,'2A','Casablanca',3,'ACTIVE'),
( 3,'Développeur back-end Java','Conception et développement de micro-services de gestion des transactions.',12,DATE_ADD(CURDATE(), INTERVAL 20 DAY),2,'2A','Casablanca',1,'ACTIVE'),
( 4,'Ingénieur données – Flux logistiques','Mise en place de pipelines de données pour le suivi des flux portuaires.',20,DATE_ADD(CURDATE(), INTERVAL 28 DAY),2,'2A','Tanger',7,'ACTIVE'),
( 5,'Analyste données – Comportement client','Analyse des parcours d''achat et construction de tableaux de bord.',10,DATE_ADD(CURDATE(), INTERVAL 2 DAY),2,'2A','Marrakech',4,'ACTIVE'),
( 6,'Assistant ingénieur réseaux 5G','Participation aux campagnes de mesure et à l''optimisation du réseau.',8,DATE_ADD(CURDATE(), INTERVAL 3 DAY),1,'1A','Rabat',2,'ACTIVE'),
( 7,'Développeur web – Place de marché','Développement de nouvelles fonctionnalités de la plateforme de vente en ligne.',12,DATE_ADD(CURDATE(), INTERVAL 18 DAY),2,'2A','Marrakech',4,'ACTIVE'),
( 8,'Ingénieur DevOps Cloud','Automatisation des déploiements et supervision des plateformes.',16,DATE_ADD(CURDATE(), INTERVAL 22 DAY),1,'2A','Rabat',2,'ACTIVE'),
( 9,'IoT – Supervision de centrales solaires','Collecte et exploitation des données de capteurs sur site.',14,DATE_ADD(CURDATE(), INTERVAL 35 DAY),2,'2A','Agadir',5,'ACTIVE'),
(10,'Transformation numérique des services publics','Étude et accompagnement de la dématérialisation de procédures administratives.',12,DATE_ADD(CURDATE(), INTERVAL 40 DAY),4,'1A','Rabat',6,'ACTIVE'),
(11,'Sécurité des systèmes d''information','Audit de sécurité et plan de remédiation (offre en cours de rédaction).',16,DATE_ADD(CURDATE(), INTERVAL 45 DAY),1,'3A','Rabat',6,'BROUILLON'),
(12,'Développeur applications métier','Maintenance évolutive d''applications métier.',12,DATE_ADD(CURDATE(), INTERVAL 30 DAY),2,'1A','Fès',8,'ACTIVE'),
(13,'Consultant décisionnel junior','Conception de rapports décisionnels pour un client du secteur bancaire.',12,DATE_SUB(CURDATE(), INTERVAL 5 DAY),2,'1A','Casablanca',3,'ACTIVE'),
(14,'Chef de projet junior – Paiement','Suivi de projets d''intégration avec les partenaires bancaires.',12,DATE_ADD(CURDATE(), INTERVAL 15 DAY),0,'1A','Casablanca',1,'ACTIVE'),
(15,'Consultant AMOA – Secteur bancaire','Assistance à maîtrise d''ouvrage sur un projet de conformité.',24,DATE_SUB(CURDATE(), INTERVAL 45 DAY),0,'2A','Casablanca',3,'POURVUE'),
(16,'Data scientist – Prévision de la demande','Modèles de prévision des volumes de fret.',24,DATE_SUB(CURDATE(), INTERVAL 300 DAY),0,'3A','Tanger',7,'CLOTUREE');

INSERT INTO offre_filiere (offre_id, filiere) VALUES
(1,'AMOA'),(1,'ASEDS'),
(2,'AMOA'),(2,'SESNum'),
(3,'ASEDS'),(3,'Cloud'),
(4,'DATA'),(4,'Cloud'),
(5,'DATA'),(5,'AMOA'),(5,'ASEDS'),
(6,'ICCN'),(6,'Smart ICT'),
(7,'ASEDS'),(7,'SESNum'),
(8,'Cloud'),(8,'ASEDS'),
(9,'Smart ICT'),(9,'ICCN'),(9,'ASEDS'),
(10,'AMOA'),(10,'SESNum'),(10,'ASEDS'),
(11,'ASEDS'),(11,'ICCN'),
(12,'AMOA'),(12,'ASEDS'),
(13,'AMOA'),(13,'DATA'),
(14,'AMOA'),(14,'ASEDS'),
(15,'AMOA'),
(16,'DATA');

INSERT INTO offre_competence (offre_id, competence) VALUES
(1,'Analyse fonctionnelle'),(1,'BPMN'),(1,'SQL'),
(2,'Analyse fonctionnelle'),(2,'BPMN'),(2,'Gestion de projet'),
(3,'Java'),(3,'Spring'),(3,'SQL'),(3,'Docker'),
(4,'Python'),(4,'Spark'),(4,'SQL'),
(5,'SQL'),(5,'Power BI'),(5,'Python'),
(6,'Réseaux IP'),(6,'5G'),
(7,'JavaScript'),(7,'React'),(7,'Java'),
(8,'Docker'),(8,'Kubernetes'),(8,'Linux'),
(9,'IoT'),(9,'Python'),(9,'Systèmes embarqués'),
(10,'Analyse fonctionnelle'),(10,'Gestion de projet'),(10,'UML'),
(11,'Sécurité applicative'),(11,'Linux'),
(12,'Java'),(12,'SQL'),
(13,'SQL'),(13,'Power BI'),
(14,'Gestion de projet'),(14,'UML'),
(15,'Analyse fonctionnelle'),(15,'BPMN'),
(16,'Python'),(16,'Machine Learning');
