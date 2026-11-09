// Espace entreprise : candidatures reçues (coordonnées masquées par le serveur) et décision, offres, profil.
import { envoyer, lire, remplacer } from "./api.js";
import { historiqueStatuts, lettre, panneauEtapes, panneauProcessus } from "./dossier.js";
import {
  action, badge, champ, chargement, confirmer, dateCourte, dateFr, dateHeure, entetePage, erreurChamp, FILIERES, h, message, NIVEAUX,
  nombre, obligatoire, select, tableau, toast, valider, vide, vider,
} from "./ui.js";

export const navigation = [["candidatures", "Candidatures reçues"], ["offres", "Mes offres"], ["profil", "Profil de l'entreprise"]];

export async function afficher(route, contenu) {
  vider(contenu, chargement());
  if (route.page === "candidatures" && route.id) return candidature(Number(route.id), contenu);
  if (route.page === "candidatures") return candidatures(contenu);
  if (route.page === "offres" && route.id === "nouvelle") return formulaireOffre(null, contenu);
  if (route.page === "offres" && route.id) return formulaireOffre(Number(route.id), contenu);
  if (route.page === "offres") return offres(contenu);
  if (route.page === "profil") return profil(contenu);
  location.replace("#/entreprise/candidatures");
}

// ---------------------------------------------------------------------------------------------------------------
// Candidatures reçues

async function candidatures(contenu) {
  document.title = "Candidatures reçues · INPT";
  const filtre = select([["", "Tous les statuts"], ["DEPOSEE", "Déposées"], ["EN_COURS_EVALUATION", "En évaluation"], ["ACCEPTEE", "Acceptées"],
    ["REFUSEE", "Refusées"], ["ANNULEE", "Annulées"]], new URLSearchParams(location.hash.split("?")[1]).get("statut") || "");
  const zone = h("div", {}, chargement());
  const charger = async () => {
    vider(zone, chargement());
    const liste = await lire(`/entreprise/candidatures${filtre.value ? `?statut=${filtre.value}` : ""}`);
    if (!liste.length) {
      vider(zone, vide("Aucune candidature", filtre.value ? "Aucune candidature avec ce statut." : "Les candidatures à vos offres apparaîtront ici."));
      return;
    }
    vider(zone, tableau([
      { titre: "Candidat", valeur: c => [h("a", { class: "lien-ligne", href: `#/entreprise/candidatures/${c.id}` }, c.etudiant ? `${c.etudiant.prenom} ${c.etudiant.nom}` : `Étudiant n° ${c.etudiantId}`),
        c.etudiant ? h("div", { class: "secondaire" }, `${c.etudiant.filiere} · ${c.etudiant.niveau}`) : null] },
      { titre: "Offre", valeur: c => c.titreOffre },
      { titre: "Compétences", valeur: c => (c.scoreCompetences === undefined ? "–" : `${c.scoreCompetences} %`), classe: "nombre" },
      { titre: "Reçue le", valeur: c => dateCourte(c.creeLe) },
      { titre: "Statut", valeur: c => badge(c.statut) },
    ], liste, "Candidatures reçues"));
  };
  filtre.addEventListener("change", charger);
  vider(contenu, entetePage("Candidatures reçues", "Ouvrez un dossier pour démarrer l'évaluation, puis rendre votre décision."),
    h("div", { class: "barre-filtres" }, h("div", { class: "champ" }, h("label", {}, "Statut", filtre))), zone);
  await charger();
}

