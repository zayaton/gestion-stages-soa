package ma.inpt.stages.common.soap;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.handler.Handler;
import java.util.List;
import ma.inpt.stages.common.Configuration;

/** Configure un port JAX-WS généré : adresse, délais d'attente, sécurité et corrélation. */
public final class ClientSoap {

    private static final String DELAI_CONNEXION = "com.sun.xml.ws.connect.timeout";
    private static final String DELAI_LECTURE = "com.sun.xml.ws.request.timeout";

    private ClientSoap() {
    }

    @SuppressWarnings("rawtypes")
    public static <P> P configurer(P port, String url, Configuration config) {
        BindingProvider fournisseur = (BindingProvider) port;
        fournisseur.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        fournisseur.getRequestContext().put(DELAI_CONNEXION, config.entier("soa.ws.delai.connexion.ms"));
        fournisseur.getRequestContext().put(DELAI_LECTURE, config.entier("soa.ws.delai.lecture.ms"));
        List<Handler> chaine = List.of(new GestionnaireClient(
                config.valeur("soa.ws.client.utilisateur"), config.valeur("soa.ws.client.password")));
        fournisseur.getBinding().setHandlerChain(chaine);
        return port;
    }
}
