package ma.inpt.stages.notifications;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ma.inpt.stages.common.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lance les rappels chaque jour à heure fixe. La même exécution est déclenchable à la main :
 * POST /api/v1/rappels/executer (bouton de l'espace responsable, utile pendant la démonstration).
 */
public final class PlanificateurRappels implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificateurRappels.class);

    private final ScheduledExecutorService executeur = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rappels-quotidiens");
        t.setDaemon(true);
        return t;
    });
    private final ServiceNotifications service;
    private final LocalTime heure;
    private final ZoneId fuseau;
    private final int jours;

    public PlanificateurRappels(ServiceNotifications service, LocalTime heure, ZoneId fuseau, int jours) {
        this.service = service;
        this.heure = heure;
        this.fuseau = fuseau;
        this.jours = jours;
    }

    /** Délai jusqu'au prochain passage : aujourd'hui si l'heure n'est pas passée, sinon demain. */
    static Duration delaiAvant(ZonedDateTime maintenant, LocalTime heure) {
        ZonedDateTime prochain = maintenant.with(heure).withSecond(0).withNano(0);
        if (!prochain.isAfter(maintenant)) {
            prochain = prochain.plusDays(1);
        }
        return Duration.between(maintenant, prochain);
    }

    public void demarrer() {
        Duration delai = delaiAvant(ZonedDateTime.now(fuseau), heure);
        executeur.scheduleAtFixedRate(this::executer, delai.toSeconds(), TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        LOG.info("Rappels planifiés chaque jour à {} ({}), prochain passage dans {} min", heure, fuseau, delai.toMinutes());
    }

    private void executer() {
        Correlation.definir("rappels-" + ZonedDateTime.now(fuseau).toLocalDate());
        try {
            service.executerRappels(jours);
        } catch (RuntimeException e) {
            // Une panne ponctuelle (service arrêté) ne doit pas annuler les passages suivants.
            LOG.error("Rappels quotidiens non exécutés : {}", e.getMessage());
        } finally {
            Correlation.effacer();
        }
    }

    @Override
    public void close() {
        executeur.shutdownNow();
    }
}
