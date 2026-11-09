package ma.inpt.stages.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.notifications.Modeles.Message;
import org.junit.jupiter.api.Test;

class GabaritsTest {

    /** Valeurs suffisantes pour n'importe quel type : chaque test retire ce qui ne sert pas. */
    private static final Map<String, List<String>> OBLIGATOIRES = Map.of(
            "CONFIRMATION_DEPOT", List.of("nomEtudiant", "titreOffre", "entreprise"),
            "CANDIDATURE_REJETEE", List.of("nomEtudiant", "titreOffre", "motif"),
            "ACCEPTATION", List.of("nomEtudiant", "titreOffre", "entreprise"),
            "REFUS", List.of("nomEtudiant", "titreOffre", "entreprise"),
            "DEMANDE_COMPLEMENT", List.of("nomEtudiant", "titreOffre", "entreprise", "commentaire"),
            "NOUVELLE_CANDIDATURE_ENTREPRISE", List.of("entreprise", "titreOffre", "nomEtudiant", "filiere", "niveau"),
            "RAPPEL_DATE_LIMITE", List.of("nomEtudiant", "titreOffre", "entreprise", "dateLimite"),
            "ALERTE_RESPONSABLE", List.of("etape", "erreur"));

    private static Map<String, String> valeurs(String type) {
        Map<String, String> v = new HashMap<>();
        OBLIGATOIRES.get(type).forEach(n -> v.put(n, "valeur-" + n));
        return v;
    }

    @Test
    void lesHuitTypesDuSujetExistent() {
        assertEquals(OBLIGATOIRES.keySet(), new java.util.HashSet<>(Gabarits.types()));
    }

    @Test
    void chaqueTypeSeRendAvecSesVariablesObligatoires() {
        for (String type : Gabarits.types()) {
            Message m = Gabarits.rendre(type, valeurs(type));
            assertFalse(m.sujet().contains("{"), type + " : " + m.sujet());
            assertFalse(m.html().contains("{"), type);
            assertTrue(m.html().contains("INPT"), type);
            assertTrue(m.html().contains("lang=\"fr\""), type);
            assertTrue(m.texte().contains("ne pas y répondre"), type);
            OBLIGATOIRES.get(type).forEach(n -> assertTrue(m.texte().contains("valeur-" + n), type + " doit afficher " + n));
        }
    }

    @Test
    void chaqueVariableObligatoireManquanteEstSignalee() {
        for (String type : Gabarits.types()) {
            ErreurMetier e = assertThrows(ErreurMetier.class, () -> Gabarits.rendre(type, Map.of()));
            assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, e.type());
            assertEquals(OBLIGATOIRES.get(type).size(), e.details().size(), type + " " + e.details());
        }
    }

    @Test
    void uneVariableInconnueEstRefusee() {
        Map<String, String> v = valeurs("REFUS");
        v.put("nomEtudient", "faute de frappe");
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> Gabarits.rendre("REFUS", v));
        assertTrue(e.details().get(0).contains("nomEtudient"));
    }

    @Test
    void unTypeInconnuEstRefuseAvecLaListeDesTypes() {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> Gabarits.rendre("SMS", Map.of()));
        assertTrue(e.details().get(0).contains("ACCEPTATION"));
    }

    @Test
    void leHtmlEchappeLesValeurs() {
        Map<String, String> v = valeurs("DEMANDE_COMPLEMENT");
        v.put("commentaire", "<script>alert('x')</script> & \"suite\"");
        Message m = Gabarits.rendre("DEMANDE_COMPLEMENT", v);
        assertFalse(m.html().contains("<script>"));
        assertTrue(m.html().contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;suite&quot;"));
        assertTrue(m.texte().contains("<script>"), "la version texte n'est pas du HTML : elle garde le texte tel quel");
    }

    @Test
    void unParagrapheDeVariableFacultativeAbsenteEstOmis() {
        Message sans = Gabarits.rendre("ACCEPTATION", valeurs("ACCEPTATION"));
        Map<String, String> v = valeurs("ACCEPTATION");
        v.put("commentaire", "Bienvenue dans l'équipe.");
        Message avec = Gabarits.rendre("ACCEPTATION", v);
        assertFalse(sans.texte().contains("Bienvenue"));
        assertTrue(avec.texte().contains("Bienvenue dans l'équipe."));
        assertTrue(avec.html().contains("l&#39;équipe"));
        assertEquals(sans.texte().split("\n\n").length + 1, avec.texte().split("\n\n").length);
    }

    @Test
    void leMotifDeRejetApparaitDansLeCourriel() {
        Map<String, String> v = valeurs("CANDIDATURE_REJETEE");
        v.put("motif", "La date limite de candidature est dépassée.");
        assertTrue(Gabarits.rendre("CANDIDATURE_REJETEE", v).texte().contains("Motif : La date limite de candidature est dépassée."));
    }

    @Test
    void leCourrielEstSobre() {
        Message m = Gabarits.rendre("CONFIRMATION_DEPOT", valeurs("CONFIRMATION_DEPOT"));
        assertTrue(m.html().contains("#003F8A"), "bleu INPT");
        assertFalse(m.html().contains("<img"), "aucune image distante");
        assertFalse(m.html().matches("(?s).*[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}].*"), "aucun emoji");
    }
}
