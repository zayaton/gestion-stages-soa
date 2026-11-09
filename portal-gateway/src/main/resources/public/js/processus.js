// Vue BPMN en direct : le diagramme du processus (le même fichier que dans Bonita) avec les étapes déjà passées en couleur.
// Les étapes viennent du journal de S4 (mode direct comme mode bonita) ; leurs noms sont les identifiants des nœuds du diagramme.
import { h, vider } from "./ui.js";

/** Nœuds du diagramme à marquer pour chaque étape du journal. */
function noeudsDeLEtape(e, decision) {
  const ok = e.resultat === "OK" || e.resultat === "INFO";
  switch (e.etape) {
    case "RECEPTION": return [["RECEPTION", "faite"]];
    case "VERIFICATION_ETUDIANT": return ok ? [["VERIFICATION_ETUDIANT", "faite"], ["G_ETUDIANT", "faite"]] : [["VERIFICATION_ETUDIANT", "rejet"], ["G_ETUDIANT", "faite"]];
    case "VERIFICATION_OFFRE": return ok
      ? [["VERIFICATION_OFFRE", "faite"], ["G_OFFRE", "faite"], ["VERIFICATION_ENTREPRISE", "faite"]]
      : [["VERIFICATION_OFFRE", "rejet"], ["G_OFFRE", "faite"]];
    case "CONTROLE_CANDIDATURE": return [["CONTROLE_CANDIDATURE", ok ? "faite" : "rejet"]];
    case "PASSERELLE_DECISION": return ok ? [["PASSERELLE_DECISION", "faite"]] : [["PASSERELLE_DECISION", "rejet"], ["ENREGISTREMENT_REJET", "faite"]];
    case "NOTIFICATION_REJET": return [["NOTIFICATION_REJET", "faite"], ["FIN_REJET", "rejet"]];
    case "ENREGISTREMENT": return [["ENREGISTREMENT", "faite"]];
    case "NOTIFICATION_DEPOT": return [["NOTIFICATION_DEPOT", "faite"]];
    case "TRANSMISSION_ENTREPRISE": return [["TRANSMISSION_ENTREPRISE", "faite"]];
    case "DECISION_ENTREPRISE": return [["DECISION_ENTREPRISE", "faite"], ["G_DECISION", "faite"]];
    case "MISE_A_JOUR_PLACES": return [["MISE_A_JOUR_PLACES", "faite"], ["ENREGISTREMENT_ACCEPTATION", "faite"]];
    case "NOTIFICATION_RESULTAT":
      if (decision === "REFUSER") return [["ENREGISTREMENT_REFUS", "faite"], ["NOTIFICATION_REFUS", "faite"], ["FIN_REFUSEE", "faite"]];
      if (decision === "DEMANDER_COMPLEMENT") return [["DEMANDE_COMPLEMENT", "faite"]];
      return [["NOTIFICATION_RESULTAT", "faite"]];
    case "CREATION_STAGE": return [["CREATION_STAGE", ok ? "faite" : "rejet"]];
    case "MISE_A_JOUR_ETUDIANT": return ok ? [["MISE_A_JOUR_ETUDIANT", "faite"], ["FIN_ACCEPTEE", "faite"]] : [["MISE_A_JOUR_ETUDIANT", "rejet"]];
    case "ERREUR": return [["SP_ERREUR", "rejet"], ["ERREUR_TECHNIQUE", "rejet"], ["SIGNALER_ERREUR", "faite"], ["ALERTE_RESPONSABLE", "faite"], ["FIN_ERREUR", "rejet"]];
    default: return [];
  }
}

/** Décision lisible dans le détail de l'étape DECISION_ENTREPRISE (mêmes mots en mode direct et en mode bonita). */
function decisionDe(detail = "") {
  if (/ACCEPTER|Acceptée/i.test(detail)) return "ACCEPTER";
  if (/REFUSER|Refusée/i.test(detail)) return "REFUSER";
  if (/COMPLEMENT|Complément/i.test(detail)) return "DEMANDER_COMPLEMENT";
  return null;
}

