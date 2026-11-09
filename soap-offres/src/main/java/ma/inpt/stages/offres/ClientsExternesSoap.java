package ma.inpt.stages.offres;

import jakarta.xml.ws.WebServiceException;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.entreprises.EntreprisesPortType;
import ma.inpt.stages.contrats.entreprises.EntreprisesService;
import ma.inpt.stages.contrats.entreprises.VerificationEntreprise;
import ma.inpt.stages.contrats.etudiants.Etudiant;
import ma.inpt.stages.contrats.etudiants.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.etudiants.EtudiantsPortType;
import ma.inpt.stages.contrats.etudiants.EtudiantsService;

/** Appels SOAP vers S1 et S2. Toute panne devient une indisponibilité clairement annoncée. */
public final class ClientsExternesSoap implements ClientsExternes {

    private final EtudiantsPortType etudiants;
    private final EntreprisesPortType entreprises;

    public ClientsExternesSoap(Configuration config) {
        etudiants = ClientSoap.configurer(new EtudiantsService().getEtudiantsPort(), config.valeur("soa.ws.url.etudiants"), config);
        entreprises = ClientSoap.configurer(new EntreprisesService().getEntreprisesPort(), config.valeur("soa.ws.url.entreprises"), config);
    }

    @Override
    public Optional<ProfilEtudiant> profilEtudiant(long etudiantId) {
        try {
            Etudiant e = etudiants.consulterEtudiant(etudiantId);
            return Optional.of(new ProfilEtudiant(e.getFiliere(), e.getNiveau(), List.copyOf(e.getCompetence())));
        } catch (EtudiantIntrouvableFault e) {
            return Optional.empty();
        } catch (WebServiceException | ma.inpt.stages.contrats.etudiants.ConflitFault | ma.inpt.stages.contrats.etudiants.DonneesInvalidesFault
                 | ma.inpt.stages.contrats.etudiants.ErreurInterneFault | ma.inpt.stages.contrats.etudiants.ServiceIndisponibleFault e) {
            throw ErreurMetier.indisponible("SERVICE_ETUDIANTS_INDISPONIBLE", "Le service des étudiants est momentanément indisponible.");
        }
    }

    @Override
    public EtatEntreprise etatEntreprise(long entrepriseId) {
        try {
            VerificationEntreprise v = entreprises.verifierEntreprise(entrepriseId);
            return new EtatEntreprise(v.isReferencee(), v.isActive(), v.getMotif());
        } catch (WebServiceException | ma.inpt.stages.contrats.entreprises.ConflitFault | ma.inpt.stages.contrats.entreprises.DonneesInvalidesFault
                 | ma.inpt.stages.contrats.entreprises.EntrepriseIntrouvableFault | ma.inpt.stages.contrats.entreprises.ErreurInterneFault
                 | ma.inpt.stages.contrats.entreprises.ServiceIndisponibleFault e) {
            throw ErreurMetier.indisponible("SERVICE_ENTREPRISES_INDISPONIBLE", "Le service des entreprises est momentanément indisponible.");
        }
    }
}
