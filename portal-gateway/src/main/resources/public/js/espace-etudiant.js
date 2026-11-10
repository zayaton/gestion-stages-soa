// Espace étudiant : offres et compatibilité, candidature, suivi des candidatures, notifications, stage, profil.
import { envoyer, lire, supprimer } from "./api.js";
import { historiqueStatuts, lettre, panneauEtapes, panneauProcessus } from "./dossier.js";
import {
  acteur, action, badge, champ, chargement, chronologie, confirmer, dateCourte, dateFr, dateHeure, delai, entetePage, FILIERES, h,
  indicateurCompatibilite, message, nombre, note, select, tableau, toast, traduireCodes, TYPES_NOTIFICATION, valider, vide, vider,
} from "./ui.js";

export const navigation = [
  ["offres", "Offres de stage"], ["candidatures", "Mes candidatures"], ["notifications", "Notifications"], ["stage", "Mon stage"], ["profil", "Mon profil"],
];

export async function afficher(route, contenu) {
  vider(contenu, chargement());
  if (route.page === "offres" && route.id) return offre(Number(route.id), contenu);
  if (route.page === "offres") return offres(contenu);
  if (route.page === "candidatures" && route.id) return candidature(Number(route.id), contenu);
  if (route.page === "candidatures") return candidatures(contenu);
  if (route.page === "notifications") return notifications(contenu);
  if (route.page === "stage") return stages(contenu);
  if (route.page === "profil") return profil(contenu);
  location.replace("#/etudiant/offres");
}

// ---------------------------------------------------------------------------------------------------------------
// Offres

async function offres(contenu) {
  document.title = "Offres de stage · INPT";
  const recherche = h("input", { type: "search", placeholder: "Titre, mission…" });
  const filiere = select([["", "Toutes"], ...FILIERES.map(f => [f, f])], "");
  const compatibles = h("input", { type: "checkbox" });
  const resultat = h("div", {}, chargement());
  let toutes = [];

  const dessiner = () => {
    const liste = compatibles.checked ? toutes.filter(o => o.compatibilite?.compatible) : toutes;
    if (!liste.length) {
      vider(resultat, vide("Aucune offre ne correspond", "Modifiez la recherche ou retirez des filtres."));
      return;
    }
    vider(resultat, h("p", { class: "compte" }, h("strong", {}, String(liste.length)), ` offre${liste.length > 1 ? "s" : ""} ouverte${liste.length > 1 ? "s" : ""}`),
      h("ul", { class: "offres", "aria-label": "Offres de stage ouvertes" }, liste.map(carteOffre)));
  };

  const charger = async () => {
    vider(resultat, chargement("Recherche des offres et calcul de la compatibilité…"));
    const params = new URLSearchParams();
    if (recherche.value.trim()) params.set("recherche", recherche.value.trim());
    if (filiere.value) params.set("filiere", filiere.value);
    try {
      toutes = await lire(`/etudiant/offres${params.size ? `?${params}` : ""}`);
      dessiner();
    } catch (err) {
      vider(resultat, message("ko", "Offres indisponibles", err.message));
    }
  };

  vider(contenu,
    entetePage("Offres de stage", "Offres ouvertes des entreprises partenaires : avant la date limite et avec au moins une place."),
    h("form", { class: "barre-filtres", onsubmit: ev => { ev.preventDefault(); charger(); } },
      h("div", { class: "champ large" }, h("label", {}, "Recherche", recherche)),
      h("div", { class: "champ" }, h("label", {}, "Filière acceptée", filiere)),
      h("label", { style: "display:flex;gap:8px;align-items:center;padding-bottom:8px" }, compatibles, "Compatibles avec mon profil"),
      h("button", { class: "bouton secondaire", type: "submit" }, "Rechercher")),
    resultat);
  compatibles.addEventListener("change", dessiner);
  await charger();
}

const TEINTES = ["bleu", "sarcelle", "safran", "terre", "nuit"];

