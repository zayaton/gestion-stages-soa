package ma.inpt.stages.etudiants;

import java.util.List;
import java.util.Optional;

/** Accès aux données du service S1. */
public interface EtudiantDao {

    record Filtre(String filiere, String niveau, String statut, String ville, String recherche) {
    }

    Etudiant inserer(Etudiant etudiant);

    Optional<Etudiant> trouver(long id);

    List<Etudiant> lister(Filtre filtre);

    void modifier(Etudiant etudiant);

    void supprimer(long id);

    void changerStatut(long id, String statut);
}
