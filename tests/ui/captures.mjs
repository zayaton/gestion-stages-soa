// Parcours du portail dans un vrai navigateur (Playwright) :
//  - joue un cycle complet par l'interface (candidature de Salma, évaluation puis acceptation par NovaPay Maghreb) ;
//  - visite chaque écran des trois espaces ;
//  - échoue si la console du navigateur affiche une erreur ou si une requête du portail répond en 5xx ;
//  - enregistre une capture par écran dans rapport/figures/ (préfixe ui-).
// Prérequis : base réinitialisée et services démarrés (scripts\captures-ui.ps1 s'en charge).
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const PORTAIL = process.env.PORTAIL_URL ?? "http://localhost:8090";
const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SORTIE = process.env.DOSSIER_CAPTURES ?? join(RACINE, "rapport", "figures");
// « msedge » (défaut du script PowerShell) : Edge installé sur le poste ; variable vide : Chromium de Playwright.
const CANAL = process.env.NAVIGATEUR_CANAL || undefined;
mkdirSync(SORTIE, { recursive: true });

const COMPTES = {
  etudiant: ["salma.elidrissi@inpt.ac.ma", "Etudiant@2026"],
  entreprise: ["contact@novapay-maghreb.ma", "Entreprise@2026"],
  responsable: ["responsable@inpt.ac.ma", "Responsable@2026"],
};

const problemes = [];
let captures = 0;

const navigateur = await chromium.launch({ channel: CANAL });
const contexte = await navigateur.newContext({ viewport: { width: 1360, height: 860 }, locale: "fr-FR", deviceScaleFactor: 1, reducedMotion: "reduce" }); // captures nettes : pas d'animation en cours

async function nouvellePage() {
  const page = await contexte.newPage();
  page.on("console", m => { if (m.type() === "error") problemes.push(`console (${page.url()}) : ${m.text()}`); });
  page.on("pageerror", e => problemes.push(`exception (${page.url()}) : ${e.message}`));
  page.on("response", r => {
    if (!r.url().startsWith(PORTAIL)) return;
    // 5xx partout ; 404 sur un fichier statique (les 404 de l'API sont des réponses métier attendues).
    if (r.status() >= 500 || (r.status() === 404 && !r.url().includes("/api/"))) problemes.push(`HTTP ${r.status()} : ${r.request().method()} ${r.url()}`);
  });
  return page;
}

/** Attend la fin du chargement de l'écran (indicateur « Chargement… » disparu, diagramme BPMN dessiné s'il y en a un). */
async function attendre(page) {
  // Pas de « networkidle » : il ne se stabilise pas avec les courriels affichés en iframe srcdoc.
  await page.waitForLoadState("load");
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => !document.querySelector("#contenu .chargement, .connexion .chargement"), null, { timeout: 15000 });
  if (await page.locator(".processus").count()) {
    await page.waitForSelector(".processus svg, .processus p", { timeout: 15000 });
    await page.waitForTimeout(400);
  }
}

async function capturer(page, nom, pleinePage = true) {
  await attendre(page);
  await page.screenshot({ path: join(SORTIE, `ui-${nom}.png`), fullPage: pleinePage });
  captures++;
  console.log(`  capture ui-${nom}.png`);
}

async function aller(page, route, nom) {
  await page.goto(`${PORTAIL}/${route}`);
  if (nom) await capturer(page, nom);
}

async function connecter(page, role) {
  await page.goto(`${PORTAIL}/`);
  await page.evaluate(() => sessionStorage.clear());
  await page.goto(`${PORTAIL}/`);
  await page.fill("input[name=identifiant]", COMPTES[role][0]);
  await page.fill("input[name=motDePasse]", COMPTES[role][1]);
  await page.click("button[type=submit]");
  await page.waitForSelector("nav.navigation");
  await attendre(page);
}

const page = await nouvellePage();

console.log("Connexion");
await page.goto(`${PORTAIL}/`);
await page.click(".comptes-demo summary");
await capturer(page, "connexion", false);

