// Exporte le diagramme BPMN du processus en PNG pour le rapport (rapport/figures/bpmn-processus.png).
// Le fichier rendu est docs/bonita/processus-candidature.bpmn, dessiné par bpmn-js (celui du portail) dans Microsoft Edge ;
// le script échoue si bpmn-js signale le moindre avertissement à l'import. Prérequis : portail démarré, Playwright installé dans tests/ui (scripts\captures-ui.ps1 l'installe).
// Usage : node rapport/outils/exporter_bpmn.mjs
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const { chromium } = createRequire(join(RACINE, "tests", "ui", "package.json"))("playwright");
const PORTAIL = process.env.PORTAIL_URL ?? "http://localhost:8090";
const SORTIE = join(RACINE, "rapport", "figures", "bpmn-processus.png");
// Lu sur le disque (et non servi par le portail) : pas besoin de reconstruire le portail après une régénération.
const XML = readFileSync(join(RACINE, "docs", "bonita", "processus-candidature.bpmn"), "utf8");

const navigateur = await chromium.launch({ channel: process.env.NAVIGATEUR_CANAL ?? "msedge" });
const page = await navigateur.newPage({ viewport: { width: 2000, height: 1000 }, deviceScaleFactor: 2 });
await page.goto(`${PORTAIL}/`);
await page.waitForFunction(() => window.BpmnJS);
const taille = await page.evaluate(async xml => {
  document.body.innerHTML = "";
  document.body.style.margin = "0";
  document.body.style.background = "#fff";
  const zone = document.createElement("div");
  zone.id = "diagramme";
  zone.style.cssText = "width:2000px;height:1000px;background:#fff";
  document.body.append(zone);
  const visionneuse = new window.BpmnJS({ container: zone });
  const { warnings } = await visionneuse.importXML(xml);
  if (warnings.length) throw new Error(`Avertissements à l'import : ${warnings.map(w => w.message).join(" ; ")}`);
  const canvas = visionneuse.get("canvas");
  const boite = canvas.viewbox().inner;
  // Cadre ajusté au diagramme, avec une marge, puis zoom 1:1 pour garder les libellés nets.
  const marge = 20;
  zone.style.width = `${Math.ceil(boite.width + 2 * marge)}px`;
  zone.style.height = `${Math.ceil(boite.height + 2 * marge)}px`;
  canvas.resized();
  canvas.viewbox({ x: boite.x - marge, y: boite.y - marge, width: boite.width + 2 * marge, height: boite.height + 2 * marge });
  // Le filigrane bpmn.io reste visible : la licence de bpmn-js interdit de le retirer.
  return { largeur: zone.style.width, hauteur: zone.style.height };
}, XML);
await page.setViewportSize({ width: parseInt(taille.largeur, 10), height: parseInt(taille.hauteur, 10) });
await page.waitForTimeout(300);
await page.locator("#diagramme").screenshot({ path: SORTIE });
await navigateur.close();
console.log(`Diagramme exporté : ${SORTIE} (${taille.largeur} × ${taille.hauteur} à l'échelle 2)`);