/** Teinte stable d'une entreprise (même entreprise, même couleur sur toutes les cartes). */
function teinte(nom) {
  let somme = 0;
  for (const c of nom) somme = (somme * 31 + c.charCodeAt(0)) >>> 0;
  return TEINTES[somme % TEINTES.length];
}

/** Carte d'une offre : monogramme de l'entreprise, titre, repères (ville, durée, places), date limite, compatibilité. */
function carteOffre(o) {
  const d = delai(o.dateLimite);
  const sigle = o.entreprise.split(/\s+/).filter(m => m.length > 2).slice(0, 2).map(m => m[0].toUpperCase()).join("") || o.entreprise.slice(0, 2).toUpperCase();
  const c = o.compatibilite;
  return h("li", { class: `offre ${teinte(o.entreprise)}${c && !c.compatible ? " incompatible" : ""}` },
    h("div", { class: "offre-tete" },
      h("span", { class: "monogramme", "aria-hidden": "true" }, sigle),
      h("div", {},
        h("a", { class: "offre-titre", href: `#/etudiant/offres/${o.id}` }, o.titre),
        h("div", { class: "offre-entreprise" }, o.entreprise))),
    h("ul", { class: "reperes" },
      h("li", {}, o.ville), h("li", {}, `${o.dureeSemaines} semaines`), h("li", {}, `${nombre(o.nombrePlaces)} place${o.nombrePlaces > 1 ? "s" : ""}`)),
    h("div", { class: `echeance${d.proche ? " proche" : ""}` }, h("span", {}, `Jusqu'au ${dateCourte(o.dateLimite)}`), h("strong", {}, d.texte)),
    h("div", { class: "offre-pied" }, indicateurCompatibilite(c)));
}

async function offre(id, contenu) {
  const o = await lire(`/etudiant/offres/${id}`);
  document.title = `${o.titre} · INPT`;
  const zoneCandidature = h("div");
  vider(contenu,
    entetePage(o.titre, `${o.entreprise} · ${o.ville}`, h("a", { href: "#/etudiant/offres" }, "Offres de stage"), badge(o.statut)),
    h("div", { class: "grille-2" },
      h("section", { class: "panneau" },
        h("h2", {}, "La mission"), h("p", { style: "white-space:pre-wrap" }, o.description),
        h("dl", { class: "fiche" },
          h("dt", {}, "Compétences demandées"), h("dd", {}, o.competences.join(", ")),
          h("dt", {}, "Filières acceptées"), h("dd", {}, o.filieres.join(", ")),
          h("dt", {}, "Niveau minimum"), h("dd", {}, o.niveauMinimum),
          h("dt", {}, "Durée"), h("dd", {}, `${o.dureeSemaines} semaines`),
          h("dt", {}, "Places restantes"), h("dd", {}, nombre(o.nombrePlaces)),
          h("dt", {}, "Date limite"), h("dd", {}, `${dateFr(o.dateLimite)} (${delai(o.dateLimite).texte})`))),
      h("div", {},
        h("section", { class: "panneau" }, h("h2", {}, "Votre profil et cette offre"), indicateurCompatibilite(o.compatibilite),
          h("p", { class: "discret", style: "margin-top:10px" }, "Le score de compétences est indicatif : seules la filière, le niveau et la date limite conditionnent la recevabilité.")),
        zoneCandidature)));
  vider(zoneCandidature, formulaireCandidature(o));
}