export function marquage(etapes, statutCandidature) {
  const marques = new Map();
  let decision = null;
  // Repli sur le statut final si le détail de la décision n'est pas explicite.
  const decisionFinale = statutCandidature === "REFUSEE" ? "REFUSER" : statutCandidature === "ACCEPTEE" ? "ACCEPTER" : null;
  for (const e of etapes) {
    if (e.etape === "DECISION_ENTREPRISE") decision = decisionDe(e.detail) ?? decisionFinale;
    for (const [id, classe] of noeudsDeLEtape(e, decision)) {
      // Une étape refaite (boucle R11) reste « faite » ; un rejet n'est jamais effacé par une étape suivante.
      if (marques.get(id) !== "rejet") marques.set(id, classe);
    }
  }
  // Étape en attente : la décision de l'entreprise, tant que la candidature est déposée ou en évaluation.
  const enAttente = ["DEPOSEE", "EN_COURS_EVALUATION"].includes(statutCandidature);
  const derniere = etapes[etapes.length - 1];
  if (enAttente && derniere && derniere.etape !== "ERREUR") marques.set("DECISION_ENTREPRISE", "attente");
  return marques;
}

let chargementDiagramme = null;
const diagramme = () => (chargementDiagramme ??= fetch("/bpmn/processus-candidature.bpmn").then(r => {
  if (!r.ok) throw new Error("Diagramme du processus introuvable.");
  return r.text();
}));

/** Affiche le diagramme dans « conteneur », les étapes passées en couleur. */
export async function afficherProcessus(conteneur, etapes, statutCandidature) {
  const zone = h("div", { class: "processus", role: "img", "aria-label": "Diagramme BPMN du processus de candidature, étapes passées en couleur" });
  const legende = h("div", { class: "legende" },
    h("span", { class: "faite" }, "Étape réalisée"), h("span", { class: "rejet" }, "Rejet ou erreur"), h("span", { class: "attente" }, "En attente"),
    h("small", {}, "Molette + Ctrl pour zoomer, glisser pour déplacer"));
  vider(conteneur, zone, legende);
  if (!window.BpmnJS) {
    vider(zone, h("p", { class: "discret", style: "padding:16px" }, "Le module d'affichage BPMN n'a pas pu être chargé."));
    return;
  }
  try {
    const visionneuse = new window.BpmnJS({ container: zone });
    await visionneuse.importXML(await diagramme());
    const canvas = visionneuse.get("canvas");
    const registre = visionneuse.get("elementRegistry");
    const marques = marquage(etapes, statutCandidature);
    for (const [id, classe] of marques) {
      if (registre.get(id)) canvas.addMarker(id, `etape-${classe}`);
    }
    // Flèches entre deux nœuds passés.
    registre.filter(el => el.type === "bpmn:SequenceFlow").forEach(f => {
      const de = marques.get(f.businessObject.sourceRef.id);
      const vers = marques.get(f.businessObject.targetRef.id);
      if (de && de !== "attente" && vers) canvas.addMarker(f.id, "etape-faite");
    });
    // Hauteur ajustée aux proportions du diagramme (large, en couloirs) pour qu'il occupe toute la largeur.
    const boite = canvas.viewbox().inner;
    const largeur = zone.clientWidth || 1000;
    zone.style.height = `${Math.round(Math.min(560, Math.max(260, (largeur * boite.height) / boite.width + 24)))}px`;
    canvas.resized();
    canvas.zoom("fit-viewport");
  } catch (err) {
    vider(zone, h("p", { class: "discret", style: "padding:16px" }, `Diagramme indisponible : ${err.message}`));
  }
}

/** Libellés des étapes du journal, pour la liste textuelle qui accompagne le diagramme. */
export const NOMS_ETAPES = {
  RECEPTION: "Réception de la candidature", VERIFICATION_ETUDIANT: "Vérification de l'étudiant", VERIFICATION_OFFRE: "Vérification de l'offre et de l'entreprise",
  CONTROLE_CANDIDATURE: "Contrôle de la candidature", PASSERELLE_DECISION: "Passerelle de décision", NOTIFICATION_REJET: "Notification du rejet",
  ENREGISTREMENT: "Enregistrement de la candidature", NOTIFICATION_DEPOT: "Confirmation du dépôt", TRANSMISSION_ENTREPRISE: "Transmission à l'entreprise",
  DEBUT_EVALUATION: "Début de l'évaluation", DECISION_ENTREPRISE: "Décision de l'entreprise", NOTIFICATION_RESULTAT: "Notification du résultat",
  MISE_A_JOUR_PLACES: "Réservation de la place", CREATION_STAGE: "Création du dossier de stage", MISE_A_JOUR_ETUDIANT: "Mise à jour du statut de l'étudiant",
  ANNULATION: "Annulation", ERREUR: "Interruption du traitement",
};
