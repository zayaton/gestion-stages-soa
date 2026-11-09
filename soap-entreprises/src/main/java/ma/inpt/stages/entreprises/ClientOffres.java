package ma.inpt.stages.entreprises;

import java.time.LocalDate;
import java.util.List;

/** Ce dont S2 a besoin du service S3 : la seule façon d'accéder aux offres, jamais leur schéma. */
public interface ClientOffres {

    record OffreResume(long id, String titre, String statut, LocalDate dateLimite, int nombrePlaces, String ville) {
    }

    List<OffreResume> offresDe(long entrepriseId);
}
