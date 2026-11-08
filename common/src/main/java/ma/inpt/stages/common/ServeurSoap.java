package ma.inpt.stages.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur HTTP d'un service SOAP : expose /health et accueillera les Endpoint JAX-WS,
 * ainsi le WSDL et le contrôle de santé partagent le même port.
 */
public final class ServeurSoap implements AutoCloseable {

    private final HttpServer serveur;
    private final ExecutorService executeur;

    public ServeurSoap(String hote, int port, VerificateurSante sante) throws IOException {
        serveur = HttpServer.create(new InetSocketAddress(hote, port), 0);
        executeur = Executors.newFixedThreadPool(16);
        serveur.setExecutor(executeur);
        serveur.createContext("/health", echange -> repondre(echange, sante.verifier()));
    }

    public HttpServer http() {
        return serveur;
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
        serveur.stop(1);
        executeur.shutdownNow();
    }
}