function formulaireCandidature(o) {
  const texte = h("textarea", { name: "lettre", maxlength: "5000", rows: "9" });
  const compteur = h("span", { class: "discret chiffres" }, "0 / 5000");
  texte.addEventListener("input", () => { compteur.textContent = `${texte.value.trim().length} / 5000`; });
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, "Envoyer ma candidature");
  const avertissement = o.compatibilite && !o.compatibilite.compatible
    ? message("attente", "Candidature probablement non recevable", `D'après votre profil : ${o.compatibilite.motif} Vous pouvez tout de même candidater ; le dossier sera vérifié.`)
    : null;
  const formulaire = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider([[texte, v => (v.length < 30 ? "La lettre de motivation doit compter au moins 30 caractères." : null)]])) return;
    vider(retour, chargement("Vérification de votre dossier…"));
    try {
      const s = await action(bouton, () => envoyer("/etudiant/candidatures", { offreId: o.id, lettreMotivation: texte.value.trim() }));
      afficherResultat(retour, s);
      if (s.statut !== "REJETEE") formulaire.remove();
    } catch (err) {
      vider(retour, message("ko", "Le dépôt n'a pas pu être traité", err.message, err.details));
    }
  } },
  champ("Lettre de motivation", texte, "Entre 30 et 5000 caractères. Expliquez en quoi la mission correspond à votre parcours."),
  h("div", { class: "actions" }, bouton, compteur));
  return h("section", { class: "panneau" }, h("h2", {}, "Candidater"), avertissement, retour, formulaire);
}

/** Résultat du dépôt : enregistré, non recevable (règle et motif) ou en cours de vérification par le processus. */
function afficherResultat(zone, s) {
  if (s.statut === "ENREGISTREE") {
    vider(zone, message("ok", "Candidature enregistrée", "Votre dossier a été vérifié et transmis à l'entreprise. Un courriel de confirmation vous a été envoyé."),
      h("a", { class: "bouton secondaire", href: `#/etudiant/candidatures/${s.candidatureId}` }, "Suivre cette candidature"));
  } else if (s.statut === "REJETEE") {
    vider(zone, message("ko", `Candidature non recevable (règle ${s.regleRejetee})`, s.motifRejet));
  } else if (s.statut === "ERREUR") {
    vider(zone, message("ko", "Traitement interrompu", "Un service ne répondait pas. Le responsable des stages a été prévenu ; réessayez plus tard."));
  } else {
    vider(zone, message("attente", "Vérification en cours", "Le processus vérifie votre dossier. Cette page se met à jour dans quelques secondes."));
    suivreSoumission(zone, s.id);
  }
}

/** Mode bonita : le processus avance en arrière-plan ; la page interroge le portail jusqu'au résultat. */
async function suivreSoumission(zone, id) {
  for (let i = 0; i < 40; i++) {
    await new Promise(r => setTimeout(r, 1500));
    if (!zone.isConnected) return;
    try {
      const l = await lire("/etudiant/candidatures");
      const c = l.candidatures.find(x => x.soumissionId === id);
      const s = l.soumissionsNonEnregistrees.find(x => x.id === id);
      if (c) return afficherResultat(zone, { statut: "ENREGISTREE", candidatureId: c.id });
      if (s && s.statut !== "EN_TRAITEMENT") return afficherResultat(zone, s);
    } catch { /* nouvel essai au tour suivant */ }
  }
  vider(zone, message("attente", "Vérification toujours en cours", "Retrouvez le résultat dans « Mes candidatures »."));
}

// ---------------------------------------------------------------------------------------------------------------
// Candidatures

