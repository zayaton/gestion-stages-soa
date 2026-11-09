package ma.inpt.stages.candidatures;

import jakarta.xml.ws.WebServiceException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.ClientRest;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.EntreprisesService;
import ma.inpt.stages.contrats.entreprises.VerificationEntreprise;
import ma.inpt.stages.contrats.etudiants.Etudiant;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.EtudiantsService;
import ma.inpt.stages.contrats.etudiants.VerificationEtudiant;
import ma.inpt.stages.contrats.offres.Disponibilite;
import ma.inpt.stages.contrats.offres.Offre;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.OffresService;

/** Appels réels : SOAP vers S1, S2, S3 (clients générés par wsimport), REST vers S5 et S6. */
public final class PartenairesDistants implements Partenaires {

    private final EtudiantsPortType etudiants;
    private final EntreprisesPortType entreprises;
    private final OffresPortType offres;
    private final ClientRest notifications;
    private final ClientRest suivi;

    public PartenairesDistants(Configuration config) {
        etudiants = ClientSoap.configurer(new EtudiantsService().getEtudiantsPort(), config.valeur("soa.ws.url.etudiants"), config);
        entreprises = ClientSoap.configurer(new EntreprisesService().getEntreprisesPort(), config.valeur("soa.ws.url.entreprises"), config);
        offres = ClientSoap.configurer(new OffresService().getOffresPort(), config.valeur("soa.ws.url.offres"), config);
        String cle = config.valeur("soa.api.cle.client");
        notifications = new ClientRest(config.valeur("soa.rest.url.notifications"), cle, "notifications", config);
        suivi = new ClientRest(config.valeur("soa.rest.url.suivi"), cle, "suivi", config);
    }

    private static ErreurMetier panne(String service) {
        return ErreurMetier.indisponible("SERVICE_" + service.toUpperCase() + "_INDISPONIBLE",
                "Le service " + service + " est momentanément indisponible.");
    }

    /** Une faute métier inattendue d'un service partenaire est traitée comme une panne : le processus s'interrompt proprement. */
    private interface Appel<T> {
        T executer() throws Exception;
    }

    private static <T> T appeler(String service, Appel<T> appel) {
        try {
            return appel.executer();
        } catch (ErreurMetier e) {
            throw e;
        } catch (WebServiceException e) {
            throw panne(service);
        } catch (Exception e) {
            throw panne(service);
        }
    }

    @Override
    public EtatEtudiant etudiant(long etudiantId) {
        return appeler("etudiants", () -> {
            VerificationEtudiant v = etudiants.verifierEtudiant(etudiantId);
            if (!v.isExiste()) {
                return new EtatEtudiant(false, false, v.getMotif(), null, null, null, null);
            }
            Etudiant e = etudiants.consulterEtudiant(etudiantId);
            return new EtatEtudiant(true, v.isEligible(), v.getMotif(), v.getNomComplet(), v.getEmail(), e.getFiliere(), e.getNiveau());
        });
    }

    @Override
    public EtatOffre offre(long offreId) {
        return appeler("offres", () -> {
            Disponibilite d = offres.verifierDisponibilite(offreId);
            if (!d.isExiste()) {
                return new EtatOffre(false, null, null, false, false, d.getMotif(), 0, 0);
            }
            Offre o = offres.consulterOffre(offreId);
            return new EtatOffre(true, o.getTitre(), o.getStatut(), d.isDateLimiteRespectee(), d.isPlacesDisponibles(), d.getMotif(),
                    o.getEntrepriseId(), o.getDureeSemaines());
        });
    }

    @Override
    public EtatEntreprise entreprise(long entrepriseId) {
        return appeler("entreprises", () -> {
            VerificationEntreprise v = entreprises.verifierEntreprise(entrepriseId);
            if (!v.isReferencee()) {
                return new EtatEntreprise(false, false, v.getMotif(), null, null, null);
            }
            var e = entreprises.consulterEntreprise(entrepriseId);
            String contact = e.getResponsable() == null ? null : e.getResponsable().getNom();
            return new EtatEntreprise(true, v.isActive(), v.getMotif(), v.getRaisonSociale(), v.getEmail(), contact);
        });
    }

    @Override
    public Compatibilite compatibilite(long offreId, long etudiantId) {
        return appeler("offres", () -> {
            var c = offres.verifierCompatibilite(offreId, etudiantId);
            String motif = !c.isFiliereAcceptee() || !c.isNiveauSuffisant() ? c.getMotif() : null;
            return new Compatibilite(c.isFiliereAcceptee(), c.isNiveauSuffisant(), c.getScoreCompetences(), motif);
        });
    }

    @Override
    public void retirerPlace(long offreId) {
        try {
            offres.decrementerPlaces(offreId);
        } catch (ma.inpt.stages.contrats.offres.ConflitFault e) {
            throw ErreurMetier.conflit("PLUS_DE_PLACE", "Règle R10 : l'offre n'a plus de place disponible ; la candidature ne peut pas être acceptée.");
        } catch (WebServiceException e) {
            throw panne("offres");
        } catch (Exception e) {
            throw panne("offres");
        }
    }

    @Override
    public void passerEnStage(long etudiantId) {
        appeler("etudiants", () -> etudiants.changerStatutEtudiant(etudiantId, "EN_STAGE"));
    }

    @Override
    public void creerStage(long candidatureId, long etudiantId, long offreId, long entrepriseId, LocalDate debut, LocalDate fin, String encadrant) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("candidatureId", candidatureId);
        corps.put("etudiantId", etudiantId);
        corps.put("offreId", offreId);
        corps.put("entrepriseId", entrepriseId);
        corps.put("dateDebut", debut.toString());
        corps.put("dateFin", fin.toString());
        if (encadrant != null) {
            corps.put("encadrantEntreprise", encadrant);
        }
        ClientRest.Reponse r = suivi.post("/stages", corps);
        if (!r.succes()) {
            throw ErreurMetier.indisponible("SERVICE_SUIVI_REFUS", "Le service de suivi a refusé la création du stage : " + r.message());
        }
    }

    @Override
    public void notifier(String type, String destinataire, Map<String, String> variables, Long candidatureId, Long offreId) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("type", type);
        corps.put("destinataire", destinataire);
        corps.put("variables", variables);
        if (candidatureId != null) {
            corps.put("candidatureId", candidatureId);
        }
        if (offreId != null) {
            corps.put("offreId", offreId);
        }
        ClientRest.Reponse r = notifications.post("/notifications", corps);
        if (!r.succes()) {
            throw ErreurMetier.indisponible("SERVICE_NOTIFICATIONS_REFUS", "Le service de notification a refusé l'envoi : " + r.message());
        }
        if ("ECHEC".equals(r.corps().path("statut").asText())) {
            throw ErreurMetier.indisponible("ENVOI_COURRIEL_ECHOUE", "La notification a été enregistrée mais le courriel n'a pas pu être envoyé.");
        }
    }
}
