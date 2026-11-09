package ma.inpt.stages.etudiants;

import jakarta.jws.WebService;
import java.util.List;
import java.util.function.Supplier;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.contrats.etudiants.ConflitFault;
import ma.inpt.stages.contrats.etudiants.DonneesInvalidesFault;
import ma.inpt.stages.contrats.etudiants.Erreur;
import ma.inpt.stages.contrats.etudiants.ErreurInterneFault;
import ma.inpt.stages.contrats.etudiants.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.FiltreEtudiants;
import ma.inpt.stages.contrats.etudiants.ServiceIndisponibleFault;
import ma.inpt.stages.contrats.etudiants.VerificationEtudiant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implémente le contrat etudiants.wsdl : convertit les types XML et traduit les erreurs en fautes typées. */
@WebService(
        serviceName = "EtudiantsService",
        portName = "EtudiantsPort",
        targetNamespace = "http://stages.inpt.ma/etudiants",
        endpointInterface = "ma.inpt.stages.contrats.etudiants.EtudiantsPortType",
        wsdlLocation = "wsdl/etudiants.wsdl")
public class EtudiantsEndpoint implements EtudiantsPortType {

    private static final Logger LOG = LoggerFactory.getLogger(EtudiantsEndpoint.class);

    private final ServiceEtudiants service;

    public EtudiantsEndpoint(ServiceEtudiants service) {
        this.service = service;
    }

    @Override
    public ma.inpt.stages.contrats.etudiants.Etudiant ajouterEtudiant(ma.inpt.stages.contrats.etudiants.Etudiant etudiant)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.ajouter(versDomaine(etudiant))));
    }

    @Override
    public ma.inpt.stages.contrats.etudiants.Etudiant consulterEtudiant(long id)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.consulter(id)));
    }

    @Override
    public List<ma.inpt.stages.contrats.etudiants.Etudiant> listerEtudiants(FiltreEtudiants f)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        EtudiantDao.Filtre filtre = f == null
                ? new EtudiantDao.Filtre(null, null, null, null, null)
                : new EtudiantDao.Filtre(f.getFiliere(), f.getNiveau(), f.getStatut(), f.getVille(), f.getRecherche());
        return executer(() -> service.lister(filtre).stream().map(EtudiantsEndpoint::versXml).toList());
    }

    @Override
    public ma.inpt.stages.contrats.etudiants.Etudiant modifierEtudiant(long id, ma.inpt.stages.contrats.etudiants.Etudiant etudiant)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.modifier(id, versDomaine(etudiant))));
    }

    @Override
    public void supprimerEtudiant(long id)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        executer(() -> {
            service.supprimer(id);
            return null;
        });
    }

    @Override
    public VerificationEtudiant verifierEtudiant(long id)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> {
            ServiceEtudiants.Verification v = service.verifier(id);
            VerificationEtudiant x = new VerificationEtudiant();
            x.setExiste(v.existe());
            x.setStatut(v.statut());
            x.setEligible(v.eligible());
            x.setRegle(v.regle());
            x.setMotif(v.motif());
            x.setNomComplet(v.nomComplet());
            x.setEmail(v.email());
            return x;
        });
    }

    @Override
    public ma.inpt.stages.contrats.etudiants.Etudiant changerStatutEtudiant(long id, String statut)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        return executer(() -> versXml(service.changerStatut(id, statut)));
    }

    private <T> T executer(Supplier<T> action)
            throws ConflitFault, DonneesInvalidesFault, ErreurInterneFault, EtudiantIntrouvableFault, ServiceIndisponibleFault {
        try {
            return action.get();
        } catch (ErreurMetier e) {
            Erreur info = new Erreur();
            info.setCode(e.code());
            info.setMessage(e.getMessage());
            info.getDetail().addAll(e.details());
            switch (e.type()) {
                case INTROUVABLE -> throw new EtudiantIntrouvableFault(e.getMessage(), info);
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

    private static Etudiant versDomaine(ma.inpt.stages.contrats.etudiants.Etudiant x) {
        if (x == null) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", List.of("L'étudiant est obligatoire."));
        }
        return new Etudiant(null, x.getMatricule(), x.getNom(), x.getPrenom(), x.getFiliere(), x.getNiveau(), x.getEmail(),
                x.getTelephone(), x.getVille(), x.getStatut(), List.copyOf(x.getCompetence()));
    }

    private static ma.inpt.stages.contrats.etudiants.Etudiant versXml(Etudiant e) {
        ma.inpt.stages.contrats.etudiants.Etudiant x = new ma.inpt.stages.contrats.etudiants.Etudiant();
        x.setId(e.id());
        x.setMatricule(e.matricule());
        x.setNom(e.nom());
        x.setPrenom(e.prenom());
        x.setFiliere(e.filiere());
        x.setNiveau(e.niveau());
        x.setEmail(e.email());
        x.setTelephone(e.telephone());
        x.setVille(e.ville());
        x.setStatut(e.statut());
        x.getCompetence().addAll(e.competences());
        return x;
    }
}
