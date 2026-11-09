package ma.inpt.stages.entreprises;

import jakarta.xml.ws.WebServiceException;
import java.util.List;
import ma.inpt.stages.common.Configuration;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.soap.ClientSoap;
import ma.inpt.stages.contrats.offres.ConflitFault;
import ma.inpt.stages.contrats.offres.DonneesInvalidesFault;
import ma.inpt.stages.contrats.offres.ErreurInterneFault;
import ma.inpt.stages.contrats.offres.EtudiantIntrouvableFault;
import ma.inpt.stages.contrats.offres.FiltreOffres;
import ma.inpt.stages.contrats.offres.OffreIntrouvableFault;
import ma.inpt.stages.contrats.offres.OffresPortType;
import ma.inpt.stages.contrats.offres.OffresService;
import ma.inpt.stages.contrats.offres.ServiceIndisponibleFault;

/** Appel SOAP vers S3. Toute panne devient une indisponibilité clairement annoncée. */
public final class ClientOffresSoap implements ClientOffres {

    private final OffresPortType port;

    public ClientOffresSoap(Configuration config) {
        port = ClientSoap.configurer(new OffresService().getOffresPort(), config.valeur("soa.ws.url.offres"), config);
    }

    @Override
    public List<OffreResume> offresDe(long entrepriseId) {
        FiltreOffres filtre = new FiltreOffres();
        filtre.setEntrepriseId(entrepriseId);
        try {
            return port.listerOffres(filtre).stream()
                    .map(o -> new OffreResume(o.getId(), o.getTitre(), o.getStatut(), Dates.versLocalDate(o.getDateLimite()), o.getNombrePlaces(), o.getVille()))
                    .toList();
        } catch (WebServiceException | ConflitFault | DonneesInvalidesFault | ErreurInterneFault
                 | EtudiantIntrouvableFault | OffreIntrouvableFault | ServiceIndisponibleFault e) {
            throw ErreurMetier.indisponible("SERVICE_OFFRES_INDISPONIBLE", "Le service des offres est momentanément indisponible.");
        }
    }
}