async function candidatures(contenu) {
  document.title = "Mes candidatures · INPT";
  const l = await lire("/etudiant/candidatures");
  vider(contenu, entetePage("Mes candidatures", "Toutes vos candidatures enregistrées et leur avancement."));
  if (!l.candidatures.length && !l.soumissionsNonEnregistrees.length) {
    contenu.append(vide("Vous n'avez pas encore candidaté", "Les offres compatibles avec votre profil vous attendent.",
      h("a", { class: "bouton", href: "#/etudiant/offres" }, "Voir les offres")));
    return;
  }
  contenu.append(h("section", { class: "panneau" }, h("div", { class: "panneau-titre" }, h("h2", {}, "Candidatures enregistrées"),
    h("span", { class: "discret" }, "Vous pouvez annuler tant que l'entreprise n'a pas commencé l'évaluation.")),
  l.candidatures.length ? tableau([
    { titre: "Offre", valeur: c => [h("a", { class: "lien-ligne", href: `#/etudiant/candidatures/${c.id}` }, c.titreOffre), h("div", { class: "secondaire" }, c.entreprise)] },
    { titre: "Déposée le", valeur: c => dateCourte(c.creeLe) },
    { titre: "Dernière mise à jour", valeur: c => dateHeure(c.modifieLe) },
    { titre: "Statut", valeur: c => badge(c.statut) },
  ], l.candidatures, "Candidatures enregistrées") : vide("Aucune candidature enregistrée.")));
  if (l.soumissionsNonEnregistrees.length) {
    contenu.append(h("section", { class: "panneau" }, h("div", { class: "panneau-titre" }, h("h2", {}, "Dépôts non enregistrés"),
      h("span", { class: "discret" }, "Dossiers jugés non recevables ou interrompus, avec le motif.")),
    tableau([
      { titre: "Offre", valeur: s => s.titreOffre },
      { titre: "Déposé le", valeur: s => dateHeure(s.creeLe) },
      { titre: "Résultat", valeur: s => badge(s.statut) },
      { titre: "Motif", valeur: s => (s.regleRejetee ? [h("strong", {}, `Règle ${s.regleRejetee} : `), s.motifRejet] : s.motifRejet || "–") },
    ], l.soumissionsNonEnregistrees, "Dépôts non enregistrés")));
  }
}

async function candidature(id, contenu) {
  const c = await lire(`/etudiant/candidatures/${id}`);
  document.title = `Candidature : ${c.titreOffre} · INPT`;
  const actions = [];
  if (c.statut === "DEPOSEE") {
    const bouton = h("button", { class: "bouton danger", onclick: async () => {
      if (!(await confirmer("Annuler cette candidature ?", "L'annulation est définitive. Vous pourrez candidater de nouveau à cette offre tant qu'elle reste ouverte.", "Annuler la candidature"))) return;
      await action(bouton, () => supprimer(`/etudiant/candidatures/${id}`), "Candidature annulée.");
      afficher({ page: "candidatures", id: String(id) }, contenu);
    } }, "Annuler ma candidature");
    actions.push(bouton);
  }
  vider(contenu,
    entetePage(c.titreOffre, c.entreprise, h("a", { href: "#/etudiant/candidatures" }, "Mes candidatures"), [badge(c.statut), ...actions]),
    c.statut === "EN_COURS_EVALUATION" ? message("info", null, "L'entreprise examine votre dossier : l'annulation n'est plus possible (règle R8).") : null,
    panneauProcessus(c.etapes, c.statut),
    h("div", { class: "grille-2" },
      h("div", {}, panneauEtapes(c.etapes)),
      h("div", {}, h("section", { class: "panneau" }, h("h2", {}, "Historique"), historiqueStatuts(c.historique)),
        h("section", { class: "panneau" }, h("h2", {}, "Ma lettre de motivation"), lettre(c.lettreMotivation)))));
}

// ---------------------------------------------------------------------------------------------------------------
// Notifications

async function notifications(contenu) {
  document.title = "Notifications · INPT";
  const liste = await lire("/etudiant/notifications");
  vider(contenu, entetePage("Notifications", "Les courriels que la plateforme vous a envoyés, du plus récent au plus ancien."));
  if (!liste.length) {
    contenu.append(vide("Aucune notification", "Vous recevrez un courriel à chaque étape importante de vos candidatures."));
    return;
  }
  contenu.append(h("section", { class: "panneau" }, liste.map(n => {
    const cadreCourriel = h("div");
    const repli = h("details", { class: "repli" },
      h("summary", {}, h("span", { class: "sujet" }, n.sujet), h("span", { class: "discret" }, `${TYPES_NOTIFICATION[n.type] ?? n.type} · ${dateHeure(n.dateEnvoi)}`), badge(n.statut)),
      cadreCourriel);
    // Le courriel est affiché dans un cadre isolé (sandbox) : son HTML ne peut ni exécuter de script ni toucher à la page.
    repli.addEventListener("toggle", () => {
      if (repli.open && !cadreCourriel.firstChild) cadreCourriel.append(h("iframe", { class: "courriel", sandbox: "", title: n.sujet, srcdoc: n.corps }));
    });
    return repli;
  })));
}

