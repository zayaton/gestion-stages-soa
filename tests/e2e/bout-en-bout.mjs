// Test de bout en bout, par le portail uniquement (comme le ferait le navigateur), sur des données fraîches.
// Lancé par scripts\test-bout-en-bout.ps1, qui réinitialise les bases, puis arrête S3 pour le mode « panne ».
// Usage : node bout-en-bout.mjs scenarios | panne
// Variable ORCHESTRATION : « direct » (résultats immédiats) ou « bonita » (le processus avance en arrière-plan :
// le script attend chaque résultat). Les mêmes vérifications valent pour les deux modes.
import { readFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const PORTAIL = process.env.PORTAIL_URL || "http://localhost:8090";
const MAILPIT = process.env.MAILPIT_URL || "http://localhost:8025/api/v1";
const LOGS = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "logs");
const MDP = { ETUDIANT: "Etudiant@2026", ENTREPRISE: "Entreprise@2026", RESPONSABLE: "Responsable@2026" };
const MODE = process.env.ORCHESTRATION || "direct";
const ASYNCHRONE = MODE !== "direct";
const LETTRE = "Je souhaite rejoindre votre équipe : ce stage correspond exactement à mon projet professionnel.";

let reussis = 0;
const echecs = [];

function verifier(libelle, condition, detail = "") {
  if (condition) {
    reussis++;
    console.log(`  OK     ${libelle}`);
  } else {
    echecs.push(libelle);
    console.log(`  ÉCHEC  ${libelle}${detail ? " : " + detail : ""}`);
  }
}

function section(titre) {
  console.log(`\n${titre}`);
}

async function appel(methode, chemin, { jeton, corps, correlation } = {}) {
  const entetes = { "Content-Type": "application/json" };
  if (jeton) entetes.Authorization = `Bearer ${jeton}`;
  if (correlation) entetes["X-Correlation-Id"] = correlation;
  const r = await fetch(PORTAIL + chemin, { method: methode, headers: entetes, body: corps === undefined ? undefined : JSON.stringify(corps),
    signal: AbortSignal.timeout(20000) });
  const texte = await r.text();
  let json = null;
  try { json = texte ? JSON.parse(texte) : null; } catch { json = null; }
  return { statut: r.status, json };
}

async function connecter(identifiant, role) {
  const r = await appel("POST", "/api/auth/connexion", { corps: { identifiant, motDePasse: MDP[role] } });
  if (r.statut !== 200) throw new Error(`Connexion impossible pour ${identifiant} : ${r.statut} ${JSON.stringify(r.json)}`);
  return r.json.jeton;
}

const pause = ms => new Promise(r => setTimeout(r, ms));

/** Répète « lire » jusqu'à ce que « fini » soit vrai (mode bonita : le processus avance en arrière-plan). */
async function attendre(lire, fini, delaiMs = 20000) {
  const limite = Date.now() + delaiMs;
  let valeur = await lire();
  while (!fini(valeur) && Date.now() < limite) {
    await pause(400);
    valeur = await lire();
  }
  return valeur;
}

/** Sujets reçus par une adresse ; si « attendu » est donné, attend qu'il arrive (le processus peut être en cours). */
async function sujetsRecus(adresse, attendu) {
  const lire = async () => {
    const r = await fetch(`${MAILPIT}/search?query=${encodeURIComponent("to:" + adresse)}`, { signal: AbortSignal.timeout(10000) });
    return ((await r.json()).messages || []).map(m => m.Subject);
  };
  return attendu ? attendre(lire, sujets => sujets.some(attendu)) : lire();
}

/** Dépôt par l'étudiant connecté. En mode bonita, attend la fin des étapes 2 à 6 et renvoie la soumission finale. */
async function candidater(jeton, offreId, correlation) {
  const r = await appel("POST", "/api/etudiant/candidatures", { jeton, corps: { offreId, lettreMotivation: LETTRE }, correlation });
  if (!ASYNCHRONE || r.statut !== 202 || r.json?.statut !== "EN_TRAITEMENT") return r;
  const id = r.json.id;
  const final = await attendre(() => appel("GET", "/api/etudiant/candidatures", { jeton }), l => {
    const c = l.json?.candidatures?.find(x => x.soumissionId === id);
    const s = l.json?.soumissionsNonEnregistrees?.find(x => x.id === id);
    return c || (s && s.statut !== "EN_TRAITEMENT");
  });
  const c = final.json?.candidatures?.find(x => x.soumissionId === id);
  const s = final.json?.soumissionsNonEnregistrees?.find(x => x.id === id);
  return { statut: r.statut, json: c ? { ...r.json, statut: "ENREGISTREE", candidatureId: c.id } : (s ?? r.json) };
}

