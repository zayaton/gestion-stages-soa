package ma.inpt.stages.offres;

import java.util.List;
import java.util.Optional;

/** Ce dont S3 a besoin des services S1 et S2 : jamais d'accès direct à leurs schémas. */
public interface ClientsExternes {

    record ProfilEtudiant(String filiere, String niveau, List<String> competences) {
    }

    record EtatEntreprise(boolean referencee, boolean active, String motif) {
    }

    Optional<ProfilEtudiant> profilEtudiant(long etudiantId);

    EtatEntreprise etatEntreprise(long entrepriseId);
}
