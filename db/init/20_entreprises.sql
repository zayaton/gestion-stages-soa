-- S2 Gestion des entreprises : schéma et données de démonstration.
-- Toutes les entreprises, ICE et adresses sont fictifs ; les courriels n'arrivent que dans Mailpit.
SET NAMES utf8mb4;
USE soa_entreprises;

CREATE TABLE entreprise (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  raison_sociale     VARCHAR(150) NOT NULL,
  ice                CHAR(15)     NOT NULL UNIQUE,
  secteur            VARCHAR(80)  NOT NULL,
  adresse            VARCHAR(200) NOT NULL,
  ville              VARCHAR(60)  NOT NULL,
  email              VARCHAR(150) NOT NULL UNIQUE,
  telephone          VARCHAR(20)  NOT NULL,
  contact_nom        VARCHAR(120) NOT NULL,
  contact_fonction   VARCHAR(100) NOT NULL,
  contact_email      VARCHAR(150) NOT NULL,
  contact_telephone  VARCHAR(20)  NOT NULL,
  statut             ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  cree_le            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modifie_le         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_entreprise_ice CHECK (ice REGEXP '^[0-9]{15}$')
);
CREATE INDEX idx_entreprise_statut ON entreprise (statut);

INSERT INTO entreprise (id, raison_sociale, ice, secteur, adresse, ville, email, telephone, contact_nom, contact_fonction, contact_email, contact_telephone, statut) VALUES
(1,'NovaPay Maghreb','001526374000081','Fintech','12 boulevard Zerktouni','Casablanca','contact@novapay-maghreb.ma','0522481020','Ghita Sentissi','Responsable RH','g.sentissi@novapay-maghreb.ma','0661002030','ACTIVE'),
(2,'Ifrane Réseaux Télécom','001638495000072','Télécommunications','5 avenue Annakhil, Hay Riad','Rabat','contact@ifrane-reseaux.ma','0537715540','Driss Lamrani','Directeur technique','d.lamrani@ifrane-reseaux.ma','0661003040','ACTIVE'),
(3,'Zellige Conseil','001740516000063','Conseil','48 rue Ibnou Majid','Casablanca','contact@zellige-conseil.ma','0522369070','Samira Hakam','Associée','s.hakam@zellige-conseil.ma','0661004050','ACTIVE'),
(4,'SoukDar Commerce','001852637000054','E-commerce','Zone industrielle Sidi Ghanem','Marrakech','contact@soukdar-commerce.ma','0524335510','Karim Ouhaddou','Responsable produit','k.ouhaddou@soukdar-commerce.ma','0661005060','ACTIVE'),
(5,'Oued Solaire Énergies','001964758000045','Énergie','Parc Haliopolis, lot 17','Agadir','contact@oued-solaire.ma','0528842290','Fatima Zahra Aït Lahcen','Cheffe de projet','f.aitlahcen@oued-solaire.ma','0661006070','ACTIVE'),
(6,'Agence Numérique du Bouregreg','002076879000036','Secteur public','3 avenue de la Vallée','Rabat','contact@anb-bouregreg.ma','0537208800','Abdelilah Skalli','Chef de division SI','a.skalli@anb-bouregreg.ma','0661007080','ACTIVE'),
(7,'Détroit Data Logistique','002188990000027','Logistique','Zone franche, îlot 9','Tanger','contact@detroit-data.ma','0539394410','Nadia Temsamani','Responsable données','n.temsamani@detroit-data.ma','0661008090','ACTIVE'),
(8,'Médina Systèmes','002291012000018','Services informatiques','22 route d''Imouzzer','Fès','contact@medina-systemes.ma','0535654120','Hassan Bennis','Gérant','h.bennis@medina-systemes.ma','0661009010','INACTIVE');
