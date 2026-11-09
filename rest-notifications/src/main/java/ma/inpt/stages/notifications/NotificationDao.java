package ma.inpt.stages.notifications;

import java.util.List;
import java.util.Optional;
import ma.inpt.stages.notifications.Modeles.Notification;

/** Accès aux données du service S5. */
public interface NotificationDao {

    record Filtre(String destinataire, Long candidatureId, String type, int limite) {
    }

    Notification inserer(Notification notification);

    Optional<Notification> trouver(long id);

    List<Notification> lister(Filtre filtre);

    /** Vrai si un rappel a déjà été envoyé avec succès à ce destinataire pour cette offre. */
    boolean rappelDejaEnvoye(long offreId, String destinataire);
}
