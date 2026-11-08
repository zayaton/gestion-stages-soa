-- Portail : comptes et sessions. Mots de passe hachés en BCrypt.
-- Comptes de démonstration uniquement (identifiants listés dans le README) :
--   étudiants    : Etudiant@2026
--   entreprises  : Entreprise@2026
--   responsable  : Responsable@2026
-- Tous les étudiants et entreprises ont un compte : chaque scénario de rejet se joue avec un étudiant différent.
SET NAMES utf8mb4;
USE soa_portail;

CREATE TABLE compte (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  identifiant       VARCHAR(150) NOT NULL UNIQUE,
  mot_de_passe_hash VARCHAR(100) NOT NULL,
  role              ENUM('ETUDIANT','ENTREPRISE','RESPONSABLE') NOT NULL,
  reference_id      BIGINT NULL COMMENT 'Identifiant S1 ou S2 selon le rôle ; NULL pour le responsable',
  actif             BOOLEAN NOT NULL DEFAULT TRUE,
  cree_le           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_compte_reference CHECK ((role = 'RESPONSABLE') = (reference_id IS NULL))
);

CREATE TABLE session (
  jeton_hash CHAR(64) PRIMARY KEY COMMENT 'SHA-256 du jeton : un vol de la base ne donne pas de session valide',
  compte_id  BIGINT NOT NULL,
  cree_le    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expire_le  DATETIME NOT NULL,
  CONSTRAINT fk_session_compte FOREIGN KEY (compte_id) REFERENCES compte (id) ON DELETE CASCADE
);
CREATE INDEX idx_session_expiration ON session (expire_le);

SET @etudiant    = '$2a$10$wVf.Qs8sUG3921ffJJaP1.ZOVRyXBjP7ATBTPeeCIbJPBwIJ7pstC';
SET @entreprise  = '$2a$10$oJ5tlMLkneAFaZfVIyg6heakzkCYAzqB.CIo1IGAm2.MB/aLQZfly';
SET @responsable = '$2a$10$aDWc8vqSbTocuOd1xKeLIuyqC6OvSYOMKnkPvZV5Xq78sYR0UpcC2';

INSERT INTO compte (identifiant, mot_de_passe_hash, role, reference_id) VALUES
('responsable@inpt.ac.ma',@responsable,'RESPONSABLE',NULL),
('salma.elidrissi@inpt.ac.ma',@etudiant,'ETUDIANT',1),
('youssef.benjelloun@inpt.ac.ma',@etudiant,'ETUDIANT',2),
('imane.alaoui@inpt.ac.ma',@etudiant,'ETUDIANT',3),
('mehdi.tazi@inpt.ac.ma',@etudiant,'ETUDIANT',4),
('nour.chraibi@inpt.ac.ma',@etudiant,'ETUDIANT',5),
('anas.ouazzani@inpt.ac.ma',@etudiant,'ETUDIANT',6),
('kawtar.berrada@inpt.ac.ma',@etudiant,'ETUDIANT',7),
('hamza.amrani@inpt.ac.ma',@etudiant,'ETUDIANT',8),
('lina.fassifihri@inpt.ac.ma',@etudiant,'ETUDIANT',9),
('omar.bennani@inpt.ac.ma',@etudiant,'ETUDIANT',10),
('zineb.lahlou@inpt.ac.ma',@etudiant,'ETUDIANT',11),
('ayoub.kettani@inpt.ac.ma',@etudiant,'ETUDIANT',12),
('rania.sefrioui@inpt.ac.ma',@etudiant,'ETUDIANT',13),
('othmane.naciri@inpt.ac.ma',@etudiant,'ETUDIANT',14),
('meryem.mansouri@inpt.ac.ma',@etudiant,'ETUDIANT',15),
('ilyas.zemmouri@inpt.ac.ma',@etudiant,'ETUDIANT',16),
('sara.bouzidi@inpt.ac.ma',@etudiant,'ETUDIANT',17),
('hajar.filali@inpt.ac.ma',@etudiant,'ETUDIANT',18),
('reda.cherkaoui@inpt.ac.ma',@etudiant,'ETUDIANT',19),
('aicha.tahiri@inpt.ac.ma',@etudiant,'ETUDIANT',20),
('contact@novapay-maghreb.ma',@entreprise,'ENTREPRISE',1),
('contact@ifrane-reseaux.ma',@entreprise,'ENTREPRISE',2),
('contact@zellige-conseil.ma',@entreprise,'ENTREPRISE',3),
('contact@soukdar-commerce.ma',@entreprise,'ENTREPRISE',4),
('contact@oued-solaire.ma',@entreprise,'ENTREPRISE',5),
('contact@anb-bouregreg.ma',@entreprise,'ENTREPRISE',6),
('contact@detroit-data.ma',@entreprise,'ENTREPRISE',7),
('contact@medina-systemes.ma',@entreprise,'ENTREPRISE',8);
