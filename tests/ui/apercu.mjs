// Aperçu rapide de l'interface : quelques écrans des trois espaces, sans modifier les données.
// Usage : node tests\ui\apercu.mjs <dossier de sortie>
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { join } from "node:path";

const PORTAIL = process.env.PORTAIL_URL ?? "http://localhost:8090";
const SORTIE = process.argv[2] ?? ".";
mkdirSync(SORTIE, { recursive: true });

const navigateur = await chromium.launch({ channel: process.env.NAVIGATEUR_CANAL ?? "msedge" });
const contexte = await navigateur.newContext({ viewport: { width: 1360, height: 860 }, locale: "fr-FR", reducedMotion: "reduce" });
const page = await contexte.newPage();
const erreurs = [];
page.on("console", m => { if (m.type() === "error") erreurs.push(m.text()); });

async function attendre() {
  await page.waitForLoadState("load");
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => !document.querySelector(".chargement"), null, { timeout: 15000 });
  await page.waitForTimeout(500);
}
async function capture(nom, plein = false) { await attendre(); await page.screenshot({ path: join(SORTIE, `${nom}.png`), fullPage: plein }); }
async function connecter(id, mdp) {
  await page.goto(PORTAIL); await attendre();
  await page.fill("input[name=identifiant]", id); await page.fill("input[name=motDePasse]", mdp);
  await page.click("button[type=submit]");
  await page.waitForSelector(".bandeau");
}

await page.goto(PORTAIL); await capture("connexion");
await connecter("salma.elidrissi@inpt.ac.ma", "Etudiant@2026");
await capture("etudiant-offres");
await page.click(".offre-titre >> nth=0"); await capture("etudiant-offre", true);
await page.goto(`${PORTAIL}/#/etudiant/candidatures`); await capture("etudiant-candidatures");
await page.click("button:has-text('Se déconnecter')");
await connecter("contact@novapay-maghreb.ma", "Entreprise@2026");
await capture("entreprise-candidatures");
await page.click("button:has-text('Se déconnecter')");
await connecter("responsable@inpt.ac.ma", "Responsable@2026");
await capture("responsable-tableau", true);
await page.goto(`${PORTAIL}/#/responsable/candidatures`); await capture("responsable-candidatures");
await navigateur.close();
console.log(erreurs.length ? `Erreurs console : ${erreurs.join(" | ")}` : "Aucune erreur console");
