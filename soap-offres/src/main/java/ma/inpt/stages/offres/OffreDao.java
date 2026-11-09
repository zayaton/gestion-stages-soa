package ma.inpt.stages.offres;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Accès aux données du service S3. */
public interface OffreDao {

    record Filtre(String statut, String ville, String filiere, String niveau, Long entrepriseId,
                  String competence, String recherche, LocalDate ouvertesAu) {
    }

    Offre inserer(Offre offre);

    Optional<Offre> trouver(long id);

    List<Offre> lister(Filtre filtre);

    void modifier(Offre offre);

    void supprimer(long id);

    /** Retire une place en une seule requête. Faux si l'offre n'est pas active ou n'a plus de place. */
    boolean decrementerPlaces(long id);
}
