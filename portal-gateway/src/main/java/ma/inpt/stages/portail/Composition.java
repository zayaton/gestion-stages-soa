package ma.inpt.stages.portail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.rest.Json;
import ma.inpt.stages.contrats.entreprises.FiltreEntreprises;
import ma.inpt.stages.contrats.offres.Offre;
import ma.inpt.stages.portail.Vues.EtudiantVue;

/**
 * Composition de données venant de plusieurs services : une candidature (S4) complétée du titre de l'offre (S3),
 * du nom de l'entreprise (S2) et de l'étudiant (S1). Chaque service reste seul maître de ses données.
 * Un cache par requête évite d'interroger deux fois le même service pour la même donnée.
 */
final class Composition {

    private final Services services;
    private final Map<Long, Offre> offres = new HashMap<>();
    private final Map<Long, EtudiantVue> etudiants = new HashMap<>();
    private Map<Long, String> entreprises;

    Composition(Services services) {
        this.services = services;
    }

    Offre offre(long id) {
        return offres.computeIfAbsent(id, k -> Services.soap("offres", () -> services.offres.consulterOffre(k)));
    }

    EtudiantVue etudiant(long id) {
        return etudiants.computeIfAbsent(id, k -> EtudiantVue.de(Services.soap("etudiants", () -> services.etudiants.consulterEtudiant(k))));
    }

    String entreprise(long id) {
        if (entreprises == null) {
            entreprises = new HashMap<>();
            Services.soap("entreprises", () -> services.entreprises.listerEntreprises(new FiltreEntreprises()))
                    .forEach(e -> entreprises.put(e.getId(), e.getRaisonSociale()));
        }
        return entreprises.getOrDefault(id, "Entreprise " + id);
    }

    /** Ajoute titre de l'offre et nom de l'entreprise à une candidature ou une soumission. Une offre supprimée n'empêche rien. */
    ObjectNode avecOffre(JsonNode element) {
        ObjectNode copie = ((ObjectNode) element).deepCopy();
        long offreId = element.path("offreId").asLong();
        try {
            Offre o = offre(offreId);
            copie.put("titreOffre", o.getTitre());
            copie.put("entreprise", entreprise(o.getEntrepriseId()));
            copie.put("villeOffre", o.getVille());
        } catch (ErreurMetier e) {
            if (e.type() != ErreurMetier.Type.INTROUVABLE) {
                throw e;
            }
            copie.put("titreOffre", "Offre n° " + offreId);
        }
        return copie;
    }

    /** Ajoute l'étudiant, complet ou masqué selon qui consulte. */
    ObjectNode avecEtudiant(ObjectNode element, boolean masquer) {
        EtudiantVue e = etudiant(element.path("etudiantId").asLong());
        element.set("etudiant", Json.MAPPEUR.valueToTree(masquer ? e.masquee() : e));
        return element;
    }

    ArrayNode liste(JsonNode elements, boolean ajouterEtudiant, boolean masquer) {
        ArrayNode resultat = Json.MAPPEUR.createArrayNode();
        for (JsonNode element : elements) {
            ObjectNode o = avecOffre(element);
            resultat.add(ajouterEtudiant ? avecEtudiant(o, masquer) : o);
        }
        return resultat;
    }
}
