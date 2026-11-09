package ma.inpt.stages.common.rest;

import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Auteur d'une opération, pour les historiques. Le portail transmet la personne connectée dans X-Acteur
 * (par exemple « entreprise:3 ») ; à défaut, c'est le client authentifié par sa clé (« client:bonita »).
 */
public final class Acteur {

    public static final String ENTETE = "X-Acteur";

    private Acteur() {
    }

    public static String de(ContainerRequestContext requete) {
        String declare = requete.getHeaderString(ENTETE);
        if (declare != null && !declare.isBlank()) {
            String nettoye = declare.trim();
            return nettoye.length() > 120 ? nettoye.substring(0, 120) : nettoye;
        }
        Object client = requete.getProperty(FiltreSecurite.PROPRIETE_CLIENT);
        return "client:" + (client == null ? "inconnu" : client);
    }
}
