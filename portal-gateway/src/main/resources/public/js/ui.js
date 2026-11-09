// Briques d'interface. Règle de sécurité : toute donnée entre dans la page par textContent (fonction h),
// jamais par innerHTML ; un nom ou une lettre de motivation ne peut donc pas injecter de HTML.

/** Crée un élément : h("a", { href, class: "x", onclick }, "texte", enfant, ...). */
export function h(balise, attributs = {}, ...enfants) {
  const e = document.createElement(balise);
  for (const [cle, valeur] of Object.entries(attributs || {})) {
    if (valeur === undefined || valeur === null || valeur === false) continue;
    if (cle.startsWith("on") && typeof valeur === "function") e.addEventListener(cle.slice(2), valeur);
    else if (cle === "class") e.className = valeur;
    else if (cle === "dataset") Object.assign(e.dataset, valeur);
    else if (valeur === true) e.setAttribute(cle, "");
    else e.setAttribute(cle, String(valeur));
  }
  ajouter(e, enfants);
  return e;
}

function ajouter(parent, enfants) {
  for (const enfant of enfants.flat(Infinity)) {
    if (enfant === null || enfant === undefined || enfant === false) continue;
    parent.append(enfant instanceof Node ? enfant : document.createTextNode(String(enfant)));
  }
}

export function vider(e, ...enfants) {
  e.replaceChildren();
  ajouter(e, enfants);
  return e;
}

// ---------- Formats ----------

const DATE = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "long", year: "numeric" });
const DATE_COURTE = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric" });
const DATE_HEURE = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

const enDate = v => (v instanceof Date ? v : new Date(String(v).length === 10 ? `${v}T00:00:00` : v));
export const dateFr = v => (v ? DATE.format(enDate(v)) : "–");
export const dateCourte = v => (v ? DATE_COURTE.format(enDate(v)) : "–");
export const dateHeure = v => (v ? DATE_HEURE.format(enDate(v)) : "–");

/** Délai jusqu'à une date limite, en mots : « aujourd'hui », « dans 3 jours », « dépassée ». */
export function delai(dateLimite) {
  const aujourdhui = new Date(); aujourdhui.setHours(0, 0, 0, 0);
  const jours = Math.round((enDate(dateLimite) - aujourdhui) / 86400000);
  if (jours < 0) return { texte: "date dépassée", proche: true };
  if (jours === 0) return { texte: "dernier jour", proche: true };
  if (jours === 1) return { texte: "demain", proche: true };
  return { texte: `dans ${jours} jours`, proche: jours <= 3 };
}

export const nombre = n => new Intl.NumberFormat("fr-FR").format(n ?? 0);
export const note = n => (n === null || n === undefined ? "–" : `${new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 }).format(n)}/20`);

// ---------- Libellés des états ----------

const ETATS = {
  // candidatures
  DEPOSEE: ["Déposée", "neutre"], EN_COURS_EVALUATION: ["En évaluation", "attente"], ACCEPTEE: ["Acceptée", "ok"],
  REFUSEE: ["Refusée", "ko"], ANNULEE: ["Annulée", "eteint"],
  // soumissions
  EN_TRAITEMENT: ["En traitement", "attente"], ENREGISTREE: ["Enregistrée", "ok"], REJETEE: ["Non recevable", "ko"], ERREUR: ["Interrompue", "ko"],
  // offres
  BROUILLON: ["Brouillon", "eteint"], ACTIVE: ["Active", "ok"], POURVUE: ["Pourvue", "neutre"], CLOTUREE: ["Clôturée", "eteint"],
  // entreprises
  INACTIVE: ["Inactive", "eteint"],
  // étudiants
  ACTIF: ["Actif", "ok"], SUSPENDU: ["Suspendu", "ko"], EN_STAGE: ["En stage", "neutre"], DIPLOME: ["Diplômé", "eteint"],
  // stages
  PLANIFIE: ["Planifié", "neutre"], EN_COURS: ["En cours", "attente"], TERMINE: ["Terminé", "ok"], INTERROMPU: ["Interrompu", "ko"],
  // notifications
  ENVOYEE: ["Envoyée", "ok"], ECHEC: ["Échec d'envoi", "ko"],
  // services
  DISPONIBLE: ["Disponible", "ok"], DEGRADE: ["Dégradé", "attente"], INDISPONIBLE: ["Indisponible", "ko"],
};
export const libelle = statut => ETATS[statut]?.[0] ?? statut;
export const badge = statut => h("span", { class: `badge ${ETATS[statut]?.[1] ?? "neutre"}` }, libelle(statut));

