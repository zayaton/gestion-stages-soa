// Éléments communs aux trois espaces pour le dossier d'une candidature : historique des statuts,
// étapes du processus (liste textuelle) et vue BPMN en direct.
import { afficherProcessus, NOMS_ETAPES } from "./processus.js";
import { acteur, chronologie, h, libelle, traduireCodes } from "./ui.js";

/** Historique des changements de statut (S4) en chronologie. */
export function historiqueStatuts(historique) {
  return chronologie((historique || []).map(x => ({
    quand: x.date,
    quoi: x.ancienStatut === null || x.ancienStatut === undefined ? `Dépôt : ${libelle(x.nouveauStatut)}`
      : x.ancienStatut === x.nouveauStatut ? `${libelle(x.nouveauStatut)} (statut inchangé)` : `${libelle(x.ancienStatut)} → ${libelle(x.nouveauStatut)}`,
    detail: x.commentaire,
    qui: acteur(x.acteur),
    etat: x.nouveauStatut === "ACCEPTEE" ? "ok" : x.nouveauStatut === "REFUSEE" ? "ko" : x.nouveauStatut === "EN_COURS_EVALUATION" ? "attente" : "",
  })));
}

/** Étapes du processus : équivalent textuel du diagramme. */
export function listeEtapes(etapes) {
  return chronologie((etapes || []).map(e => ({
    quand: e.date,
    quoi: NOMS_ETAPES[e.etape] ?? e.etape,
    detail: traduireCodes(e.detail),
    qui: acteur(e.acteur),
    etat: e.resultat === "OK" ? "ok" : e.resultat === "REJET" || e.resultat === "ECHEC" ? "ko" : "",
  })));
}

/** Panneau « Suivi du processus » : diagramme BPMN coloré, sur toute la largeur (le diagramme est large). */
export function panneauProcessus(etapes, statut) {
  const zone = h("div");
  const panneau = h("section", { class: "panneau" },
    h("div", { class: "panneau-titre" }, h("h2", {}, "Suivi du processus"), h("span", { class: "discret" }, "Diagramme BPMN du traitement de la candidature")),
    zone);
  afficherProcessus(zone, etapes || [], statut);
  return panneau;
}

/** Panneau « Étapes du processus » : équivalent textuel du diagramme. */
export function panneauEtapes(etapes) {
  return h("section", { class: "panneau" },
    h("div", { class: "panneau-titre" }, h("h2", {}, "Étapes du processus"), h("span", { class: "discret" }, `${(etapes || []).length} étape(s)`)),
    listeEtapes(etapes));
}

/** Lettre de motivation, texte brut (retours à la ligne conservés). */
export function lettre(texte) {
  return h("div", { style: "white-space:pre-wrap" }, texte || "–");
}
