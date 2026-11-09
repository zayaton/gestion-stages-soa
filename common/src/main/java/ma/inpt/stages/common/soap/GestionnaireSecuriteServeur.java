package ma.inpt.stages.common.soap;

import jakarta.xml.soap.Name;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import jakarta.xml.ws.soap.SOAPFaultException;
import java.util.Iterator;
import java.util.Set;
import javax.xml.namespace.QName;
import ma.inpt.stages.common.ComptesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exige un en-tête WS-Security UsernameToken sur chaque requête.
 * Un en-tête absent ou des identifiants faux donnent une faute SOAP, avant tout traitement métier.
 */
public final class GestionnaireSecuriteServeur implements SOAPHandler<SOAPMessageContext> {

    public static final String NS_WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final String TYPE_TEXTE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireSecuriteServeur.class);
    private static final QName SECURITY = new QName(NS_WSSE, "Security");
    private static final QName USERNAME_TOKEN = new QName(NS_WSSE, "UsernameToken");
    private static final QName USERNAME = new QName(NS_WSSE, "Username");
    private static final QName PASSWORD = new QName(NS_WSSE, "Password");

    private final ComptesService comptes;

    public GestionnaireSecuriteServeur(ComptesService comptes) {
        this.comptes = comptes;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext contexte) {
        if (Boolean.TRUE.equals(contexte.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY))) {
            return true;
        }
        try {
            verifier(contexte);
        } catch (SOAPException e) {
            throw faute("InvalidSecurity", "L'en-tête de sécurité est illisible.");
        }
        return true;
    }

    private void verifier(SOAPMessageContext contexte) throws SOAPException {
        SOAPHeader entete = contexte.getMessage().getSOAPHeader();
        SOAPElement jeton = entete == null ? null : enfant(entete, SECURITY);
        jeton = jeton == null ? null : enfant(jeton, USERNAME_TOKEN);
        if (jeton == null) {
            LOG.warn("Appel SOAP refusé : en-tête WS-Security absent");
            throw faute("InvalidSecurity", "En-tête de sécurité WS-Security UsernameToken absent.");
        }
        SOAPElement utilisateur = enfant(jeton, USERNAME);
        SOAPElement motDePasse = enfant(jeton, PASSWORD);
        if (utilisateur == null || motDePasse == null) {
            throw faute("InvalidSecurity", "Le jeton de sécurité doit contenir Username et Password.");
        }
        String type = motDePasse.getAttribute("Type");
        if (!type.isEmpty() && !TYPE_TEXTE.equals(type)) {
            throw faute("UnsupportedSecurityToken", "Seul le type de mot de passe PasswordText est accepté.");
        }
        String nom = utilisateur.getTextContent().trim();
        if (!comptes.authentifier(nom, motDePasse.getTextContent())) {
            LOG.warn("Appel SOAP refusé : identifiants invalides pour le compte {}", nom);
            throw faute("FailedAuthentication", "Identifiants de service invalides.");
        }
        String operation = operation(contexte);
        if (contexte.get(MessageContext.WSDL_OPERATION) == null) {
            LOG.warn("Appel SOAP refusé : opération inconnue {}", operation);
            throw fauteClient("L'opération " + operation + " n'existe pas dans ce service.");
        }
        LOG.info("Appel SOAP {} par le compte {}", operation, nom);
    }

    private static String operation(SOAPMessageContext contexte) throws SOAPException {
        SOAPBody corps = contexte.getMessage().getSOAPBody();
        Iterator<?> enfants = corps.getChildElements();
        while (enfants.hasNext()) {
            if (enfants.next() instanceof SOAPElement element) {
                return element.getLocalName();
            }
        }
        return "inconnue";
    }

    private static SOAPElement enfant(SOAPElement parent, QName nom) {
        Iterator<?> enfants = parent.getChildElements(nom);
        return enfants.hasNext() ? (SOAPElement) enfants.next() : null;
    }

    private static SOAPFaultException faute(String code, String message) {
        try {
            Name nom = SOAPFactory.newInstance().createName(code, "wsse", NS_WSSE);
            SOAPFault faute = SOAPFactory.newInstance().createFault();
            faute.setFaultCode(nom);
            faute.setFaultString(message);
            return new SOAPFaultException(faute);
        } catch (SOAPException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SOAPFaultException fauteClient(String message) {
        try {
            SOAPFault faute = SOAPFactory.newInstance().createFault();
            faute.setFaultCode(new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Client", "S"));
            faute.setFaultString(message);
            return new SOAPFaultException(faute);
        } catch (SOAPException e) {
            throw new IllegalStateException(e);
        }
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
        return Set.of(SECURITY);
    }
}
