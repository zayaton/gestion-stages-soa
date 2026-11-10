package ma.inpt.stages.bonita;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bonitasoft.engine.bpm.bar.BarResource;
import org.bonitasoft.engine.bpm.bar.BusinessArchive;
import org.bonitasoft.engine.bpm.bar.BusinessArchiveBuilder;
import org.bonitasoft.engine.bpm.bar.BusinessArchiveFactory;
import org.bonitasoft.engine.bpm.contract.Type;
import org.bonitasoft.engine.bpm.flownode.GatewayType;
import org.bonitasoft.engine.bpm.flownode.TimerType;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.impl.ProcessDefinitionBuilder;
import org.bonitasoft.engine.bpm.process.impl.UserTaskDefinitionBuilder;
import org.bonitasoft.engine.expression.Expression;
import org.bonitasoft.engine.expression.ExpressionBuilder;
import org.bonitasoft.engine.operation.OperationBuilder;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * Construit le processus « Candidature » 1.0 avec l'API du moteur Bonita, puis le déploie par l'API REST de Bonita.
 *
 * <p>Le processus reprend le diagramme de docs/bonita/processus-candidature.bpmn (mêmes noms de tâches, de passerelles et
 * d'événements) en <b>mode secours</b> du guide : chaque tâche de service est une opération Groovy unique, qui appelle
 * elle-même les services avec la classe {@code inpt.SoaProcessus} (livrée dans l'archive). Ce mode ne demande aucun
 * connecteur de la Marketplace. En cas de panne, la méthode appelée signale l'incident (S4, responsable) puis échoue.
 *
 * <p>Usage : {@code Deploiement <dossier du projet> [url de Bonita] [utilisateur] [mot de passe]}
 */
public final class Deploiement {

    private static final String NOM = "Candidature";
    private static final String VERSION = "1.0";
    /** Bonita exige que la valeur affectée garde la classe de la valeur courante : toujours un HashMap. */
    private static final String MAP = java.util.HashMap.class.getName();
    private static final String TACHE_DECISION = "Décision de l'entreprise";

    private Deploiement() {
    }

    public static void main(String[] args) throws Exception {
        Path projet = Path.of(args.length > 0 ? args[0] : "../../..").toAbsolutePath().normalize();
        String bonita = args.length > 1 ? args[1] : "http://localhost:8080/bonita";
        String utilisateur = args.length > 2 ? args[2] : "walter.bates";
        String motDePasse = args.length > 3 ? args[3] : "bpm";

        byte[] classe = compilerSoaProcessus(projet.resolve("docs/bonita/groovy/inpt/SoaProcessus.groovy"));
        BusinessArchive archive = new BusinessArchiveBuilder().createNewBusinessArchive()
                .setProcessDefinition(definition())
                .addClasspathResource(new BarResource("soa-processus.jar", classe))
                .done();
        Path bar = projet.resolve("bpmn").resolve(NOM + "--" + VERSION + ".bar");
        Files.deleteIfExists(bar);
        BusinessArchiveFactory.writeBusinessArchiveToFile(archive, bar.toFile());
        System.out.println("Archive construite : " + bar);

        new ClientBonita(bonita, utilisateur, motDePasse).deployer(bar, utilisateur);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Définition du processus

    static DesignProcessDefinition definition() throws Exception {
        ProcessDefinitionBuilder p = new ProcessDefinitionBuilder().createNewInstance(NOM, VERSION);
        p.addDescription("Traitement d'une candidature à une offre de stage (étapes 1 à 9 de l'énoncé). Mode secours : "
                + "chaque tâche appelle les services par la classe inpt.SoaProcessus.");
        p.addActor("Étudiant", true);
        p.addActor("Système");
        p.addActor("Entreprise");

        p.addContract()
                .addInput("soumissionId", Type.LONG, "Soumission créée par S4")
                .addInput("etudiantId", Type.LONG, "Étudiant")
                .addInput("offreId", Type.LONG, "Offre")
                .addInput("lettreMotivation", Type.TEXT, "Lettre de motivation")
                .addConstraint("identifiants", "etudiantId > 0 && offreId > 0", "Identifiants invalides", "etudiantId", "offreId");

        p.addData("dossier", MAP, groovy("initialiser", "new java.util.HashMap(inpt.SoaProcessus.initialiser(soumissionId, etudiantId, offreId, lettreMotivation))", MAP,
                entree("soumissionId", Long.class), entree("etudiantId", Long.class), entree("offreId", Long.class),
                entree("lettreMotivation", String.class)));

        // Étapes 1 à 6
        p.addStartEvent("Candidature déposée");
        tache(p, "Vérification de l'étudiant", "inpt.SoaProcessus.verifierEtudiant(dossier)");
        p.addGateway("Étudiant éligible ?", GatewayType.EXCLUSIVE);
        tache(p, "Vérification de l'offre", "inpt.SoaProcessus.verifierOffre(dossier)");
        p.addGateway("Offre existante et active ?", GatewayType.EXCLUSIVE);
        tache(p, "Vérification de l'entreprise et du profil", "inpt.SoaProcessus.verifierEntrepriseEtProfil(dossier)");
        tache(p, "Contrôle de la candidature", "inpt.SoaProcessus.controlerCandidature(dossier)");
        p.addGateway("Conditions satisfaites ?", GatewayType.EXCLUSIVE);
        tache(p, "Enregistrement de la candidature", "inpt.SoaProcessus.enregistrer(dossier)");
        tache(p, "Confirmation du dépôt", "inpt.SoaProcessus.confirmerDepot(dossier)");
        tache(p, "Transmission à l'entreprise", "inpt.SoaProcessus.transmettre(dossier)");
        tache(p, "Enregistrement du rejet", "inpt.SoaProcessus.enregistrerRejet(dossier)");
        tache(p, "Notification du rejet", "inpt.SoaProcessus.notifierRejet(dossier)");
        p.addEndEvent("Candidature rejetée");

        // Étape 7 : tâche humaine, avec relance au bout de 7 jours
        UserTaskDefinitionBuilder decision = p.addUserTask(TACHE_DECISION, "Entreprise");
        decision.addContract()
                .addInput("decision", Type.TEXT, "ACCEPTER, REFUSER ou DEMANDER_COMPLEMENT")
                .addInput("commentaire", Type.TEXT, "Commentaire pour l'étudiant")
                .addConstraint("decisionConnue", "['ACCEPTER', 'REFUSER', 'DEMANDER_COMPLEMENT'].contains(decision)", "Décision inconnue", "decision");
        decision.addOperation(new OperationBuilder().createSetDataOperation("dossier",
                groovy("apresDecision", "new java.util.HashMap(inpt.SoaProcessus.apresDecision(dossier, decision, commentaire))", MAP, dossier(),
                        entree("decision", String.class), entree("commentaire", String.class))));
        decision.addBoundaryEvent("7 jours sans décision", true)
                .addTimerEventTriggerDefinition(TimerType.DURATION, new ExpressionBuilder().createConstantLongExpression(7L * 24 * 3600 * 1000));
        tache(p, "Relance de l'entreprise", "inpt.SoaProcessus.relancer(dossier)");
        p.addGateway("Décision ?", GatewayType.EXCLUSIVE);
        tache(p, "Demande de complément", "inpt.SoaProcessus.demanderComplement(dossier)");

        // Étapes 8 et 9
        tache(p, "Réservation de la place", "inpt.SoaProcessus.retirerPlace(dossier)");
        tache(p, "Enregistrement de l'acceptation", "inpt.SoaProcessus.enregistrerStatut(dossier, 'ACCEPTEE')");
        tache(p, "Notification du résultat", "inpt.SoaProcessus.notifierAcceptation(dossier)");
        tache(p, "Création du dossier de stage", "inpt.SoaProcessus.creerStage(dossier)");
        tache(p, "Mise à jour du statut de l'étudiant", "inpt.SoaProcessus.passerEnStage(dossier)");
        p.addEndEvent("Candidature acceptée");
        tache(p, "Enregistrement du refus", "inpt.SoaProcessus.enregistrerStatut(dossier, 'REFUSEE')");
        tache(p, "Notification du refus", "inpt.SoaProcessus.notifierRefus(dossier)");
        p.addEndEvent("Candidature refusée");

        // Flux
        p.addTransition("Candidature déposée", "Vérification de l'étudiant");
        p.addTransition("Vérification de l'étudiant", "Étudiant éligible ?");
        p.addTransition("Étudiant éligible ?", "Vérification de l'offre", condition("etudiantEligible", "dossier.etudiantEligible == true"));
        p.addDefaultTransition("Étudiant éligible ?", "Conditions satisfaites ?");
        p.addTransition("Vérification de l'offre", "Offre existante et active ?");
        p.addTransition("Offre existante et active ?", "Vérification de l'entreprise et du profil",
                condition("offreValable", "inpt.SoaProcessus.offreValable(dossier)"));
        p.addDefaultTransition("Offre existante et active ?", "Conditions satisfaites ?");
        p.addTransition("Vérification de l'entreprise et du profil", "Contrôle de la candidature");
        p.addTransition("Contrôle de la candidature", "Conditions satisfaites ?");
        p.addTransition("Conditions satisfaites ?", "Enregistrement de la candidature",
                condition("conditionsSatisfaites", "inpt.SoaProcessus.conditionsSatisfaites(dossier)"));
        p.addDefaultTransition("Conditions satisfaites ?", "Enregistrement du rejet");
        p.addTransition("Enregistrement de la candidature", "Confirmation du dépôt");
        p.addTransition("Confirmation du dépôt", "Transmission à l'entreprise");
        p.addTransition("Transmission à l'entreprise", TACHE_DECISION);
        p.addTransition("Enregistrement du rejet", "Notification du rejet");
        p.addTransition("Notification du rejet", "Candidature rejetée");
        p.addTransition("7 jours sans décision", "Relance de l'entreprise");
        p.addTransition("Relance de l'entreprise", TACHE_DECISION);
        p.addTransition(TACHE_DECISION, "Décision ?");
        p.addTransition("Décision ?", "Réservation de la place", condition("accepter", "inpt.SoaProcessus.decisionEst(dossier, 'ACCEPTER')"));
        p.addTransition("Décision ?", "Enregistrement du refus", condition("refuser", "inpt.SoaProcessus.decisionEst(dossier, 'REFUSER')"));
        p.addTransition("Décision ?", "Demande de complément", condition("complement", "inpt.SoaProcessus.decisionEst(dossier, 'DEMANDER_COMPLEMENT')"));
        p.addTransition("Demande de complément", TACHE_DECISION);
        p.addTransition("Réservation de la place", "Enregistrement de l'acceptation");
        p.addTransition("Enregistrement de l'acceptation", "Notification du résultat");
        p.addTransition("Notification du résultat", "Création du dossier de stage");
        p.addTransition("Création du dossier de stage", "Mise à jour du statut de l'étudiant");
        p.addTransition("Mise à jour du statut de l'étudiant", "Candidature acceptée");
        p.addTransition("Enregistrement du refus", "Notification du refus");
        p.addTransition("Notification du refus", "Candidature refusée");
        return p.done();
    }

    /** Tâche de service du couloir Système : une opération, dossier = script(dossier). */
    private static void tache(ProcessDefinitionBuilder p, String nom, String script) throws Exception {
        p.addAutomaticTask(nom).addOperation(new OperationBuilder().createSetDataOperation("dossier",
                groovy(identifiant(nom), "new java.util.HashMap(" + script + ")", MAP, dossier())));
    }

    private static Expression condition(String nom, String script) throws Exception {
        return groovy(nom, script, Boolean.class.getName(), dossier());
    }

    private static Expression groovy(String nom, String script, String type, Expression... dependances) throws Exception {
        return new ExpressionBuilder().createGroovyScriptExpression(nom, script, type, dependances);
    }

    private static Expression dossier() throws Exception {
        return new ExpressionBuilder().createDataExpression("dossier", MAP);
    }

    private static Expression entree(String nom, Class<?> type) throws Exception {
        return new ExpressionBuilder().createContractInputExpression(nom, type.getName());
    }

    private static String identifiant(String nom) {
        return java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD).replaceAll("[^A-Za-z]", "");
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Classe Groovy livrée avec le processus

    static byte[] compilerSoaProcessus(Path source) throws IOException {
        Path classes = Files.createTempDirectory("soa-processus");
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setTargetDirectory(classes.toFile());
        configuration.setSourceEncoding("UTF-8");
        CompilationUnit unite = new CompilationUnit(configuration);
        unite.addSource(source.toFile());
        unite.compile();
        ByteArrayOutputStream jar = new ByteArrayOutputStream();
        try (JarOutputStream sortie = new JarOutputStream(jar); Stream<Path> fichiers = Files.walk(classes)) {
            for (Path f : fichiers.filter(Files::isRegularFile).toList()) {
                sortie.putNextEntry(new JarEntry(classes.relativize(f).toString().replace('\\', '/')));
                sortie.write(Files.readAllBytes(f));
                sortie.closeEntry();
            }
        }
        System.out.println("Classe inpt.SoaProcessus compilée (Groovy " + groovy.lang.GroovySystem.getVersion() + ").");
        return jar.toByteArray();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // API REST de Bonita

    static final class ClientBonita {
        private final String base;
        private final HttpClient http;
        private String jeton;

        ClientBonita(String base, String utilisateur, String motDePasse) throws Exception {
            this.base = base;
            CookieManager cookies = new CookieManager();
            this.http = HttpClient.newBuilder().cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10)).build();
            String corps = "username=" + URLEncoder.encode(utilisateur, StandardCharsets.UTF_8) + "&password="
                    + URLEncoder.encode(motDePasse, StandardCharsets.UTF_8) + "&redirect=false";
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/loginservice"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corps)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException("Connexion à Bonita refusée (" + r.statusCode() + ") : " + r.body());
            }
            jeton = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("X-Bonita-API-Token"))
                    .map(java.net.HttpCookie::getValue).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Bonita n'a pas renvoyé de jeton X-Bonita-API-Token."));
            System.out.println("Connecté à " + base + " (" + utilisateur + ").");
        }

        void deployer(Path bar, String utilisateur) throws Exception {
            // Une version 1.0 déjà déployée est retirée d'abord (cas compris) : S4 cherche ce nom et cette version.
            String existant = premier(get("/API/bpm/process?p=0&c=10&f=name=" + enc(NOM) + "&f=version=" + VERSION), "id");
            if (existant != null) {
                put("/API/bpm/process/" + existant, "{\"activationState\":\"DISABLED\"}");
                supprimerCas(existant);
                envoyer("DELETE", "/API/bpm/process/" + existant, null, 200, 204);
                System.out.println("Ancienne version retirée (processus " + existant + ").");
            }
            String fichier = televerser(bar);
            String processus = champ(envoyer("POST", "/API/bpm/process", "{\"fileupload\":\"" + fichier + "\"}", 200), "id");
            String idUtilisateur = premier(get("/API/identity/user?p=0&c=10&f=userName=" + enc(utilisateur)), "id");
            if (idUtilisateur == null) {
                throw new IllegalStateException("Utilisateur Bonita introuvable : " + utilisateur);
            }
            for (String acteur : tous(get("/API/bpm/actor?p=0&c=50&f=process_id=" + processus), "id")) {
                envoyer("POST", "/API/bpm/actorMember", "{\"actor_id\":\"" + acteur + "\",\"user_id\":\"" + idUtilisateur + "\"}", 200);
            }
            put("/API/bpm/process/" + processus, "{\"activationState\":\"ENABLED\"}");
            String etat = champ(get("/API/bpm/process/" + processus), "activationState");
            if (!"ENABLED".equals(etat)) {
                throw new IllegalStateException("Le processus est déployé mais pas activé (état " + etat + ").");
            }
            System.out.println("Processus " + NOM + " " + VERSION + " déployé et activé (identifiant " + processus + ").");
        }

        private void supprimerCas(String processus) throws Exception {
            for (String route : List.of("/API/bpm/case", "/API/bpm/archivedCase")) {
                List<String> ids;
                while (!(ids = tous(get(route + "?p=0&c=100&f=processDefinitionId=" + processus), route.endsWith("archivedCase") ? "sourceObjectId" : "id")).isEmpty()) {
                    for (String id : ids) {
                        envoyer("DELETE", route + "/" + id, null, 200, 204, 404);
                    }
                    if (route.endsWith("archivedCase")) {
                        break;
                    }
                }
            }
        }

        private String televerser(Path bar) throws Exception {
            String limite = "----limite" + UUID.randomUUID();
            ByteArrayOutputStream corps = new ByteArrayOutputStream();
            corps.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + bar.getFileName()
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            corps.write(Files.readAllBytes(bar));
            corps.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/portal/processUpload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite).header("X-Bonita-API-Token", jeton)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corps.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("Téléversement refusé (" + r.statusCode() + ") : " + r.body());
            }
            return r.body().trim();
        }

        private String get(String chemin) throws Exception {
            return envoyer("GET", chemin, null, 200);
        }

        private void put(String chemin, String json) throws Exception {
            envoyer("PUT", chemin, json, 200, 204);
        }

        private String envoyer(String methode, String chemin, String json, int... attendus) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + chemin)).header("X-Bonita-API-Token", jeton)
                    .timeout(Duration.ofSeconds(60));
            if (json != null) {
                b.header("Content-Type", "application/json");
            }
            b.method(methode, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            for (int code : attendus) {
                if (r.statusCode() == code) {
                    return r.body();
                }
            }
            throw new IllegalStateException(methode + " " + chemin + " : " + r.statusCode() + " " + r.body());
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }

        /** Valeur d'un champ texte d'un objet JSON (réponses simples de l'API Bonita). */
        private static String champ(String json, String nom) {
            Matcher m = Pattern.compile("\"" + nom + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            return m.find() ? m.group(1) : null;
        }

        private static String premier(String json, String nom) {
            List<String> l = tous(json, nom);
            return l.isEmpty() ? null : l.get(0);
        }

        private static List<String> tous(String json, String nom) {
            Matcher m = Pattern.compile("\"" + nom + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            List<String> l = new java.util.ArrayList<>();
            while (m.find()) {
                l.add(m.group(1));
            }
            return l;
        }
    }
}