console.log("Espace étudiant (avant candidature)");
await connecter(page, "etudiant");
await capturer(page, "etudiant-offres");
await aller(page, "#/etudiant/offres/1");
await page.fill("textarea[name=lettre]", "Madame, Monsieur, ma formation en AMOA m'a préparée au recueil des besoins et à la recette ; votre application de paiement mobile m'intéresse particulièrement.");
await page.click("button:has-text('Envoyer ma candidature')");
await page.waitForSelector("text=Candidature enregistrée", { timeout: 30000 });
await capturer(page, "etudiant-candidature-deposee");
await page.click("a:has-text('Suivre cette candidature')");
await page.waitForURL(/#\/etudiant\/candidatures\/\d+/);
const idCandidature = page.url().match(/candidatures\/(\d+)/)[1];
await capturer(page, "etudiant-candidature-en-attente");

console.log(`Espace entreprise (candidature n° ${idCandidature})`);
await connecter(page, "entreprise");
await capturer(page, "entreprise-candidatures");
await aller(page, `#/entreprise/candidatures/${idCandidature}`, "entreprise-candidature-deposee");
await page.click("button:has-text(\"Démarrer l'évaluation\")");
await page.waitForSelector("text=Votre décision");
await page.check("input[name=decision][value=ACCEPTER]");
await page.fill("textarea", "Profil très en phase avec la mission. Démarrage prévu en février.");
await page.click("button:has-text('Enregistrer la décision')");
await page.waitForSelector("dialog.modale[open]");
await page.screenshot({ path: join(SORTIE, "ui-entreprise-confirmation-decision.png") });
captures++;
console.log("  capture ui-entreprise-confirmation-decision.png");
await page.click("dialog.modale button.bouton:not(.secondaire)");
await page.waitForSelector("text=Candidature acceptée");
await capturer(page, "entreprise-candidature-acceptee");
await aller(page, "#/entreprise/offres", "entreprise-offres");
await aller(page, "#/entreprise/offres/nouvelle", "entreprise-offre-formulaire");
await aller(page, "#/entreprise/profil", "entreprise-profil");

console.log("Espace étudiant (après décision)");
await connecter(page, "etudiant");
await aller(page, "#/etudiant/candidatures", "etudiant-candidatures");
await aller(page, `#/etudiant/candidatures/${idCandidature}`, "etudiant-candidature-acceptee");
await aller(page, "#/etudiant/notifications");
await attendre(page);
await page.locator("details.repli").first().evaluate(d => { d.open = true; });
await page.waitForSelector("iframe.courriel");
await page.waitForTimeout(300);
await capturer(page, "etudiant-notifications");
await aller(page, "#/etudiant/stage", "etudiant-stage");
await aller(page, "#/etudiant/profil", "etudiant-profil");

console.log("Espace responsable");
await connecter(page, "responsable");
await capturer(page, "responsable-tableau-de-bord");
await page.click("button:has-text('Envoyer les rappels maintenant')");
await page.waitForSelector("text=Rappels exécutés", { timeout: 30000 });
await capturer(page, "responsable-rappels");
await aller(page, "#/responsable/candidatures", "responsable-candidatures");
await aller(page, `#/responsable/candidatures/${idCandidature}`, "responsable-candidature-acceptee");
await aller(page, "#/responsable/candidatures/7", "responsable-candidature-refusee");
await aller(page, "#/responsable/stages", "responsable-stages");
await aller(page, "#/responsable/stages/1", "responsable-stage");
await aller(page, "#/responsable/etudiants", "responsable-etudiants");
await aller(page, "#/responsable/etudiants/1", "responsable-etudiant");
await aller(page, "#/responsable/entreprises", "responsable-entreprises");
await aller(page, "#/responsable/entreprises/1", "responsable-entreprise");
await aller(page, "#/responsable/notifications", "responsable-notifications");

console.log("Contrôles d'accès dans l'interface");
await aller(page, "#/etudiant/offres");
await attendre(page);
if (!page.url().includes("#/responsable/")) problemes.push(`un responsable a pu ouvrir l'espace étudiant : ${page.url()}`);
await page.click("button:has-text('Se déconnecter')");
await page.waitForSelector("text=Vous êtes déconnecté.");

await navigateur.close();
console.log(`\n${captures} capture(s) dans ${SORTIE}`);
if (problemes.length) {
  console.error(`\n${problemes.length} problème(s) relevé(s) dans le navigateur :`);
  problemes.forEach(p => console.error(`  - ${p}`));
  process.exit(1);
}
console.log("Aucune erreur de console, aucune réponse 5xx du portail.");
