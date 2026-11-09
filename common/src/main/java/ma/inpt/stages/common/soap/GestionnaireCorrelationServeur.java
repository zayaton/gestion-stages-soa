package ma.inpt.stages.common.soap;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import ma.inpt.stages.common.Correlation;

/** Côté serveur : lit l'identifiant de corrélation (en-tête SOAP, sinon HTTP) et le place dans les journaux. */
public final class GestionnaireCorrelationServeur implements SOAPHandler<SOAPMessageContext> {

    private static final QName ENTETE = new QName(Correlation.NAMESPACE, Correlation.ELEMENT_SOAP);

    @Override
    public boolean handleMessage(SOAPMessageContext contexte) {
        if (!Boolean.TRUE.equals(contexte.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY))) {
            Correlation.definir(lire(contexte));
        }
        return true;
    }

    private static String lire(SOAPMessageContext contexte) {
        try {
            SOAPHeader entete = contexte.getMessage().getSOAPHeader();
            if (entete != null) {
                Iterator<?> elements = entete.getChildElements(ENTETE);
                if (elements.hasNext()) {
                    return ((SOAPElement) elements.next()).getTextContent();
                }
            }
        } catch (SOAPException e) {
            return null;
        }
        return lireEnteteHttp(contexte);
    }

    @SuppressWarnings("unchecked")
    private static String lireEnteteHttp(SOAPMessageContext contexte) {
        Map<String, List<String>> entetes = (Map<String, List<String>>) contexte.get(MessageContext.HTTP_REQUEST_HEADERS);
        if (entetes != null) {
            for (Map.Entry<String, List<String>> entree : entetes.entrySet()) {
                if (Correlation.ENTETE_HTTP.equalsIgnoreCase(entree.getKey()) && !entree.getValue().isEmpty()) {
                    return entree.getValue().get(0);
                }
            }
        }
        return null;
    }

    @Override
    public boolean handleFault(SOAPMessageContext contexte) {
        return true;
    }

    @Override
    public void close(MessageContext contexte) {
        Correlation.effacer();
    }

    @Override
    public Set<QName> getHeaders() {
        return Set.of(ENTETE);
    }
}