export const TYPES_NOTIFICATION = {
  CONFIRMATION_DEPOT: "Confirmation de dépôt", CANDIDATURE_REJETEE: "Candidature non recevable", ACCEPTATION: "Acceptation",
  REFUS: "Refus", DEMANDE_COMPLEMENT: "Demande de complément", NOUVELLE_CANDIDATURE_ENTREPRISE: "Nouvelle candidature",
  RAPPEL_DATE_LIMITE: "Rappel de date limite", ALERTE_RESPONSABLE: "Alerte au responsable",
};

/** Remplace, dans un texte venant des services, les codes connus (statuts, types de courriel) par leur libellé français. */
export const traduireCodes = texte => (texte ? String(texte).replace(/(?<![A-Za-z_])[A-Z][A-Z_]{3,}(?![A-Za-z_])/g,
  code => (TYPES_NOTIFICATION[code] ? `« ${TYPES_NOTIFICATION[code]} »` : ETATS[code] ? `« ${ETATS[code][0]} »` : code)) : texte);

export const FILIERES = ["AMOA", "ASEDS", "DATA", "SESNum", "ICCN", "Cloud", "Smart ICT"];
export const NIVEAUX = ["1A", "2A", "3A"];

// ---------- États de page ----------

export const chargement = (texte = "Chargement…") => h("div", { class: "chargement", role: "status" }, texte);

export function vide(titre, texte, action) {
  return h("div", { class: "vide" }, h("span", { class: "titre" }, titre), texte ? h("p", {}, texte) : null, action ?? null);
}

/** Bloc d'erreur lisible, avec les détails renvoyés par le serveur. */
export function erreur(err, titre = "Impossible d'afficher cette page") {
  return h("div", { class: "message ko", role: "alert" },
    h("span", { class: "titre" }, titre),
    h("p", {}, err.message),
    err.details?.length ? h("ul", {}, err.details.map(d => h("li", {}, d))) : null);
}

export function message(type, titre, texte, details) {
  return h("div", { class: `message ${type}`, role: type === "ko" ? "alert" : "status" },
    titre ? h("span", { class: "titre" }, titre) : null, texte ? h("p", {}, texte) : null,
    details?.length ? h("ul", {}, details.map(d => h("li", {}, d))) : null);
}

// ---------- Toasts ----------

export function toast(texte, type = "ok", titre) {
  let zone = document.querySelector(".toasts");
  if (!zone) { zone = h("div", { class: "toasts", "aria-live": "polite" }); document.body.append(zone); }
  const t = h("div", { class: `toast ${type}`, role: type === "ko" ? "alert" : "status" }, titre ? h("span", { class: "titre" }, titre) : null, texte);
  zone.append(t);
  setTimeout(() => t.remove(), type === "ko" ? 8000 : 4500);
}

/** Exécute une action de bouton : bouton occupé pendant l'appel, toast de succès ou d'erreur. */
export async function action(bouton, fonction, succes) {
  bouton.setAttribute("aria-busy", "true");
  bouton.disabled = true;
  try {
    const r = await fonction();
    if (succes) toast(succes);
    return r;
  } catch (err) {
    toast(err.message, "ko", "Action refusée");
    throw err;
  } finally {
    bouton.removeAttribute("aria-busy");
    bouton.disabled = false;
  }
}

// ---------- Confirmation ----------

export function confirmer(question, detail, libelleOui = "Confirmer") {
  return new Promise(resolve => {
    const d = h("dialog", { class: "modale" },
      h("h3", {}, question), detail ? h("p", { class: "secondaire" }, detail) : null,
      h("div", { class: "actions" },
        h("button", { class: "bouton", value: "oui", onclick: () => { d.close("oui"); } }, libelleOui),
        h("button", { class: "bouton secondaire", value: "non", onclick: () => { d.close("non"); } }, "Annuler")));
    d.addEventListener("close", () => { resolve(d.returnValue === "oui"); d.remove(); });
    document.body.append(d);
    d.showModal();
  });
}

// ---------- Formulaires ----------

