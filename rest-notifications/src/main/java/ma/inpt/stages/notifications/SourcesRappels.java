package ma.inpt.stages.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.ClientRest;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.EntreprisesService;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.EtudiantsService;
import ma.inpt.stages.contrats.etudiants.FiltreEtudiants;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.OffresService;
import ma.inpt.stages.notifications.Modeles.EtudiantActif;
import ma.inpt.stages.notifications.Modeles.OffreOuverte;

/**
 * Données nécessaires aux rappels, obtenues auprès des services qui les possèdent :
 * S3 (offres), S1 (étudiants), S2 (entreprises), S4 (candidatures). S5 ne lit jamais leurs schémas.
 */
public interface SourcesRappels {

    List<OffreOuverte> offresOuvertes();

    List<EtudiantActif> etudiantsActifs();

    /** Étudiants ayant une candidature non annulée à cette offre. */
    Set<Long> candidats(long offreId);

    String raisonSociale(long entrepriseId);

    final class Distantes implements SourcesRappels {

        private final OffresPortType offres;
        private final EtudiantsPortType etudiants;
        private final EntreprisesPortType entreprises;
        private final ClientRest candidatures;

        public Distantes(Configuration config) {
            offres = ClientSoap.configurer(new OffresService().getOffresPort(), config.valeur("soa.ws.url.offres"), config);
            etudiants = ClientSoap.configurer(new EtudiantsService().getEtudiantsPort(), config.valeur("soa.ws.url.etudiants"), config);
            entreprises = ClientSoap.configurer(new EntreprisesService().getEntreprisesPort(), config.valeur("soa.ws.url.entreprises"), config);
            candidatures = new ClientRest(config.valeur("soa.rest.url.candidatures"), config.valeur("soa.api.cle.client"), "candidatures", config);
        }

        private static ErreurMetier panne(String service) {
            return ErreurMetier.indisponible("SERVICE_" + service.toUpperCase() + "_INDISPONIBLE",
                    "Le service " + service + " est momentanément indisponible : aucun rappel n'a été envoyé.");
        }

        @Override
        public List<OffreOuverte> offresOuvertes() {
            FiltreOffres filtre = new FiltreOffres();
            filtre.setOuvertesSeulement(true);
            try {
                return offres.listerOffres(filtre).stream()
                        .map(o -> new OffreOuverte(o.getId(), o.getTitre(), Dates.versLocalDate(o.getDateLimite()), List.copyOf(o.getFiliere()),
                                o.getNiveauMinimum(), o.getEntrepriseId()))
                        .toList();
            } catch (Exception e) {
                throw panne("offres");
            }
        }

        @Override
        public List<EtudiantActif> etudiantsActifs() {
            FiltreEtudiants filtre = new FiltreEtudiants();
            filtre.setStatut("ACTIF");
            try {
                return etudiants.listerEtudiants(filtre).stream()
                        .map(e -> new EtudiantActif(e.getId(), e.getPrenom() + " " + e.getNom(), e.getEmail(), e.getFiliere(), e.getNiveau()))
                        .toList();
            } catch (Exception e) {
                throw panne("etudiants");
            }
        }

        @Override
        public Set<Long> candidats(long offreId) {
            ClientRest.Reponse r;
            try {
                r = candidatures.get("/offres/" + offreId + "/candidatures");
            } catch (ErreurMetier e) {
                throw panne("candidatures");
            }
            if (!r.succes() || !r.corps().isArray()) {
                throw panne("candidatures");
            }
            Set<Long> ids = new HashSet<>();
            for (JsonNode c : r.corps()) {
                if (!"ANNULEE".equals(c.path("statut").asText())) {
                    ids.add(c.path("etudiantId").asLong());
                }
            }
            return ids;
        }

        @Override
        public String raisonSociale(long entrepriseId) {
            try {
                String nom = entreprises.verifierEntreprise(entrepriseId).getRaisonSociale();
                return nom == null ? "l'entreprise" : nom;
            } catch (Exception e) {
                throw panne("entreprises");
            }
        }
    }

    /** Fenêtre des rappels : offres dont la date limite tombe entre aujourd'hui et aujourd'hui + jours. */
    static boolean dansLaFenetre(LocalDate dateLimite, LocalDate aujourdhui, int jours) {
        return !dateLimite.isBefore(aujourdhui) && !dateLimite.isAfter(aujourdhui.plusDays(jours));
    }
}
