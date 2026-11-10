// Captures du portail Bonita pour le rapport (rapport/figures/bonita-*.png), sur de vrais cas du processus déployé :
//  - Salma (offre 1) : dépôt, évaluation, acceptation -> cas archivé ;
//  - Mehdi (offre 4) : dépôt -> cas en attente de la décision de l'entreprise ;
//  - Aïcha (offre 10) pendant une panne de S3 -> tâche en erreur, soumission en ERREUR.
// Prérequis (scripts\captures-bonita.ps1 s'en charge) : processus redéployé (aucun ancien cas), bases réinitialisées,
// pile démarrée en mode bonita.
import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SORTIE = join(RACINE, "rapport", "figures");
const B = "http://localhost:8080/bonita";
const S4 = "http://localhost:8084/api/v1";
const CLE = { "X-API-Key": "cle-postman-2026-Rb3WsE", "Content-Type": "application/json" };
const attendre = ms => new Promise(r => setTimeout(r, ms));

async function s4(methode, chemin, corps, acteur) {
  const r = await fetch(S4 + chemin, { method: methode, headers: { ...CLE, ...(acteur ? { "X-Acteur": acteur } : {}) }, body: corps ? JSON.stringify(corps) : undefined });
  const texte = await r.text();
  if (r.status >= 400) throw new Error(`${methode} ${chemin} : ${r.status} ${texte}`);
  return texte ? JSON.parse(texte) : null;
}

/** Dépose une candidature et attend la fin du traitement automatique par Bonita. */
async function deposer(etudiant, offre, attendu) {
  const s = await s4("POST", "/soumissions", { etudiantId: etudiant, offreId: offre,
    lettreMotivation: "Je souhaite rejoindre votre équipe : la mission correspond à mon parcours et à mon projet professionnel." }, `etudiant:${etudiant}`);
  for (let i = 0; i < 60; i++) {
    const e = await s4("GET", `/soumissions/${s.id}`);
    if (e.statut !== "EN_TRAITEMENT") {
      if (e.statut !== attendu) throw new Error(`soumission ${s.id} : ${e.statut} au lieu de ${attendu}`);
      console.log(`  soumission ${s.id} (cas Bonita ${e.bonitaCaseId}) : ${e.statut}`);
      return e;
    }
    await attendre(1000);
  }
  throw new Error(`soumission ${s.id} toujours en traitement`);
}

function powershell(script, ...args) {
  execFileSync("powershell", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", join(RACINE, "scripts", script), ...args], { stdio: "ignore" });
}

console.log("Cas de démonstration");
const salma = await deposer(1, 1, "ENREGISTREE");
await s4("POST", `/candidatures/${salma.candidatureId}/evaluation`, null, "entreprise:1");
await s4("POST", `/candidatures/${salma.candidatureId}/decision`, { decision: "ACCEPTER", commentaire: "Profil en adéquation avec la mission." }, "entreprise:1");
for (let i = 0; i < 60 && (await s4("GET", `/candidatures/${salma.candidatureId}`)).statut !== "ACCEPTEE"; i++) await attendre(1000);
console.log("  candidature de Salma acceptée");
await deposer(4, 4, "ENREGISTREE");
powershell("arreter-service.ps1", "S3");
try {
  await deposer(20, 10, "ERREUR");
} finally {
  powershell("start-all.ps1");
}
await attendre(3000);

console.log("Captures du portail Bonita");
const nav = await chromium.launch({ channel: process.env.NAVIGATEUR_CANAL || "msedge" });
const ctx = await nav.newContext({ viewport: { width: 1360, height: 760 }, locale: "fr-FR" });
await ctx.addCookies([{ name: "BOS_Locale", value: "fr", url: B }]);
const page = await ctx.newPage();
const r = await page.request.post(`${B}/loginservice`, { form: { username: "walter.bates", password: "bpm", redirect: "false" } });
if (!r.ok()) throw new Error(`connexion à Bonita : ${r.status()}`);

async function capturer(nom, chemin, avant) {
  await page.goto(`${B}/apps/adminAppBonita/${chemin}/`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(2500);
  if (avant) { await avant(); await page.waitForTimeout(2500); }
  await page.screenshot({ path: join(SORTIE, `bonita-${nom}.png`) });
  console.log(`  bonita-${nom}.png`);
}
const cadre = () => page.frames().find(f => f !== page.mainFrame()) ?? page.mainFrame();

await capturer("processus", "admin-process-list");
await capturer("cas-ouverts", "admin-case-list");
await capturer("taches", "admin-task-list");
await capturer("tache-decision", "admin-task-list", async () => { await cadre().getByText("Tâche en attente", { exact: true }).click(); });
await capturer("cas-archives", "admin-case-list", async () => { await cadre().getByText("Cas archivés", { exact: true }).click(); });
await nav.close();
console.log("Terminé.");