/** Champ avec libellé, aide et zone d'erreur ; l'élément de saisie est relié aux deux pour les lecteurs d'écran. */
export function champ(libelleTexte, saisie, aide) {
  const id = saisie.id || `champ-${Math.random().toString(36).slice(2, 9)}`;
  saisie.id = id;
  const zoneAide = aide ? h("span", { class: "aide", id: `${id}-aide` }, aide) : null;
  const zoneErreur = h("span", { class: "erreur", id: `${id}-erreur`, "aria-live": "polite" });
  saisie.setAttribute("aria-describedby", [aide ? `${id}-aide` : null, `${id}-erreur`].filter(Boolean).join(" "));
  return h("div", { class: "champ" }, h("label", { for: id }, libelleTexte), saisie, zoneAide, zoneErreur);
}

export function erreurChamp(saisie, texte) {
  const zone = document.getElementById(`${saisie.id}-erreur`);
  if (zone) zone.textContent = texte || "";
  if (texte) saisie.setAttribute("aria-invalid", "true"); else saisie.removeAttribute("aria-invalid");
}

/** Valide des règles simples côté navigateur (le serveur revalide tout). Renvoie true si tout est correct. */
export function valider(regles) {
  let ok = true;
  let premier = null;
  for (const [saisie, verifier] of regles) {
    const texte = verifier(saisie.value.trim());
    erreurChamp(saisie, texte);
    if (texte) { ok = false; premier ??= saisie; }
  }
  premier?.focus();
  return ok;
}

export const obligatoire = nom => v => (v ? null : `${nom} est obligatoire.`);

export function select(options, valeur, attributs = {}) {
  return h("select", attributs, options.map(([v, texte]) => h("option", { value: v, selected: v === valeur }, texte)));
}

/** Tableau : colonnes [{ titre, valeur(ligne) -> contenu, classe }]. */
export function tableau(colonnes, lignes, legende) {
  return h("div", { class: "tableau-conteneur" },
    h("table", { class: "tableau" },
      legende ? h("caption", { class: "cache" }, legende) : null,
      h("thead", {}, h("tr", {}, colonnes.map(c => h("th", { scope: "col", class: c.classe }, c.titre)))),
      h("tbody", {}, lignes.map(l => h("tr", {}, colonnes.map(c => h("td", { class: c.classe }, c.valeur(l))))))));
}

/** Chronologie : éléments { quand, quoi, qui, detail, etat }. */
export function chronologie(elements) {
  if (!elements.length) return vide("Aucune opération enregistrée.");
  return h("ol", { class: "chronologie" }, elements.map(e =>
    h("li", { class: e.etat || "" },
      h("div", { class: "quand" }, dateHeure(e.quand)),
      h("div", { class: "quoi" }, e.quoi),
      e.detail ? h("div", {}, e.detail) : null,
      e.qui ? h("div", { class: "qui" }, e.qui) : null)));
}

/** Acteur d'un historique, en mots. */
export function acteur(code) {
  if (!code) return "";
  if (code === "processus" || code === "processus-bonita") return "Traitement automatique";
  if (code === "client:bonita") return "Processus Bonita";
  if (code.startsWith("client:")) return `Service ${code.slice(7)}`;
  const [role, id] = code.split(":");
  const roles = { etudiant: "Étudiant", entreprise: "Entreprise", responsable: "Responsable des stages" };
  return roles[role] ? `${roles[role]}${id ? ` n° ${id}` : ""}` : code;
}

export function entetePage(titre, sousTitre, fil, actions) {
  return h("div", { class: "entete-page" },
    h("div", {}, fil ? h("div", { class: "fil" }, fil) : null, h("h1", {}, titre), sousTitre ? h("p", { class: "sous-titre" }, sousTitre) : null),
    actions ? h("div", { class: "actions" }, actions) : null);
}

export function indicateurCompatibilite(c) {
  if (!c) return null;
  return h("div", { class: `compat ${c.compatible ? "oui" : "non"}` },
    h("strong", {}, c.compatible ? "Compatible avec votre profil" : "Non compatible"),
    !c.compatible && c.motif ? h("div", { class: "secondaire" }, c.motif) : null,
    h("div", { class: "mesure", title: "Part des compétences demandées que vous possédez : indicateur seulement, il ne bloque pas une candidature." },
      h("div", { class: "mesure-piste", "aria-hidden": "true" }, h("div", { class: "mesure-valeur", style: `width:${Math.max(0, Math.min(100, c.score))}%` })),
      h("span", { class: "discret chiffres" }, `Compétences : ${c.score} %`)));
}
