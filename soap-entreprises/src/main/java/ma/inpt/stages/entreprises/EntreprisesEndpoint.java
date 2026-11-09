package ma.inpt.stages.entreprises;

import jakarta.jws.WebService;
import java.util.List;
import java.util.function.Supplier;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.contrats.entreprises.ConflitFault;
import ma.inpt.stages.contrats.entreprises.DonneesInvalidesFault;
import ma.inpt.stages.contrats.entreprises.EntrepriseIntrouvableFault;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.Erreur;
import ma.inpt.stages.contrats.entreprises.ErreurInterneFault;
import ma.inpt.stages.contrats.entreprises.FiltreEntreprises;
import ma.inpt.stages.contrats.entreprises.OffreResume;
import ma.inpt.stages.contrats.entreprises.ResponsableContact;
import ma.inpt.stages.contrats.entreprises.ServiceIndisponibleFault;
import ma.inpt.stages.contrats.entreprises.VerificationEntreprise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implémente le contrat entreprises.wsdl : convertit les types XML et traduit les erreurs en fautes typées. */
@WebService(
        serviceName = "EntreprisesService",
        portName = "EntreprisesPort",
        targetNamespace = "http://stages.inpt.ma/entreprises",
        endpointInterface = "ma.inpt.stages.contrats.entreprises.EntreprisesPortType",
        wsdlLocation = "wsdl/entreprises.wsdl")
public class EntreprisesEndpoint implements EntreprisesPortType {

    private static final Logger LOG = LoggerFactory.getLogger(EntreprisesEndpoint.class);

    private final ServiceEntreprises service;

    public EntreprisesEndpoint(ServiceEntreprises service) {
        this.service = service;
    }

    @Override
    public ma.inpt.stages.contrats.entreprises.Entreprise ajouterEntreprise(ma.inpt.stages.contrats.entreprises.Entreprise entreprise)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.ajouter(versDomaine(entreprise))));
    }

    @Override
    public ma.inpt.stages.contrats.entreprises.Entreprise consulterEntreprise(long id)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.consulter(id)));
    }

    @Override
    public List<ma.inpt.stages.contrats.entreprises.Entreprise> listerEntreprises(FiltreEntreprises f)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        EntrepriseDao.Filtre filtre = f == null
                ? new EntrepriseDao.Filtre(null, null, null, null)
                : new EntrepriseDao.Filtre(f.getSecteur(), f.getVille(), f.getStatut(), f.getRecherche());
        return executer(() -> service.lister(filtre).stream().map(EntreprisesEndpoint::versXml).toList());
    }

    @Override
    public ma.inpt.stages.contrats.entreprises.Entreprise modifierEntreprise(long id, ma.inpt.stages.contrats.entreprises.Entreprise entreprise)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.modifier(id, versDomaine(entreprise))));
    }

    @Override
    public void supprimerEntreprise(long id)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        executer(() -> {
            service.supprimer(id);
            return null;
        });
    }

    @Override
    public VerificationEntreprise verifierEntreprise(long id)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        return executer(() -> {
            ServiceEntreprises.Verification v = service.verifier(id);
            VerificationEntreprise x = new VerificationEntreprise();
            x.setReferencee(v.referencee());
            x.setActive(v.active());
            x.setRegle(v.regle());
            x.setMotif(v.motif());
            x.setRaisonSociale(v.raisonSociale());
            x.setEmail(v.email());
            return x;
        });
    }

    @Override
    public List<OffreResume> consulterOffresEntreprise(long id)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        return executer(() -> service.offres(id).stream().map(o -> {
            OffreResume x = new OffreResume();
            x.setId(o.id());
            x.setTitre(o.titre());
            x.setStatut(o.statut());
            x.setDateLimite(Dates.versXml(o.dateLimite()));
            x.setNombrePlaces(o.nombrePlaces());
            x.setVille(o.ville());
            return x;
        }).toList());
    }

    private <T> T executer(Supplier<T> action)
            throws ConflitFault, DonneesInvalidesFault, EntrepriseIntrouvableFault, ErreurInterneFault, ServiceIndisponibleFault {
        try {
            return action.get();
        } catch (ErreurMetier e) {
            Erreur info = new Erreur();
            info.setCode(e.code());
            info.setMessage(e.getMessage());
            info.getDetail().addAll(e.details());
            switch (e.type()) {
                case INTROUVABLE -> throw new EntrepriseIntrouvableFault(e.getMessage(), info);
                case DONNEES_INVALIDES, REQUETE_INVALIDE -> throw new DonneesInvalidesFault(e.getMessage(), info);
                case CONFLIT -> throw new ConflitFault(e.getMessage(), info);
                case INDISPONIBLE -> throw new ServiceIndisponibleFault(e.getMessage(), info);
                default -> throw new ErreurInterneFault(e.getMessage(), info);
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

    private static Entreprise versDomaine(ma.inpt.stages.contrats.entreprises.Entreprise x) {
        if (x == null) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", List.of("L'entreprise est obligatoire."));
        }
        ResponsableContact r = x.getResponsable() == null ? new ResponsableContact() : x.getResponsable();
        return new Entreprise(null, x.getRaisonSociale(), x.getIce(), x.getSecteur(), x.getAdresse(), x.getVille(), x.getEmail(),
                x.getTelephone(), r.getNom(), r.getFonction(), r.getEmail(), r.getTelephone(), x.getStatut());
    }

    private static ma.inpt.stages.contrats.entreprises.Entreprise versXml(Entreprise e) {
        ma.inpt.stages.contrats.entreprises.Entreprise x = new ma.inpt.stages.contrats.entreprises.Entreprise();
        x.setId(e.id());
        x.setRaisonSociale(e.raisonSociale());
        x.setIce(e.ice());
        x.setSecteur(e.secteur());
        x.setAdresse(e.adresse());
        x.setVille(e.ville());
        x.setEmail(e.email());
        x.setTelephone(e.telephone());
        ResponsableContact r = new ResponsableContact();
        r.setNom(e.contactNom());
        r.setFonction(e.contactFonction());
        r.setEmail(e.contactEmail());
        r.setTelephone(e.contactTelephone());
        x.setResponsable(r);
        x.setStatut(e.statut());
        return x;
    }
}