/**
 * Décision de l'entreprise. En mode bonita, la réponse arrive avant la fin des étapes 8 et 9 : on attend le statut final,
 * et l'on relit les étapes exécutées par le processus depuis la décision.
 */
async function decider(jeton, id, corps, statutAttendu) {
  let r = await appel("POST", `/api/entreprise/candidatures/${id}/decision`, { jeton, corps });
  if (!ASYNCHRONE) return r;
  // Après une demande de complément, la tâche de décision réapparaît dès que la notification est partie.
  for (let i = 0; i < 25 && r.statut === 409 && r.json?.code === "DECISION_EN_COURS"; i++) {
    await pause(400);
    r = await appel("POST", `/api/entreprise/candidatures/${id}/decision`, { jeton, corps });
  }
  if (r.statut !== 200) return r;
  const derniere = corps.decision === "ACCEPTER" ? "MISE_A_JOUR_ETUDIANT" : "NOTIFICATION_RESULTAT";
  const detail = await attendre(() => appel("GET", `/api/entreprise/candidatures/${id}`, { jeton }), d => {
    const e = d.json?.etapes ?? [];
    const depuis = e.slice(e.map(x => x.etape).lastIndexOf("DECISION_ENTREPRISE"));
    return d.json?.statut === statutAttendu && depuis.some(x => x.etape === derniere);
  });
  const e = detail.json?.etapes ?? [];
  return { statut: r.statut, json: { candidature: { ...detail.json }, etapes: e.slice(e.map(x => x.etape).lastIndexOf("DECISION_ENTREPRISE")) } };
}

// ---------------------------------------------------------------------------------------------------------------

