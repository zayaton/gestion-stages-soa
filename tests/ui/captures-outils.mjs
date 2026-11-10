// Captures de pages web d'outils pour le rapport : Swagger UI de S4 et rapport HTML de Newman.
// Prérequis : S4 démarré ; tests\postman\rapports\rapport-newman.html produit par scripts\tests-postman.ps1.
import { chromium } from "playwright";
import { existsSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SORTIE = join(RACINE, "rapport", "figures");
const nav = await chromium.launch({ channel: process.env.NAVIGATEUR_CANAL || "msedge" });
const page = await nav.newPage({ viewport: { width: 1360, height: 860 }, locale: "fr-FR" });

await page.goto("http://localhost:8084/docs/");
await page.waitForSelector(".opblock-tag", { timeout: 20000 });
await page.waitForTimeout(800);
// Seul le groupe « Processus » reste déplié, pour que la capture tienne sur un écran.
for (const tag of await page.locator(".opblock-tag").all()) {
  const nom = (await tag.getAttribute("data-tag")) ?? "";
  const ouvert = (await tag.getAttribute("data-is-open")) === "true";
  if ((nom === "Processus") !== ouvert) await tag.click();
}
await page.waitForTimeout(800);
await page.locator(".opblock-tag[data-tag='Processus']").scrollIntoViewIfNeeded();
await page.evaluate(() => window.scrollBy(0, -120));
await page.screenshot({ path: join(SORTIE, "swagger-s4.png") });
console.log("  swagger-s4.png");

const rapport = join(RACINE, "tests", "postman", "rapports", "rapport-newman.html");
if (!existsSync(rapport)) throw new Error("Rapport Newman absent : lancer .\\scripts\\tests-postman.ps1");
await page.goto(pathToFileURL(rapport).href);
await page.waitForTimeout(1500);
await page.screenshot({ path: join(SORTIE, "newman-rapport.png") });
console.log("  newman-rapport.png");
await nav.close();