async function candidature(id, contenu) {
  const c = await lire(`/entreprise/candidatures/${id}`);
  const e = c.etudiant;
  document.title = `Candidature de ${e ? `${e.prenom} ${e.nom}` : "l'étudiant"} · INPT`;
  vider(contenu,
    entetePage(e ? `${e.prenom} ${e.nom}` : `Étudiant n° ${c.etudiantId}`, `Candidature à « ${c.titreOffre} »`,
      h("a", { href: "#/entreprise/candidatures" }, "Candidatures reçues"), badge(c.statut)),
    h("div", { class: "grille-2" },
      h("div", {},
        h("section", { class: "panneau" }, h("h2", {}, "Le candidat"),
          e ? h("dl", { class: "fiche" },
            h("dt", {}, "Filière"), h("dd", {}, e.filiere), h("dt", {}, "Niveau"), h("dd", {}, e.niveau), h("dt", {}, "Ville"), h("dd", {}, e.ville),
            h("dt", {}, "Compétences"), h("dd", {}, e.competences.join(", ") || "–"),
            h("dt", {}, "Adéquation"), h("dd", {}, c.scoreCompetences === undefined ? "–" : `${c.scoreCompetences} % des compétences demandées (indicatif)`),
            h("dt", {}, "Courriel"), h("dd", { class: "code" }, e.email), h("dt", {}, "Téléphone"), h("dd", { class: "code" }, e.telephone))
            : message("attente", null, "Le profil de l'étudiant est momentanément indisponible."),
          h("p", { class: "discret", style: "margin-top:12px" }, "Coordonnées masquées : le service des stages vous transmet les coordonnées complètes après acceptation.")),
        h("section", { class: "panneau" }, h("h2", {}, "Lettre de motivation"), lettre(c.lettreMotivation))),
      h("div", {}, panneauDecision(c, contenu),
        h("section", { class: "panneau" }, h("h2", {}, "Historique"), historiqueStatuts(c.historique)))),
    panneauProcessus(c.etapes, c.statut),
    panneauEtapes(c.etapes));
}

function panneauDecision(c, contenu) {
  const recharger = () => afficher({ page: "candidatures", id: String(c.id) }, contenu);
  if (c.statut === "DEPOSEE") {
    const bouton = h("button", { class: "bouton", onclick: async () => {
      await action(bouton, () => envoyer(`/entreprise/candidatures/${c.id}/evaluation`), "Évaluation démarrée.");
      recharger();
    } }, "Démarrer l'évaluation");
    return h("section", { class: "panneau" }, h("h2", {}, "Évaluation"),
      h("p", {}, "Ouvrez l'évaluation pour pouvoir rendre votre décision. À partir de ce moment, l'étudiant ne peut plus annuler sa candidature."), bouton);
  }
  if (c.statut !== "EN_COURS_EVALUATION") {
    return h("section", { class: "panneau" }, h("h2", {}, "Décision"),
      message(c.statut === "ACCEPTEE" ? "ok" : "info", null, c.statut === "ACCEPTEE" ? "Candidature acceptée : le dossier de stage a été créé."
        : c.statut === "REFUSEE" ? "Candidature refusée." : "Candidature annulée par l'étudiant."));
  }
  const choix = [
    ["ACCEPTER", "Accepter", "Une place est retirée de l'offre, le stage est créé et l'étudiant est prévenu."],
    ["REFUSER", "Refuser", "L'étudiant est prévenu ; le commentaire lui est transmis."],
    ["DEMANDER_COMPLEMENT", "Demander un complément", "La candidature reste en évaluation ; précisez l'information attendue."],
  ];
  const radios = choix.map(([v]) => h("input", { type: "radio", name: "decision", value: v }));
  const commentaire = h("textarea", { rows: "4", maxlength: "1000" });
  const zoneErreur = h("div");
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, "Enregistrer la décision");
  const formulaire = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    const decision = radios.find(r => r.checked)?.value;
    vider(zoneErreur);
    if (!decision) { zoneErreur.append(message("ko", null, "Choisissez une décision.")); return; }
    if (!valider([[commentaire, v => (decision === "DEMANDER_COMPLEMENT" && !v ? "Précisez l'information attendue de l'étudiant." : null)]])) return;
    const libelle = choix.find(x => x[0] === decision)[1];
    if (decision !== "DEMANDER_COMPLEMENT" && !(await confirmer(`${libelle} cette candidature ?`, "La décision est définitive et l'étudiant en est informé par courriel.", libelle))) return;
    try {
      await action(bouton, () => envoyer(`/entreprise/candidatures/${c.id}/decision`, { decision, commentaire: commentaire.value.trim() || null }),
        "Décision enregistrée.");
      recharger();
    } catch (err) {
      vider(retour, message("ko", "Décision refusée", err.message, err.details));
    }
  } },
  h("fieldset", { class: "choix", style: "border:0;padding:0;margin:0" }, h("legend", { class: "cache" }, "Décision"),
    choix.map(([v, texte, detail], i) => h("label", {}, radios[i], h("span", {}, texte, h("span", { class: "detail" }, detail))))),
  zoneErreur,
  champ("Commentaire pour l'étudiant", commentaire, "Obligatoire pour une demande de complément."),
  h("div", { class: "actions" }, bouton));
  return h("section", { class: "panneau" }, h("h2", {}, "Votre décision"), retour, formulaire);
}

