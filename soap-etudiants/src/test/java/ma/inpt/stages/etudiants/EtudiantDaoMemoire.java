package ma.inpt.stages.etudiants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.inpt.stages.common.ErreurMetier;

/** DAO en mémoire : teste les règles et le contrat SOAP sans base de données. */
final class EtudiantDaoMemoire implements EtudiantDao {

    final Map<Long, Etudiant> lignes = new LinkedHashMap<>();
    private long suivant = 1;

    static Etudiant etudiant(String matricule, String email, String statut) {
        return new Etudiant(null, matricule, "Nom", "Prénom", "AMOA", "2A", email, "0612345678", "Rabat", statut, List.of("SQL"));
    }

    Etudiant ajouter(String statut) {
        long n = suivant;
        return inserer(etudiant("INPT-2024-" + String.format("%04d", n), "e" + n + "@inpt.ac.ma", statut));
    }

    @Override
    public Etudiant inserer(Etudiant e) {
        boolean doublon = lignes.values().stream().anyMatch(x -> x.matricule().equals(e.matricule()) || x.email().equals(e.email()));
        if (doublon) {
            throw ErreurMetier.conflit("CONFLIT_DONNEES", "Ce matricule ou cette adresse e-mail est déjà utilisé.");
        }
        Etudiant cree = e.avec(suivant++, e.statut());
        lignes.put(cree.id(), cree);
        return cree;
    }

    @Override
    public Optional<Etudiant> trouver(long id) {
        return Optional.ofNullable(lignes.get(id));
    }

    @Override
    public List<Etudiant> lister(Filtre f) {
        return new ArrayList<>(lignes.values());
    }

    @Override
    public void modifier(Etudiant e) {
        lignes.put(e.id(), e);
    }

    @Override
    public void supprimer(long id) {
        lignes.remove(id);
    }

    @Override
    public void changerStatut(long id, String statut) {
        lignes.put(id, lignes.get(id).avec(id, statut));
    }
}
