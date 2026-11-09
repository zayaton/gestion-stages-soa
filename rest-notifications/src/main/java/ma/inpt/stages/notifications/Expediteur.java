package ma.inpt.stages.notifications;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import ma.inpt.stages.common.Configuration;

/** Envoi réel d'un courriel. En démonstration, le serveur SMTP est Mailpit : rien ne sort de la machine. */
public interface Expediteur {

    /** Échec d'envoi : la notification est alors enregistrée avec le statut ECHEC. */
    final class EchecEnvoi extends Exception {
        public EchecEnvoi(String message) {
            super(message);
        }
    }

    void envoyer(String destinataire, Modeles.Message message, String correlationId) throws EchecEnvoi;

    /** Envoi SMTP avec Jakarta Mail : message multipart, version texte et version HTML. */
    final class Smtp implements Expediteur {

        private final Session session;
        private final String adresseExpediteur;
        private final String nomExpediteur;

        public Smtp(Configuration config) {
            Properties p = new Properties();
            p.put("mail.smtp.host", config.valeur("notifications.smtp.hote"));
            p.put("mail.smtp.port", config.valeur("notifications.smtp.port"));
            p.put("mail.smtp.connectiontimeout", config.valeur("notifications.smtp.delai.ms"));
            p.put("mail.smtp.timeout", config.valeur("notifications.smtp.delai.ms"));
            p.put("mail.smtp.writetimeout", config.valeur("notifications.smtp.delai.ms"));
            this.session = Session.getInstance(p);
            this.adresseExpediteur = config.valeur("notifications.expediteur.adresse");
            this.nomExpediteur = config.valeur("notifications.expediteur.nom");
        }

        @Override
        public void envoyer(String destinataire, Modeles.Message message, String correlationId) throws EchecEnvoi {
            try {
                MimeMessage mime = new MimeMessage(session);
                mime.setFrom(new InternetAddress(adresseExpediteur, nomExpediteur, "UTF-8"));
                mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire, true));
                mime.setSubject(message.sujet(), "UTF-8");
                if (correlationId != null) {
                    // Permet de retrouver dans Mailpit le courriel d'une candidature donnée.
                    mime.setHeader("X-Correlation-Id", correlationId);
                }
                MimeBodyPart texte = new MimeBodyPart();
                texte.setText(message.texte(), "UTF-8");
                MimeBodyPart html = new MimeBodyPart();
                html.setContent(message.html(), "text/html; charset=UTF-8");
                MimeMultipart corps = new MimeMultipart("alternative");
                corps.addBodyPart(texte);
                corps.addBodyPart(html);
                mime.setContent(corps);
                Transport.send(mime);
            } catch (MessagingException | UnsupportedEncodingException e) {
                throw new EchecEnvoi("Envoi SMTP impossible : " + e.getMessage());
            }
        }
    }
}
