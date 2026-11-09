package ma.inpt.stages.portail;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.ClientRest;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.EntreprisesService;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.EtudiantsService;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.OffresService;

/**
 * Les six services vus par le portail : clients SOAP générés par wsimport (S1, S2, S3) et clients REST (S4, S5, S6).
 * Les erreurs des services sont reprises telles quelles (code, message, détails) ; une panne devient 503.
 */
public class Services {

    /** Appel SOAP ; ses fautes typées sont traduites par {@link #soap}. */
    @FunctionalInterface
    public interface AppelSoap<T> {
        T executer() throws Exception;
    }

    public final EtudiantsPortType etudiants;
    public final EntreprisesPortType entreprises;
    public final OffresPortType offres;
    public final ClientRest candidatures;
    public final ClientRest notifications;
    public final ClientRest suivi;

    public Services(Configuration config) {
        etudiants = ClientSoap.configurer(new EtudiantsService().getEtudiantsPort(), config.valeur("soa.ws.url.etudiants"), config);
        entreprises = ClientSoap.configurer(new EntreprisesService().getEntreprisesPort(), config.valeur("soa.ws.url.entreprises"), config);
        offres = ClientSoap.configurer(new OffresService().getOffresPort(), config.valeur("soa.ws.url.offres"), config);
        String cle = config.valeur("soa.api.cle.client");
        candidatures = new ClientRest(config.valeur("soa.rest.url.candidatures"), cle, "candidatures", config);
        notifications = new ClientRest(config.valeur("soa.rest.url.notifications"), cle, "notifications", config);
        suivi = new ClientRest(config.valeur("soa.rest.url.suivi"), cle, "suivi", config);
    }

    /** Pour les tests : ports et clients fournis. */
    protected Services(EtudiantsPortType etudiants, EntreprisesPortType entreprises, OffresPortType offres,
                       ClientRest candidatures, ClientRest notifications, ClientRest suivi) {
        this.etudiants = etudiants;
        this.entreprises = entreprises;
        this.offres = offres;
        this.candidatures = candidatures;
        this.notifications = notifications;
        this.suivi = suivi;
    }

    public static <T> T soap(String service, AppelSoap<T> appel) {
        try {
            return appel.executer();
        } catch (ErreurMetier e) {
            throw e;
        } catch (Exception e) {
            throw traduire(service, e);
        }
    }

    /** Faute SOAP typée (XxxFault avec getFaultInfo()) vers la même erreur côté portail. */
    @SuppressWarnings("unchecked")
    static ErreurMetier traduire(String service, Exception e) {
        String nom = e.getClass().getSimpleName();
        if (nom.endsWith("Fault")) {
            try {
                Object info = e.getClass().getMethod("getFaultInfo").invoke(e);
                String code = (String) info.getClass().getMethod("getCode").invoke(info);
                String message = (String) info.getClass().getMethod("getMessage").invoke(info);
                List<String> details = (List<String>) info.getClass().getMethod("getDetail").invoke(info);
                ErreurMetier.Type type = nom.endsWith("IntrouvableFault") ? ErreurMetier.Type.INTROUVABLE
                        : "DonneesInvalidesFault".equals(nom) ? ErreurMetier.Type.DONNEES_INVALIDES
                        : "ConflitFault".equals(nom) ? ErreurMetier.Type.CONFLIT
                        : ErreurMetier.Type.INDISPONIBLE;
                return ErreurMetier.de(type, code, message, new ArrayList<>(details));
            } catch (ReflectiveOperationException | ClassCastException ignore) {
                // Faute inattendue : traitée comme une panne du service.
            }
        }
        return ErreurMetier.indisponible("SERVICE_" + service.toUpperCase() + "_INDISPONIBLE",
                "Le service " + service + " est momentanément indisponible. Réessayez dans un instant.");
    }

    /** Corps d'une réponse REST réussie, ou la même erreur que le service (code HTTP, code, message, détails). */
    public static JsonNode rest(ClientRest.Reponse r) {
        if (r.succes()) {
            return r.corps();
        }
        ErreurMetier.Type type = switch (r.statut()) {
            case 400 -> ErreurMetier.Type.REQUETE_INVALIDE;
            case 404 -> ErreurMetier.Type.INTROUVABLE;
            case 409 -> ErreurMetier.Type.CONFLIT;
            case 422 -> ErreurMetier.Type.DONNEES_INVALIDES;
            default -> ErreurMetier.Type.INDISPONIBLE;
        };
        List<String> details = new ArrayList<>();
        r.corps().path("details").forEach(d -> details.add(d.asText()));
        String code = r.code().isEmpty() ? "ERREUR_SERVICE" : r.code();
        String message = r.message().isEmpty() ? "Le service a refusé la demande." : r.message();
        throw ErreurMetier.de(type, code, message, details);
    }
}
