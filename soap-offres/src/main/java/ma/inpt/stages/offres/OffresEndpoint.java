package ma.inpt.stages.offres;

import jakarta.jws.WebService;
import java.util.List;
import java.util.function.Supplier;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.contrats.offres.Compatibilite;
import ma.inpt.stages.contrats.offres.ConflitFault;
import ma.inpt.stages.contrats.offres.Disponibilite;
import ma.inpt.stages.contrats.offres.DonneesInvalidesFault;
import ma.inpt.stages.contrats.offres.Erreur;
import ma.inpt.stages.contrats.offres.ErreurInterneFault;
import ma.inpt.stages.contrats.offres.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.contrats.offres.OffreIntrouvableFault;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.ServiceIndisponibleFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implémente le contrat offres.wsdl : convertit les types XML et traduit les erreurs en fautes typées. */
@WebService(
        serviceName = "OffresService",
        portName = "OffresPort",
        targetNamespace = "http://stages.inpt.ma/offres",
        endpointInterface = "ma.inpt.stages.contrats.offres.OffresPortType",
        wsdlLocation = "wsdl/offres.wsdl")
public class OffresEndpoint implements OffresPortType {

    private static final Logger LOG = LoggerFactory.getLogger(OffresEndpoint.class);

    private final ServiceOffres service;

    public OffresEndpoint(ServiceOffres service) {
        this.service = service;
    }

    @Override
    public ma.inpt.stages.contrats.offres.Offre creerOffre(ma.inpt.stages.contrats.offres.Offre offre)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.creer(versDomaine(offre))));
    }

    @Override
    public ma.inpt.stages.contrats.offres.Offre modifierOffre(long id, ma.inpt.stages.contrats.offres.Offre offre)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.modifier(id, versDomaine(offre))));
    }

    @Override
    public void supprimerOffre(long id)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        executer(() -> {
            service.supprimer(id);
            return null;
        });
    }

    @Override
    public ma.inpt.stages.contrats.offres.Offre consulterOffre(long id)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.consulter(id)));
    }

    @Override
    public List<ma.inpt.stages.contrats.offres.Offre> listerOffres(FiltreOffres f)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        ServiceOffres.Criteres criteres = f == null
                ? new ServiceOffres.Criteres(null, null, null, null, null, null, null, false)
                : new ServiceOffres.Criteres(f.getStatut(), f.getVille(), f.getFiliere(), f.getNiveau(), f.getEntrepriseId(),
                        f.getCompetence(), f.getRecherche(), Boolean.TRUE.equals(f.isOuvertesSeulement()));
        return executer(() -> service.lister(criteres).stream().map(OffresEndpoint::versXml).toList());
    }

    @Override
    public Disponibilite verifierDisponibilite(long offreId)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> {
            ServiceOffres.Disponibilite d = service.verifierDisponibilite(offreId);
            Disponibilite x = new Disponibilite();
            x.setExiste(d.existe());
            x.setActive(d.active());
            x.setDateLimiteRespectee(d.dateLimiteRespectee());
            x.setPlacesDisponibles(d.placesDisponibles());
            x.setNombrePlaces(d.nombrePlaces());
            x.setDisponible(d.disponible());
            x.setRegle(d.regle());
            x.setMotif(d.motif());
            x.setEntrepriseId(d.entrepriseId());
            return x;
        });
    }

    @Override
    public Compatibilite verifierCompatibilite(long offreId, long etudiantId)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> {
            ServiceOffres.Compatibilite c = service.verifierCompatibilite(offreId, etudiantId);
            Compatibilite x = new Compatibilite();
            x.setCompatible(c.compatible());
            x.setFiliereAcceptee(c.filiereAcceptee());
            x.setNiveauSuffisant(c.niveauSuffisant());
            x.setDateLimiteRespectee(c.dateLimiteRespectee());
            x.setScoreCompetences(c.scoreCompetences());
            x.setRegle(c.regle());
            x.setMotif(c.motif());
            return x;
        });
    }

    @Override
    public ma.inpt.stages.contrats.offres.Offre decrementerPlaces(long offreId)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.decrementerPlaces(offreId)));
    }

    private <T> T executer(Supplier<T> action)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, OffreIntrouvableFault, ServiceIndisponibleFault {
        try {
            return action.get();
        } catch (ErreurMetier e) {
            Erreur info = new Erreur();
            info.setCode(e.code());
            info.setMessage(e.getMessage());
            info.getDetail().addAll(e.details());
            switch (e.type()) {
                case INTROUVABLE -> {
                    if ("ETUDIANT_INTROUVABLE".equals(e.code())) {
                        throw new EtudiantIntrouvableFault(e.getMessage(), info);
                    }
                    throw new OffreIntrouvableFault(e.getMessage(), info);
                }
                case DONNEES_INVALIDES -> throw new DonneesInvalidesFault(e.getMessage(), info);
                case CONFLIT -> throw new ConflitFault(e.getMessage(), info);
                default -> throw new ServiceIndisponibleFault(e.getMessage(), info);
            }
        } catch (RuntimeException e) {
            LOG.error("Erreur inattendue", e);
            String message = "Une erreur interne est survenue.";
            Erreur info = new Erreur();
            info.setCode("ERREUR_INTERNE");
            info.setMessage(message);
            throw new ErreurInterneFault(message, info);
        }
    }

    private static Offre versDomaine(ma.inpt.stages.contrats.offres.Offre x) {
        if (x == null) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", List.of("L'offre est obligatoire."));
        }
        return new Offre(null, x.getTitre(), x.getDescription(), List.copyOf(x.getCompetence()), x.getDureeSemaines(),
                Dates.versLocalDate(x.getDateLimite()), x.getNombrePlaces(), List.copyOf(x.getFiliere()), x.getNiveauMinimum(),
                x.getVille(), x.getEntrepriseId(), x.getStatut());
    }

    private static ma.inpt.stages.contrats.offres.Offre versXml(Offre o) {
        ma.inpt.stages.contrats.offres.Offre x = new ma.inpt.stages.contrats.offres.Offre();
        x.setId(o.id());
        x.setTitre(o.titre());
        x.setDescription(o.description());
        x.getCompetence().addAll(o.competences());
        x.setDureeSemaines(o.dureeSemaines());
        x.setDateLimite(Dates.versXml(o.dateLimite()));
        x.setNombrePlaces(o.nombrePlaces());
        x.getFiliere().addAll(o.filieres());
        x.setNiveauMinimum(o.niveauMinimum());
        x.setVille(o.ville());
        x.setEntrepriseId(o.entrepriseId());
        x.setStatut(o.statut());
        return x;
    }
}