// ---------------------------------------------------------------------------------------------------------------
// Offres

async function offres(contenu) {
  document.title = "Mes offres · INPT";
  const liste = await lire("/entreprise/offres");
  const cloturer = async (o, bouton) => {
    if (!(await confirmer(`Clôturer « ${o.titre} » ?`, "L'offre n'acceptera plus de candidature. Les candidatures en cours restent à traiter.", "Clôturer"))) return;
    await action(bouton, () => envoyer(`/entreprise/offres/${o.id}/cloture`), "Offre clôturée.");
    afficher({ page: "offres" }, contenu);
  };
  vider(contenu, entetePage("Mes offres", "Offres publiées sur la plateforme et candidatures reçues pour chacune.", null,
    h("a", { class: "bouton", href: "#/entreprise/offres/nouvelle" }, "Publier une offre")));
  if (!liste.length) {
    contenu.append(vide("Aucune offre", "Publiez votre première offre de stage.", h("a", { class: "bouton", href: "#/entreprise/offres/nouvelle" }, "Publier une offre")));
    return;
  }
  contenu.append(h("section", { class: "panneau" }, tableau([
    { titre: "Offre", valeur: o => [h("a", { class: "lien-ligne", href: `#/entreprise/offres/${o.id}` }, o.titre), h("div", { class: "secondaire" }, o.ville)] },
    { titre: "Date limite", valeur: o => dateCourte(o.dateLimite) },
    { titre: "Places", valeur: o => nombre(o.nombrePlaces), classe: "nombre" },
    { titre: "Candidatures", valeur: o => nombre(o.candidatures), classe: "nombre" },
    { titre: "Statut", valeur: o => badge(o.statut) },
    { titre: "", valeur: o => (o.statut === "CLOTUREE" ? null : h("button", { class: "bouton discret", onclick: ev => cloturer(o, ev.currentTarget) }, "Clôturer")) },
  ], liste, "Mes offres")));
}

const TRANSITIONS_OFFRE = { BROUILLON: ["BROUILLON", "ACTIVE", "CLOTUREE"], ACTIVE: ["ACTIVE", "POURVUE", "CLOTUREE"], POURVUE: ["POURVUE", "ACTIVE", "CLOTUREE"], CLOTUREE: ["CLOTUREE"] };
const NOMS_STATUT_OFFRE = { BROUILLON: "Brouillon (non visible des étudiants)", ACTIVE: "Active (publiée)", POURVUE: "Pourvue", CLOTUREE: "Clôturée" };

