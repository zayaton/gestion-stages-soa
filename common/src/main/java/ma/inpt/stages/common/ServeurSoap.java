package ma.inpt.stages.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.handler.Handler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ma.inpt.stages.common.soap.GestionnaireCorrelationServeur;
import ma.inpt.stages.common.soap.GestionnaireSecuriteServeur;

/**
 * Serveur HTTP d'un service SOAP : le contrôle de santé et l'Endpoint JAX-WS partagent le même port.
 * Chaque requête passe par la corrélation puis par le contrôle WS-Security.
 */
public final class ServeurSoap implements AutoCloseable {

    static {
        // Aucune trace de pile dans les fautes renvoyées aux clients.
        System.setProperty("com.sun.xml.ws.fault.SOAPFaultBuilder.disableCaptureStackTrace", "true");
    }

    private final HttpServer serveur;
    private final ExecutorService executeur;
    private final List<Endpoint> endpoints = new ArrayList<>();

    public ServeurSoap(String hote, int port, VerificateurSante sante) throws IOException {
        serveur = HttpServer.create(new InetSocketAddress(hote, port), 0);
        executeur = Executors.newFixedThreadPool(16);
        serveur.setExecutor(executeur);
        serveur.createContext("/health", echange -> repondre(echange, sante.verifier()));
    }

    /** Publie le service sur ce chemin : le WSDL est servi à chemin?wsdl, sans authentification. */
    @SuppressWarnings("rawtypes")
    public void publier(String chemin, Object implementation, ComptesService comptes) {
        Endpoint endpoint = Endpoint.create(implementation);
        List<Handler> chaine = new ArrayList<>();
        // JAX-WS parcourt cette liste à l'envers en entrée : la corrélation passe donc avant la sécurité.
        chaine.add(new GestionnaireSecuriteServeur(comptes));
        chaine.add(new GestionnaireCorrelationServeur());
        endpoint.getBinding().setHandlerChain(chaine);
        endpoint.publish(serveur.createContext(chemin));
        endpoints.add(endpoint);
    }

    public void demarrer() {
        serveur.start();
    }

    public int port() {
        return serveur.getAddress().getPort();
    }

    private static void repondre(HttpExchange echange, VerificateurSante.Etat etat) throws IOException {
        byte[] corps = etat.json().getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        echange.sendResponseHeaders(etat.codeHttp(), corps.length);
        try (OutputStream sortie = echange.getResponseBody()) {
            sortie.write(corps);
        }
    }

    @Override
    public void close() {
        endpoints.forEach(Endpoint::stop);
        serveur.stop(1);
        executeur.shutdownNow();
    }
}