async function scenarios() {
  section("1. Connexion et rôles");
  const refus = await appel("POST", "/api/auth/connexion", { corps: { identifiant: "salma.elidrissi@inpt.ac.ma", motDePasse: "faux" } });
  verifier("Mauvais mot de passe : 401", refus.statut === 401 && refus.json?.code === "NON_AUTHENTIFIE");
  const salma = await connecter("salma.elidrissi@inpt.ac.ma", "ETUDIANT");
  const novapay = await connecter("contact@novapay-maghreb.ma", "ENTREPRISE");
  const zellige = await connecter("contact@zellige-conseil.ma", "ENTREPRISE");
  const responsable = await connecter("responsable@inpt.ac.ma", "RESPONSABLE");
  const moi = await appel("GET", "/api/auth/moi", { jeton: salma });
  verifier("Identité de l'étudiante (nom venant de S1)", moi.json?.nom === "Salma El Idrissi" && moi.json?.role === "ETUDIANT");
  verifier("Sans jeton : 401", (await appel("GET", "/api/etudiant/profil")).statut === 401);
  verifier("Étudiante dans l'espace responsable : 403", (await appel("GET", "/api/responsable/tableau-de-bord", { jeton: salma })).statut === 403);
  verifier("Entreprise dans l'espace étudiant : 403", (await appel("GET", "/api/etudiant/offres", { jeton: novapay })).statut === 403);

  section("2. Parcours nominal : Salma candidate, NovaPay Maghreb accepte (étapes 1 à 9)");
  const offres = await appel("GET", "/api/etudiant/offres", { jeton: salma });
  const offre1 = offres.json?.find(o => o.id === 1);
  verifier("Offre 1 visible, avec entreprise et indicateur de compatibilité", offre1?.entreprise === "NovaPay Maghreb" && offre1?.compatibilite?.compatible === true,
    JSON.stringify(offre1?.compatibilite));
  verifier("Les offres non ouvertes ne sont pas proposées (brouillon, date passée, complète)", ![11, 13, 14].some(id => offres.json?.some(o => o.id === id)));
  const depot = await candidater(salma, 1, "e2e-parcours-nominal");
  verifier("Dépôt : 202, candidature enregistrée", depot.statut === 202 && depot.json?.statut === "ENREGISTREE", JSON.stringify(depot.json));
  const idSalma = depot.json?.candidatureId;
  const mes = await appel("GET", "/api/etudiant/candidatures", { jeton: salma });
  verifier("Dans « mes candidatures » : DEPOSEE, avec le titre de l'offre",
    mes.json?.candidatures?.some(c => c.id === idSalma && c.statut === "DEPOSEE" && c.titreOffre === "Stage AMOA – Paiement mobile"));

  const recues = await appel("GET", "/api/entreprise/candidatures", { jeton: novapay });
  const vue = recues.json?.find(c => c.id === idSalma);
  verifier("L'entreprise voit la candidature", !!vue);
  verifier("Courriel de l'étudiante masqué pour l'entreprise", vue?.etudiant?.email === "s***@inpt.ac.ma", vue?.etudiant?.email);
  verifier("Téléphone de l'étudiante masqué pour l'entreprise", /^\d{2}\*{6}\d{2}$/.test(vue?.etudiant?.telephone ?? ""), vue?.etudiant?.telephone);
  verifier("Le JSON reçu par l'entreprise ne contient aucune coordonnée complète", !JSON.stringify(recues.json).includes("salma.elidrissi@"));
  verifier("Une autre entreprise ne voit pas ce dossier : 404", (await appel("GET", `/api/entreprise/candidatures/${idSalma}`, { jeton: zellige })).statut === 404);
  verifier("L'étudiante ne voit pas le dossier d'un autre : 404", (await appel("GET", "/api/etudiant/candidatures/2", { jeton: salma })).statut === 404);

  verifier("L'entreprise démarre l'évaluation", (await appel("POST", `/api/entreprise/candidatures/${idSalma}/evaluation`, { jeton: novapay })).json?.statut === "EN_COURS_EVALUATION");
  const annul = await appel("DELETE", `/api/etudiant/candidatures/${idSalma}`, { jeton: salma });
  verifier("R8 : annulation refusée une fois l'évaluation commencée (409)", annul.statut === 409 && annul.json?.code === "ANNULATION_IMPOSSIBLE"
    && annul.json?.message.includes("R8"), JSON.stringify(annul.json));
  const decision = await decider(novapay, idSalma, { decision: "ACCEPTER", commentaire: "Profil retenu, bienvenue." }, "ACCEPTEE");
  verifier("Acceptation : candidature ACCEPTEE, toutes les étapes de suivi réussies (R10)", decision.json?.candidature?.statut === "ACCEPTEE"
    && decision.json?.etapes?.every(e => e.resultat === "OK"), JSON.stringify(decision.json?.etapes));
  const stages = await appel("GET", "/api/etudiant/stages", { jeton: salma });
  verifier("R10 : le stage apparaît dans l'espace de l'étudiante", stages.json?.length === 1 && stages.json[0].statut === "PLANIFIE"
    && stages.json[0].titreOffre === "Stage AMOA – Paiement mobile");
  verifier("R10 : l'étudiante est désormais EN_STAGE (S1)", (await appel("GET", "/api/etudiant/profil", { jeton: salma })).json?.statut === "EN_STAGE");
  const detail = await appel("GET", `/api/etudiant/candidatures/${idSalma}`, { jeton: salma });
  verifier("Historique et étapes du processus visibles par l'étudiante", detail.json?.historique?.length === 3 && detail.json?.etapes?.length >= 13);
  const dossier = await appel("GET", `/api/responsable/candidatures/${idSalma}`, { jeton: responsable });
  verifier("Le responsable voit les coordonnées complètes et le stage", dossier.json?.etudiant?.email === "salma.elidrissi@inpt.ac.ma" && dossier.json?.stage?.id > 0);
  const salmaSujets = await sujetsRecus("salma.elidrissi@inpt.ac.ma", s => s.startsWith("Candidature retenue"));
  verifier("Mailpit : confirmation de dépôt reçue", salmaSujets.includes("Candidature enregistrée : Stage AMOA – Paiement mobile"), salmaSujets.join(" | "));
  verifier("Mailpit : acceptation reçue", salmaSujets.includes("Candidature retenue : Stage AMOA – Paiement mobile"));
  verifier("Mailpit : l'entreprise a été informée", (await sujetsRecus("contact@novapay-maghreb.ma")).includes("Nouvelle candidature : Stage AMOA – Paiement mobile"));
  const contient = id => readdirSync(LOGS).filter(f => f.endsWith(".log") && readFileSync(join(LOGS, f), "utf8").includes(id));
  if (!ASYNCHRONE) {
    const avecCorrelation = contient("e2e-parcours-nominal");
    verifier("Corrélation : le même identifiant dans les journaux du portail, de S1 à S5",
      ["portal-gateway", "rest-candidatures", "soap-etudiants", "soap-entreprises", "soap-offres", "rest-notifications"].every(m => avecCorrelation.includes(m + ".log")),
      avecCorrelation.join(", "));
  } else {
    const portail = contient("e2e-parcours-nominal");
    const processus = contient(`soumission-${depot.json?.id}`);
    verifier("Corrélation : identifiant du portail jusqu'à S4, puis celui du cas Bonita de S1 à S6",
      ["portal-gateway", "rest-candidatures"].every(m => portail.includes(m + ".log"))
      && ["soap-etudiants", "soap-entreprises", "soap-offres", "rest-notifications", "rest-suivi"].every(m => processus.includes(m + ".log")),
      `portail : ${portail.join(", ")} | processus : ${processus.join(", ")}`);
  }

  section("3. Règles R1 à R7 : chaque étudiant depuis son propre compte");
  const cas = [
    ["R1", "sara.bouzidi@inpt.ac.ma", 1, "suspendu"],
    ["R1", "ilyas.zemmouri@inpt.ac.ma", 1, "déjà en stage"],
    ["R2", "aicha.tahiri@inpt.ac.ma", 11, "BROUILLON"],
    ["R2", "aicha.tahiri@inpt.ac.ma", 12, "Médina Systèmes"],
    ["R3", "aicha.tahiri@inpt.ac.ma", 13, "date limite"],
    ["R4", "aicha.tahiri@inpt.ac.ma", 14, "plus de place"],
    ["R5", "youssef.benjelloun@inpt.ac.ma", 2, "déjà une candidature"],
    ["R6", "imane.alaoui@inpt.ac.ma", 1, "5 candidatures actives"],
    ["R7", "nour.chraibi@inpt.ac.ma", 1, "niveau 2A"],
    ["R7", "anas.ouazzani@inpt.ac.ma", 1, "ICCN"],
  ];
  for (const [regle, compte, offre, extrait] of cas) {
    const jeton = await connecter(compte, "ETUDIANT");
    const r = await candidater(jeton, offre);
    verifier(`${regle} : ${compte.split("@")[0]}, offre ${offre} rejetée (« ${extrait} »)`,
      r.statut === 202 && r.json?.statut === "REJETEE" && r.json?.regleRejetee === regle && r.json?.motifRejet?.includes(extrait), JSON.stringify(r.json));
    const sujets = await sujetsRecus(compte, s => s.startsWith("Candidature non recevable"));
    verifier(`${regle} : courriel de rejet reçu par ${compte.split("@")[0]}`, sujets.some(s => s.startsWith("Candidature non recevable")));
  }
  const aicha = await connecter("aicha.tahiri@inpt.ac.ma", "ETUDIANT");
  const sesRejets = await appel("GET", "/api/etudiant/candidatures", { jeton: aicha });
  verifier("Les rejets restent consultables, avec la règle et le titre de l'offre",
    sesRejets.json?.soumissionsNonEnregistrees?.length === 4 && sesRejets.json.soumissionsNonEnregistrees.every(s => s.regleRejetee && s.titreOffre));

  section("4. R8 : annulation par l'étudiant");
  const youssef = await connecter("youssef.benjelloun@inpt.ac.ma", "ETUDIANT");
  verifier("Youssef annule sa candidature déposée : 204", (await appel("DELETE", "/api/etudiant/candidatures/1", { jeton: youssef })).statut === 204);
  verifier("Elle apparaît ANNULEE", (await appel("GET", "/api/etudiant/candidatures/1", { jeton: youssef })).json?.statut === "ANNULEE");

  section("5. R11 : demande de complément, puis décision");
  const mehdi = await connecter("mehdi.tazi@inpt.ac.ma", "ETUDIANT");
  const detroit = await connecter("contact@detroit-data.ma", "ENTREPRISE");
  const dm = await candidater(mehdi, 4);
  const idMehdi = dm.json?.candidatureId;
  verifier("Mehdi candidate à l'offre 4", dm.json?.statut === "ENREGISTREE");
  await appel("POST", `/api/entreprise/candidatures/${idMehdi}/evaluation`, { jeton: detroit });
  const complement = await decider(detroit, idMehdi,
    { decision: "DEMANDER_COMPLEMENT", commentaire: "Pouvez-vous préciser vos disponibilités à partir de février ?" }, "EN_COURS_EVALUATION");
  verifier("R11 : la candidature reste EN_COURS_EVALUATION", complement.json?.candidature?.statut === "EN_COURS_EVALUATION");
  verifier("R11 : l'étudiant reçoit la demande", (await sujetsRecus("mehdi.tazi@inpt.ac.ma", s => s.startsWith("Complément"))).includes("Complément d'information demandé : Ingénieur données – Flux logistiques"));
  const refus2 = await decider(detroit, idMehdi, { decision: "REFUSER", commentaire: "Calendrier incompatible." }, "REFUSEE");
  verifier("La décision revient à l'entreprise : refus enregistré", refus2.json?.candidature?.statut === "REFUSEE");
  verifier("L'étudiant reçoit la réponse", (await sujetsRecus("mehdi.tazi@inpt.ac.ma", s => s.startsWith("Réponse"))).some(s => s.startsWith("Réponse à votre candidature")));

  section("6. Espace responsable");
  const tdb = await appel("GET", "/api/responsable/tableau-de-bord", { jeton: responsable });
  verifier("Tableau de bord : indicateurs calculés", tdb.json?.candidaturesParStatut?.ACCEPTEE >= 4 && tdb.json?.tauxAcceptation > 0 && tdb.json?.stagesEnCours >= 1,
    JSON.stringify(tdb.json));
  const etat = await appel("GET", "/api/responsable/services", { jeton: responsable });
  verifier("État du système : les six services disponibles", etat.json?.length === 6 && etat.json.every(s => s.etat === "DISPONIBLE"));
  const suppr = await appel("DELETE", "/api/responsable/etudiants/3", { jeton: responsable });
  verifier("Supprimer un étudiant qui a des candidatures : 409", suppr.statut === 409 && suppr.json?.code === "ETUDIANT_AVEC_CANDIDATURES");
  const rappels = await appel("POST", "/api/responsable/rappels", { jeton: responsable });
  verifier("Rappels de date limite déclenchés", rappels.statut === 200 && rappels.json?.rappelsEnvoyes >= 1, JSON.stringify(rappels.json));
  verifier("Mailpit : un étudiant compatible a reçu un rappel", (await sujetsRecus("omar.bennani@inpt.ac.ma")).some(s => s.startsWith("Plus que quelques jours")));
}