async function formulaireOffre(id, contenu) {
  const o = id ? await lire(`/entreprise/offres/${id}`) : null;
  document.title = `${o ? "Modifier l'offre" : "Publier une offre"} · INPT`;
  const demain = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const titre = h("input", { value: o?.titre ?? "", maxlength: "150" });
  const description = h("textarea", { rows: "6" });
  description.value = o?.description ?? "";
  const competences = h("input", { value: o?.competences.join(", ") ?? "", placeholder: "SQL, BPMN, Analyse fonctionnelle" });
  const duree = h("input", { type: "number", min: "1", max: "52", value: o?.dureeSemaines ?? 12 });
  const dateLimite = h("input", { type: "date", min: o ? undefined : demain, value: o?.dateLimite ?? "" });
  const places = h("input", { type: "number", min: o ? "0" : "1", value: o?.nombrePlaces ?? 1 });
  const ville = h("input", { value: o?.ville ?? "" });
  const niveau = select(NIVEAUX.map(n => [n, n]), o?.niveauMinimum ?? "1A");
  const filieres = FILIERES.map(f => h("input", { type: "checkbox", value: f, checked: o?.filieres.includes(f) }));
  const statuts = o ? TRANSITIONS_OFFRE[o.statut] : ["BROUILLON", "ACTIVE"];
  const statut = select(statuts.map(s => [s, NOMS_STATUT_OFFRE[s]]), o?.statut ?? "ACTIVE");
  const erreurFilieres = h("div");
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, o ? "Enregistrer les modifications" : "Publier l'offre");

  const formulaire = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    vider(erreurFilieres);
    const choisies = filieres.filter(f => f.checked).map(f => f.value);
    const ok = valider([
      [titre, obligatoire("Le titre")], [description, obligatoire("La description")],
      [competences, v => (v.split(",").map(x => x.trim()).filter(Boolean).length ? null : "Indiquez au moins une compétence.")],
      [duree, v => (Number(v) >= 1 ? null : "La durée doit être d'au moins une semaine.")],
      [dateLimite, v => (!v ? "La date limite est obligatoire." : !o && v < demain ? "La date limite doit être postérieure à aujourd'hui." : null)],
      [places, v => (v === "" || Number(v) < (o ? 0 : 1) ? (o ? "Le nombre de places ne peut pas être négatif." : "Proposez au moins une place.") : null)],
      [ville, obligatoire("La ville")],
    ]);
    if (!choisies.length) erreurFilieres.append(h("span", { class: "erreur" }, "Cochez au moins une filière."));
    if (!ok || !choisies.length) return;
    const corps = {
      titre: titre.value.trim(), description: description.value.trim(), competences: competences.value.split(",").map(x => x.trim()).filter(Boolean),
      dureeSemaines: Number(duree.value), dateLimite: dateLimite.value, nombrePlaces: Number(places.value), filieres: choisies,
      niveauMinimum: niveau.value, ville: ville.value.trim(), entrepriseId: 0, statut: statut.value,
    };
    try {
      const r = await action(bouton, () => (o ? remplacer(`/entreprise/offres/${o.id}`, corps) : envoyer("/entreprise/offres", corps)),
        o ? "Offre enregistrée." : "Offre publiée.");
      location.hash = `#/entreprise/offres/${r.id}`;
      if (o) afficher({ page: "offres", id: String(r.id) }, contenu);
    } catch (err) {
      vider(retour, message("ko", "L'offre n'a pas été enregistrée", err.message, err.details));
      for (const [saisie, mot] of [[dateLimite, "date limite"], [places, "place"]]) {
        if (err.details.some(d => d.toLowerCase().includes(mot))) erreurChamp(saisie, err.details.find(d => d.toLowerCase().includes(mot)));
      }
    }
  } },
  champ("Titre", titre),
  champ("Description de la mission", description),
  champ("Compétences demandées", competences, "Séparées par des virgules."),
  h("div", { class: "ligne" }, champ("Durée (semaines)", duree), champ("Date limite de candidature", dateLimite), champ("Places", places)),
  h("div", { class: "ligne" }, champ("Ville", ville), champ("Niveau minimum", niveau), champ("Statut", statut)),
  h("fieldset", { style: "border:0;padding:0;margin:0" }, h("legend", { class: "libelle", style: "font-weight:600;font-size:.87rem;margin-bottom:6px" }, "Filières acceptées"),
    h("div", { style: "display:flex;flex-wrap:wrap;gap:6px 18px" }, FILIERES.map((f, i) => h("label", { style: "display:flex;gap:6px;align-items:center" }, filieres[i], f))),
    erreurFilieres),
  h("div", { class: "actions" }, bouton, h("a", { class: "bouton secondaire", href: "#/entreprise/offres" }, "Retour aux offres")));

  vider(contenu, entetePage(o ? o.titre : "Publier une offre", o ? `Offre n° ${o.id}` : "Une offre en brouillon n'est pas visible des étudiants.",
    h("a", { href: "#/entreprise/offres" }, "Mes offres"), o ? badge(o.statut) : null),
  o?.statut === "CLOTUREE" ? message("info", null, "Une offre clôturée ne peut plus être modifiée.") : null,
  h("section", { class: "panneau", style: "max-width:860px" }, retour, formulaire));
  if (o?.statut === "CLOTUREE") formulaire.querySelectorAll("input, textarea, select, button[type=submit]").forEach(x => { x.disabled = true; });
}

