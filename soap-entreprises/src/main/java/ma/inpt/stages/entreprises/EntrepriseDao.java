package ma.inpt.stages.entreprises;

import java.util.List;
import java.util.Optional;

/** Accès aux données du service S2. */
public interface EntrepriseDao {

    record Filtre(String secteur, String ville, String statut, String recherche) {
    }

    Entreprise inserer(Entreprise entreprise);

    Optional<Entreprise> trouver(long id);

    List<Entreprise> lister(Filtre filtre);

    void modifier(Entreprise entreprise);

    void supprimer(long id);
}
