package ma.inpt.stages.notifications;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.notifications.Modeles.Message;

/**
 * Gabarits des courriels, en français. Chaque type déclare ses variables obligatoires et facultatives.
 * Toutes les valeurs sont échappées : une lettre de motivation ne peut pas injecter de HTML dans un courriel.
 */
public final class Gabarits {

    /** Un gabarit : sujet, paragraphes (avec {variable}), variables obligatoires et facultatives. */
    private record Gabarit(String sujet, List<String> paragraphes, List<String> obligatoires, List<String> facultatives) {
    }

    private static final Map<String, Gabarit> GABARITS = new LinkedHashMap<>();

    static {
        GABARITS.put("CONFIRMATION_DEPOT", new Gabarit(
                "Candidature enregistrée : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "Votre candidature à l'offre « {titreOffre} » de {entreprise} a été vérifiée et enregistrée.",
                        "L'entreprise en a été informée. Vous recevrez un message dès qu'elle aura pris sa décision.",
                        "Tant que l'entreprise n'a pas commencé l'évaluation, vous pouvez annuler cette candidature depuis votre espace."),
                List.of("nomEtudiant", "titreOffre", "entreprise"), List.of()));
        GABARITS.put("CANDIDATURE_REJETEE", new Gabarit(
                "Candidature non recevable : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "Votre candidature à l'offre « {titreOffre} » n'a pas pu être enregistrée.",
                        "Motif : {motif}",
                        "Aucune démarche n'est attendue de votre part. Vous pouvez consulter les autres offres ouvertes depuis votre espace."),
                List.of("nomEtudiant", "titreOffre", "motif"), List.of("regle")));
        GABARITS.put("ACCEPTATION", new Gabarit(
                "Candidature retenue : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "{entreprise} a retenu votre candidature à l'offre « {titreOffre} ».",
                        "{commentaire}",
                        "Votre dossier de stage a été créé. Le service des stages vous contactera pour la convention."),
                List.of("nomEtudiant", "titreOffre", "entreprise"), List.of("commentaire")));
        GABARITS.put("REFUS", new Gabarit(
                "Réponse à votre candidature : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "{entreprise} n'a pas retenu votre candidature à l'offre « {titreOffre} ».",
                        "{commentaire}",
                        "D'autres offres restent ouvertes dans votre espace."),
                List.of("nomEtudiant", "titreOffre", "entreprise"), List.of("commentaire")));
        GABARITS.put("DEMANDE_COMPLEMENT", new Gabarit(
                "Complément d'information demandé : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "{entreprise} étudie votre candidature à l'offre « {titreOffre} » et vous demande un complément d'information :",
                        "« {commentaire} »",
                        "Votre candidature reste en cours d'évaluation."),
                List.of("nomEtudiant", "titreOffre", "entreprise", "commentaire"), List.of()));
        GABARITS.put("NOUVELLE_CANDIDATURE_ENTREPRISE", new Gabarit(
                "Nouvelle candidature : {titreOffre}",
                List.of("Bonjour,",
                        "{nomEtudiant} ({filiere}, {niveau}) a déposé une candidature à votre offre « {titreOffre} ».",
                        "Adéquation des compétences : {score} %. Cet indicateur est informatif.",
                        "Le dossier est disponible dans l'espace entreprise de {entreprise}, où vous pourrez rendre votre décision."),
                List.of("entreprise", "titreOffre", "nomEtudiant", "filiere", "niveau"), List.of("score")));
        GABARITS.put("RAPPEL_DATE_LIMITE", new Gabarit(
                "Plus que quelques jours pour candidater : {titreOffre}",
                List.of("Bonjour {nomEtudiant},",
                        "L'offre « {titreOffre} » de {entreprise} correspond à votre filière et à votre niveau.",
                        "Les candidatures ferment le {dateLimite}.",
                        "Vous pouvez candidater depuis votre espace étudiant."),
                List.of("nomEtudiant", "titreOffre", "entreprise", "dateLimite"), List.of()));
        GABARITS.put("ALERTE_RESPONSABLE", new Gabarit(
                "Incident de traitement : {etape}",
                List.of("Bonjour,",
                        "Le traitement automatique d'une candidature s'est interrompu à l'étape « {etape} ».",
                        "Cause : {erreur}",
                        "Référence : {reference}",
                        "Une intervention est nécessaire depuis l'espace responsable."),
                List.of("etape", "erreur"), List.of("reference")));
    }

    private Gabarits() {
    }

    public static boolean existe(String type) {
        return GABARITS.containsKey(type);
    }

    public static List<String> types() {
        return List.copyOf(GABARITS.keySet());
    }

    /** Construit le message ; lève DONNEES_INVALIDES si une variable obligatoire manque ou si une variable est inconnue. */
    public static Message rendre(String type, Map<String, String> variables) {
        Gabarit g = GABARITS.get(type);
        if (g == null) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.",
                    List.of("Le type doit être l'un de : " + String.join(", ", types()) + "."));
        }
        List<String> erreurs = new ArrayList<>();
        for (String nom : g.obligatoires()) {
            String valeur = variables.get(nom);
            if (valeur == null || valeur.isBlank()) {
                erreurs.add("La variable « " + nom + " » est obligatoire pour une notification " + type + ".");
            }
        }
        for (String nom : variables.keySet()) {
            if (!g.obligatoires().contains(nom) && !g.facultatives().contains(nom)) {
                erreurs.add("La variable « " + nom + " » n'est pas utilisée par une notification " + type + ".");
            }
        }
        if (!erreurs.isEmpty()) {
            throw ErreurMetier.donneesInvalides("DONNEES_INVALIDES", "Les données transmises sont invalides.", erreurs);
        }
        String sujet = remplir(g.sujet(), variables, false);
        List<String> paragraphes = new ArrayList<>();
        for (String p : g.paragraphes()) {
            // Un paragraphe qui ne contient qu'une variable facultative absente est omis.
            if (!variablesAbsentes(p, variables)) {
                paragraphes.add(p);
            }
        }
        StringBuilder texte = new StringBuilder();
        StringBuilder html = new StringBuilder();
        for (String p : paragraphes) {
            texte.append(remplir(p, variables, false)).append("\n\n");
            html.append("<p style=\"margin:0 0 14px 0;\">").append(remplir(p, variables, true)).append("</p>\n");
        }
        texte.append("--\nPlateforme des stages - INPT, Rabat\nMessage automatique : merci de ne pas y répondre.\n");
        return new Message(sujet, enveloppe(echapper(sujet), html.toString()), texte.toString());
    }

    private static boolean variablesAbsentes(String paragraphe, Map<String, String> variables) {
        var m = java.util.regex.Pattern.compile("\\{([a-zA-Z]+)}").matcher(paragraphe);
        while (m.find()) {
            String v = variables.get(m.group(1));
            if (v == null || v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String remplir(String modele, Map<String, String> variables, boolean html) {
        String resultat = modele;
        for (Map.Entry<String, String> v : variables.entrySet()) {
            String valeur = v.getValue() == null ? "" : v.getValue().trim();
            resultat = resultat.replace("{" + v.getKey() + "}", html ? echapper(valeur) : valeur);
        }
        return html ? resultat.replace("\n", "<br>") : resultat;
    }

    static String echapper(String texte) {
        StringBuilder sb = new StringBuilder(texte.length());
        for (char c : texte.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Mise en page sobre : styles en ligne (les messageries ignorent les feuilles de style), pas d'image ni d'emoji. */
    private static String enveloppe(String titre, String contenu) {
        return """
                <!DOCTYPE html>
                <html lang="fr"><head><meta charset="utf-8"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#f3f1ec;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f1ec;padding:24px 0;">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="width:600px;max-width:600px;background:#ffffff;border:1px solid #d9d5cc;">
                <tr><td style="padding:18px 28px;border-bottom:3px solid #003F8A;">
                <span style="font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:bold;letter-spacing:2px;color:#003F8A;">INPT</span>
                <span style="font-family:Georgia,'Times New Roman',serif;font-size:13px;color:#5a5a5a;padding-left:10px;">Plateforme des stages</span>
                </td></tr>
                <tr><td style="padding:26px 28px 10px 28px;font-family:Georgia,'Times New Roman',serif;font-size:15px;line-height:1.55;color:#1f1f1f;">
                <h1 style="font-size:18px;font-weight:normal;color:#003F8A;margin:0 0 18px 0;">%s</h1>
                %s</td></tr>
                <tr><td style="padding:14px 28px 20px 28px;border-top:1px solid #e6e2d9;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#7a7a7a;">
                Institut National des Postes et Télécommunications, Rabat. Message automatique : merci de ne pas y répondre.
                </td></tr>
                </table></td></tr></table>
                </body></html>
                """.formatted(titre, titre, contenu);
    }
}