// ---------------------------------------------------------------------------------------------------------------
// Profil

async function profil(contenu) {
  document.title = "Profil de l'entreprise · INPT";
  const e = await lire("/entreprise/profil");
  const saisies = {
    raisonSociale: h("input", { value: e.raisonSociale }), secteur: h("input", { value: e.secteur }), adresse: h("input", { value: e.adresse }),
    ville: h("input", { value: e.ville }), email: h("input", { type: "email", value: e.email }), telephone: h("input", { value: e.telephone }),
    contactNom: h("input", { value: e.responsable?.nom ?? "" }), contactFonction: h("input", { value: e.responsable?.fonction ?? "" }),
    contactEmail: h("input", { type: "email", value: e.responsable?.email ?? "" }), contactTelephone: h("input", { value: e.responsable?.telephone ?? "" }),
  };
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, "Enregistrer");
  const formulaire = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider(Object.entries(saisies).map(([, s]) => [s, v => (v ? null : "Ce champ est obligatoire.")]))) return;
    const v = k => saisies[k].value.trim();
    try {
      await action(bouton, () => remplacer("/entreprise/profil", {
        raisonSociale: v("raisonSociale"), ice: e.ice, secteur: v("secteur"), adresse: v("adresse"), ville: v("ville"), email: v("email"), telephone: v("telephone"),
        responsable: { nom: v("contactNom"), fonction: v("contactFonction"), email: v("contactEmail"), telephone: v("contactTelephone") }, statut: e.statut,
      }), "Profil enregistré.");
      vider(retour);
    } catch (err) {
      vider(retour, message("ko", "Le profil n'a pas été enregistré", err.message, err.details));
    }
  } },
  h("div", { class: "ligne" }, champ("Raison sociale", saisies.raisonSociale), champ("Secteur d'activité", saisies.secteur)),
  h("div", { class: "ligne" }, champ("Adresse", saisies.adresse), champ("Ville", saisies.ville)),
  h("div", { class: "ligne" }, champ("Courriel", saisies.email), champ("Téléphone", saisies.telephone, "10 chiffres, par exemple 0522481020.")),
  h("h3", { style: "margin:8px 0 0" }, "Responsable de contact"),
  h("div", { class: "ligne" }, champ("Nom", saisies.contactNom), champ("Fonction", saisies.contactFonction)),
  h("div", { class: "ligne" }, champ("Courriel", saisies.contactEmail), champ("Téléphone", saisies.contactTelephone)),
  h("div", { class: "actions" }, bouton));
  vider(contenu, entetePage(e.raisonSociale, `${e.secteur} · ${e.ville}`, null, badge(e.statut)),
    h("div", { class: "grille-2" },
      h("section", { class: "panneau" }, retour, formulaire),
      h("section", { class: "panneau" }, h("h2", {}, "Identification"),
        h("dl", { class: "fiche" }, h("dt", {}, "ICE"), h("dd", { class: "code" }, e.ice), h("dt", {}, "Statut"), h("dd", {}, badge(e.statut))),
        h("p", { class: "discret", style: "margin-top:12px" }, "L'ICE et le statut sont gérés par le service des stages."))));
}

export { dateFr, dateHeure, toast };