// ---------------------------------------------------------------------------------------------------------------
// Stage

async function stages(contenu) {
  document.title = "Mon stage · INPT";
  const liste = await lire("/etudiant/stages");
  vider(contenu, entetePage("Mon stage", "Dossier de stage créé après l'acceptation d'une candidature."));
  if (!liste.length) {
    contenu.append(vide("Pas encore de stage", "Votre dossier de stage apparaîtra ici dès qu'une entreprise aura accepté votre candidature."));
    return;
  }
  for (const s of liste) contenu.append(blocStage(s));
}

export function blocStage(s) {
  return h("section", { class: "panneau" },
    h("div", { class: "panneau-titre" }, h("h2", {}, s.titreOffre), badge(s.statut)),
    h("div", { class: "grille-2" },
      h("div", {},
        h("dl", { class: "fiche" },
          h("dt", {}, "Entreprise"), h("dd", {}, s.entreprise ?? "–"),
          h("dt", {}, "Période"), h("dd", {}, `du ${dateFr(s.dateDebut)} au ${dateFr(s.dateFin)}`),
          h("dt", {}, "Encadrant entreprise"), h("dd", {}, s.encadrantEntreprise ?? "à désigner"),
          h("dt", {}, "Encadrant pédagogique"), h("dd", {}, s.encadrantPedagogique ?? "à désigner")),
        h("h3", { style: "margin-top:20px" }, "Comptes rendus"),
        s.comptesRendus?.length
          ? s.comptesRendus.map(cr => h("article", { style: "border-top:1px solid var(--filet);padding:10px 0" },
            h("div", {}, h("strong", {}, cr.periode), h("span", { class: "discret" }, ` · ${cr.auteur} · ${dateCourte(cr.creeLe)}`)),
            h("p", { style: "white-space:pre-wrap;margin:4px 0 0" }, cr.contenu)))
          : h("p", { class: "discret" }, "Aucun compte rendu pour le moment."),
        h("h3", { style: "margin-top:20px" }, "Évaluations"),
        s.evaluations?.length
          ? s.evaluations.map(e => h("article", { style: "border-top:1px solid var(--filet);padding:10px 0" },
            h("div", {}, h("strong", { class: "chiffres" }, note(e.note)), h("span", { class: "discret" }, ` · ${e.evaluateur} · ${dateCourte(e.creeLe)}`)),
            e.commentaire ? h("p", { style: "margin:4px 0 0" }, e.commentaire) : null))
          : h("p", { class: "discret" }, "Aucune évaluation pour le moment.")),
      h("div", {}, h("h3", {}, "Historique des opérations"),
        chronologie((s.historique || []).map(o => ({ quand: o.date, quoi: traduireCodes(o.detail) || o.operation, qui: acteur(o.acteur) }))))));
}

// ---------------------------------------------------------------------------------------------------------------
// Profil

async function profil(contenu) {
  document.title = "Mon profil · INPT";
  const e = await lire("/etudiant/profil");
  vider(contenu, entetePage(`${e.prenom} ${e.nom}`, `${e.filiere} · ${e.niveau}`, null, badge(e.statut)),
    h("section", { class: "panneau", style: "max-width:720px" },
      h("dl", { class: "fiche" },
        h("dt", {}, "Matricule"), h("dd", { class: "code" }, e.matricule),
        h("dt", {}, "Filière"), h("dd", {}, e.filiere), h("dt", {}, "Niveau"), h("dd", {}, e.niveau),
        h("dt", {}, "Adresse électronique"), h("dd", {}, e.email), h("dt", {}, "Téléphone"), h("dd", {}, e.telephone),
        h("dt", {}, "Ville"), h("dd", {}, e.ville),
        h("dt", {}, "Compétences déclarées"), h("dd", {}, e.competences.join(", ") || "–")),
      h("p", { class: "discret", style: "margin-top:14px" }, "Pour corriger ces informations, adressez-vous au service des stages.")));
}

export { toast };