async function panne() {
  section("7. Service indisponible : S3 (offres) est arrêté");
  const omar = await connecter("omar.bennani@inpt.ac.ma", "ETUDIANT");
  const r = await candidater(omar, 4);
  if (!ASYNCHRONE) {
    verifier("Le dépôt répond 503 avec un message clair", r.statut === 503 && r.json?.code === "SERVICE_OFFRES_INDISPONIBLE"
      && r.json?.message.includes("responsable"), JSON.stringify(r.json));
  } else {
    verifier("Le processus s'interrompt dans son sous-processus d'erreur : soumission en ERREUR", r.statut === 202 && r.json?.statut === "ERREUR",
      JSON.stringify(r.json));
  }
  verifier("La liste des offres répond 503 (pas d'erreur 500)", (await appel("GET", "/api/etudiant/offres", { jeton: omar })).statut === 503);
  const responsable = await connecter("responsable@inpt.ac.ma", "RESPONSABLE");
  const etat = await appel("GET", "/api/responsable/services", { jeton: responsable });
  verifier("L'état du système signale S3 indisponible", etat.json?.find(s => s.service === "S3 Offres")?.etat === "INDISPONIBLE");
  verifier("Le responsable a reçu l'alerte du processus", (await sujetsRecus("responsable@inpt.ac.ma", s => s.startsWith("Incident"))).some(s => s.startsWith("Incident de traitement")));
  const sesDepots = await appel("GET", "/api/etudiant/candidatures", { jeton: omar });
  verifier("Ses candidatures restent consultables malgré la panne, le dépôt interrompu en ERREUR", sesDepots.statut === 200
    && sesDepots.json?.soumissionsNonEnregistrees?.some(s => s.statut === "ERREUR" && s.detailsIndisponibles === true), JSON.stringify(sesDepots.json));
}

// ---------------------------------------------------------------------------------------------------------------

const mode = process.argv[2] || "scenarios";
try {
  if (mode === "panne") await panne(); else await scenarios();
} catch (e) {
  echecs.push("Exécution interrompue : " + e.message);
  console.log(`  ÉCHEC  Exécution interrompue : ${e.message}`);
}
console.log(`\nRésultat (${mode}, orchestration ${MODE}) : ${reussis} vérification(s) réussie(s), ${echecs.length} échec(s).`);
process.exit(echecs.length === 0 ? 0 : 1);
