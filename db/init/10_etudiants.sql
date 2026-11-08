-- S1 Gestion des étudiants : schéma et données de démonstration (fictives).
SET NAMES utf8mb4;
USE soa_etudiants;

CREATE TABLE etudiant (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  matricule   VARCHAR(20)  NOT NULL UNIQUE,
  nom         VARCHAR(80)  NOT NULL,
  prenom      VARCHAR(80)  NOT NULL,
  filiere     VARCHAR(30)  NOT NULL,
  niveau      ENUM('1A','2A','3A') NOT NULL,
  email       VARCHAR(150) NOT NULL UNIQUE,
  telephone   VARCHAR(20)  NOT NULL,
  ville       VARCHAR(60)  NOT NULL,
  statut      ENUM('ACTIF','SUSPENDU','EN_STAGE','DIPLOME') NOT NULL DEFAULT 'ACTIF',
  cree_le     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_etudiant_matricule CHECK (matricule REGEXP '^INPT-[0-9]{4}-[0-9]{4}$')
);
CREATE INDEX idx_etudiant_filiere_niveau ON etudiant (filiere, niveau);
CREATE INDEX idx_etudiant_statut ON etudiant (statut);

-- Compétences déclarées : base du score indicatif calculé par S3 (règle R7).
CREATE TABLE etudiant_competence (
  etudiant_id BIGINT NOT NULL,
  competence  VARCHAR(60) NOT NULL,
  PRIMARY KEY (etudiant_id, competence),
  CONSTRAINT fk_competence_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id) ON DELETE CASCADE
);

INSERT INTO etudiant (id, matricule, nom, prenom, filiere, niveau, email, telephone, ville, statut) VALUES
( 1,'INPT-2024-0001','El Idrissi','Salma','AMOA','2A','salma.elidrissi@inpt.ac.ma','0661234512','Rabat','ACTIF'),
( 2,'INPT-2024-0002','Benjelloun','Youssef','AMOA','2A','youssef.benjelloun@inpt.ac.ma','0662345623','Casablanca','ACTIF'),
( 3,'INPT-2024-0003','Alaoui','Imane','ASEDS','2A','imane.alaoui@inpt.ac.ma','0663456734','Fès','ACTIF'),
( 4,'INPT-2023-0004','Tazi','Mehdi','DATA','3A','mehdi.tazi@inpt.ac.ma','0664567845','Rabat','ACTIF'),
( 5,'INPT-2025-0005','Chraibi','Nour','AMOA','1A','nour.chraibi@inpt.ac.ma','0665678956','Salé','ACTIF'),
( 6,'INPT-2024-0006','Ouazzani','Anas','ICCN','2A','anas.ouazzani@inpt.ac.ma','0666789067','Tanger','ACTIF'),
( 7,'INPT-2023-0007','Berrada','Kawtar','Cloud','3A','kawtar.berrada@inpt.ac.ma','0667890178','Casablanca','ACTIF'),
( 8,'INPT-2024-0008','Amrani','Hamza','Smart ICT','2A','hamza.amrani@inpt.ac.ma','0668901289','Agadir','ACTIF'),
( 9,'INPT-2024-0009','Fassi Fihri','Lina','SESNum','2A','lina.fassifihri@inpt.ac.ma','0669012390','Fès','ACTIF'),
(10,'INPT-2024-0010','Bennani','Omar','DATA','2A','omar.bennani@inpt.ac.ma','0660123401','Marrakech','ACTIF'),
(11,'INPT-2023-0011','Lahlou','Zineb','ASEDS','3A','zineb.lahlou@inpt.ac.ma','0771234512','Rabat','ACTIF'),
(12,'INPT-2025-0012','Kettani','Ayoub','Cloud','1A','ayoub.kettani@inpt.ac.ma','0772345623','Tanger','ACTIF'),
(13,'INPT-2023-0013','Sefrioui','Rania','SESNum','3A','rania.sefrioui@inpt.ac.ma','0773456734','Casablanca','ACTIF'),
(14,'INPT-2025-0014','Naciri','Othmane','ICCN','1A','othmane.naciri@inpt.ac.ma','0774567845','Rabat','ACTIF'),
(15,'INPT-2023-0015','Mansouri','Meryem','Smart ICT','3A','meryem.mansouri@inpt.ac.ma','0775678956','Marrakech','ACTIF'),
(16,'INPT-2023-0016','Zemmouri','Ilyas','AMOA','3A','ilyas.zemmouri@inpt.ac.ma','0776789067','Casablanca','EN_STAGE'),
(17,'INPT-2024-0017','Bouzidi','Sara','ASEDS','2A','sara.bouzidi@inpt.ac.ma','0777890178','Agadir','SUSPENDU'),
(18,'INPT-2022-0018','Filali','Hajar','DATA','3A','hajar.filali@inpt.ac.ma','0778901289','Rabat','DIPLOME'),
(19,'INPT-2024-0019','Cherkaoui','Reda','Cloud','2A','reda.cherkaoui@inpt.ac.ma','0779012390','Kénitra','EN_STAGE'),
(20,'INPT-2025-0020','Tahiri','Aïcha','AMOA','1A','aicha.tahiri@inpt.ac.ma','0770123401','Meknès','ACTIF');

INSERT INTO etudiant_competence (etudiant_id, competence) VALUES
(1,'Analyse fonctionnelle'),(1,'BPMN'),(1,'UML'),(1,'SQL'),
(2,'Analyse fonctionnelle'),(2,'Gestion de projet'),(2,'SQL'),
(3,'Java'),(3,'SQL'),(3,'Docker'),(3,'Spring'),
(4,'Python'),(4,'SQL'),(4,'Spark'),(4,'Machine Learning'),
(5,'UML'),(5,'SQL'),
(6,'Réseaux IP'),(6,'Linux'),(6,'5G'),
(7,'Docker'),(7,'Kubernetes'),(7,'AWS'),(7,'Linux'),
(8,'IoT'),(8,'Python'),(8,'Systèmes embarqués'),
(9,'JavaScript'),(9,'React'),(9,'UX Design'),
(10,'Python'),(10,'SQL'),(10,'Power BI'),
(11,'Java'),(11,'Sécurité applicative'),(11,'Spring'),
(12,'Linux'),(12,'Docker'),
(13,'JavaScript'),(13,'Analyse fonctionnelle'),(13,'Gestion de projet'),
(14,'Réseaux IP'),(14,'Linux'),
(15,'IoT'),(15,'Systèmes embarqués'),(15,'Python'),
(16,'Analyse fonctionnelle'),(16,'BPMN'),(16,'Power BI'),
(17,'Java'),(17,'SQL'),
(18,'Python'),(18,'Spark'),(18,'SQL'),
(19,'Docker'),(19,'Kubernetes'),(19,'Linux'),
(20,'UML'),(20,'Gestion de projet');
