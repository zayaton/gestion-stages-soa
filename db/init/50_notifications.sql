-- S5 Notifications : schéma seul. Aucune donnée de démonstration : chaque ligne doit correspondre
-- à un courriel réellement envoyé, donc visible dans Mailpit.
SET NAMES utf8mb4;
USE soa_notifications;

CREATE TABLE notification (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  type           ENUM('CONFIRMATION_DEPOT','CANDIDATURE_REJETEE','ACCEPTATION','REFUS',
                      'DEMANDE_COMPLEMENT','NOUVELLE_CANDIDATURE_ENTREPRISE','RAPPEL_DATE_LIMITE',
                      'ALERTE_RESPONSABLE') NOT NULL,
  destinataire   VARCHAR(150) NOT NULL,
  sujet          VARCHAR(255) NOT NULL,
  corps          MEDIUMTEXT NOT NULL,
  date_envoi     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  statut         ENUM('ENVOYEE','ECHEC') NOT NULL,
  erreur         VARCHAR(500) NULL,
  candidature_id BIGINT NULL,
  offre_id       BIGINT NULL,
  correlation_id VARCHAR(64) NULL
);
CREATE INDEX idx_notification_destinataire ON notification (destinataire, date_envoi);
-- Évite d'envoyer deux fois le même rappel : destinataire + offre + type.
CREATE INDEX idx_notification_rappel ON notification (type, offre_id, destinataire);
