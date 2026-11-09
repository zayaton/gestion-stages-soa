package ma.inpt.stages.common.soap;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import ma.inpt.stages.common.Correlation;

/** Côté client : ajoute à chaque requête le jeton WS-Security et l'identifiant de corrélation. */
public final class GestionnaireClient implements SOAPHandler<SOAPMessageContext> {

    private final String utilisateur;
    private final String motDePasse;

    public GestionnaireClient(String utilisateur, String motDePasse) {
        this.utilisateur = utilisateur;
        this.motDePasse = motDePasse;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext contexte) {
        if (Boolean.TRUE.equals(contexte.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY))) {
            try {
                ajouterEntetes(contexte);
            } catch (SOAPException e) {
                throw new WebServiceException("Construction des en-têtes SOAP impossible.", e);
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void ajouterEntetes(SOAPMessageContext contexte) throws SOAPException {
        SOAPEnvelope enveloppe = contexte.getMessage().getSOAPPart().getEnvelope();
        SOAPHeader entete = enveloppe.getHeader() != null ? enveloppe.getHeader() : enveloppe.addHeader();

        SOAPElement jeton = entete.addChildElement("Security", "wsse", GestionnaireSecuriteServeur.NS_WSSE)
                .addChildElement("UsernameToken", "wsse");
        jeton.addChildElement("Username", "wsse").addTextNode(utilisateur);
        SOAPElement mot = jeton.addChildElement("Password", "wsse");
        mot.setAttribute("Type", GestionnaireSecuriteServeur.TYPE_TEXTE);
        mot.addTextNode(motDePasse);

        String correlation = Correlation.courant();
        if (correlation != null) {
            entete.addChildElement(Correlation.ELEMENT_SOAP, "cor", Correlation.NAMESPACE).addTextNode(correlation);
            Map<String, List<String>> http = (Map<String, List<String>>) contexte.get(MessageContext.HTTP_REQUEST_HEADERS);
            Map<String, List<String>> copie = http == null ? new HashMap<>() : new HashMap<>(http);
            copie.put(Correlation.ENTETE_HTTP, new ArrayList<>(List.of(correlation)));
            contexte.put(MessageContext.HTTP_REQUEST_HEADERS, copie);
        }
        contexte.getMessage().saveChanges();
    }

    @Override
    public boolean handleFault(SOAPMessageContext contexte) {
        return true;
    }

    @Override
    public void close(MessageContext contexte) {
        // Rien à libérer.
    }

    @Override
    public Set<QName> getHeaders() {
        return Set.of();
    }
}
