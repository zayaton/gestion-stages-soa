// Coquille de l'application : connexion, bandeau, navigation par espace et routage par l'adresse (#/espace/page/id).
import { api, ErreurApi, quandSessionExpire, session } from "./api.js";
import { champ, erreur, h, message, toast, valider, vider } from "./ui.js";
import * as etudiant from "./espace-etudiant.js";
import * as entreprise from "./espace-entreprise.js";
import * as responsable from "./espace-responsable.js";

const racine = document.getElementById("application");

const ESPACES = {
  ETUDIANT: { module: etudiant, base: "etudiant", nom: "Espace étudiant", accueil: "offres" },
  ENTREPRISE: { module: entreprise, base: "entreprise", nom: "Espace entreprise", accueil: "candidatures" },
  RESPONSABLE: { module: responsable, base: "responsable", nom: "Espace responsable", accueil: "tableau" },
};

const DEMO = [
  ["Étudiante", "salma.elidrissi@inpt.ac.ma", "Etudiant@2026"],
  ["Entreprise", "contact@novapay-maghreb.ma", "Entreprise@2026"],
  ["Responsable", "responsable@inpt.ac.ma", "Responsable@2026"],
];

let utilisateur = null;

// ---------------------------------------------------------------------------------------------------------------
// Connexion

function pageConnexion(messageInitial) {
  document.title = "Connexion · INPT, plateforme des stages";
  const identifiant = h("input", { type: "email", name: "identifiant", autocomplete: "username", required: true });
  const motDePasse = h("input", { type: "password", name: "motDePasse", autocomplete: "current-password", required: true });
  const retour = h("div");
  if (messageInitial) retour.append(message("info", null, messageInitial));
  const bouton = h("button", { class: "bouton", type: "submit" }, "Se connecter");
  const formulaire = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider([[identifiant, v => (v ? null : "Saisissez votre adresse électronique.")], [motDePasse, v => (v ? null : "Saisissez votre mot de passe.")]])) return;
    bouton.disabled = true;
    vider(retour);
    try {
      const { donnees } = await api("POST", "/auth/connexion", { identifiant: identifiant.value.trim(), motDePasse: motDePasse.value });
      session.enregistrer({ jeton: donnees.jeton, expireLe: donnees.expireLe });
      utilisateur = donnees.utilisateur;
      // replaceState plutôt que location.hash : un « hashchange » afficherait l'espace une seconde fois.
      history.replaceState(null, "", `#/${ESPACES[utilisateur.role].base}/${ESPACES[utilisateur.role].accueil}`);
      afficher();
    } catch (err) {
      vider(retour, message("ko", "Connexion impossible", err.message));
      motDePasse.value = "";
      motDePasse.focus();
    } finally {
      bouton.disabled = false;
    }
  } },
  champ("Adresse électronique", identifiant), champ("Mot de passe", motDePasse), h("div", { class: "actions" }, bouton));

  const demo = h("details", { class: "comptes-demo" }, h("summary", {}, "Comptes de démonstration"),
    h("table", { class: "tableau" }, h("tbody", {}, DEMO.map(([role, id, mdp]) => h("tr", {},
      h("td", {}, role), h("td", {}, h("button", { type: "button", onclick: () => { identifiant.value = id; motDePasse.value = mdp; identifiant.focus(); } }, id)))))),
    h("p", { class: "discret" }, "Tous les comptes de démonstration figurent dans le README. Données fictives."));

  delete document.body.dataset.espace;
  vider(racine, h("div", { class: "connexion" },
    h("section", { class: "connexion-presentation" },
      h("div", { class: "zlafa-scene", "aria-hidden": "true" }, h("img", { src: "/img/zlafa-blanc.png", alt: "" })),
      h("div", { class: "connexion-carte" },
        h("p", { class: "surtitre" }, "Institut National des Postes et Télécommunications"),
        h("h1", {}, "La plateforme des stages", h("br"), h("span", {}, "et des offres de recrutement")),
        h("p", {}, "Les entreprises partenaires publient leurs offres, les étudiants candidatent, et chaque dossier est suivi jusqu'à la fin du stage."),
        h("ol", { class: "circuit" },
          h("li", { class: "etudiant" }, h("strong", {}, "Étudiants"), h("span", {}, "trouvent les offres compatibles et candidatent")),
          h("li", { class: "entreprise" }, h("strong", {}, "Entreprises"), h("span", {}, "évaluent les dossiers et rendent leur décision")),
          h("li", { class: "responsable" }, h("strong", {}, "Responsable des stages"), h("span", {}, "suit chaque candidature et chaque stage"))))),
    h("section", { class: "connexion-formulaire" },
      h("div", { class: "cadre" },
        h("img", { class: "connexion-logo", src: "/img/logo-inpt.png", alt: "INPT, Institut National des Postes et Télécommunications" }),
        h("h2", {}, "Connexion"), h("p", { class: "secondaire" }, "Accédez à votre espace avec votre adresse électronique."),
        retour, formulaire, demo,
        h("p", { class: "connexion-pied" }, "INPT · Rabat · Année universitaire 2026-2027")))));
  identifiant.focus();
}

async function deconnecter() {
  try { await api("POST", "/auth/deconnexion"); } catch { /* la session locale est effacée de toute façon */ }
  session.effacer();
  utilisateur = null;
  // replaceState ne déclenche pas « hashchange », qui réafficherait la connexion sans le message.
  history.replaceState(null, "", location.pathname);
  pageConnexion("Vous êtes déconnecté.");
}

quandSessionExpire(() => {
  utilisateur = null;
  pageConnexion("Votre session a expiré. Reconnectez-vous pour continuer.");
});

// ---------------------------------------------------------------------------------------------------------------
// Cadre des espaces

function cadre(espace, route) {
  const liens = espace.module.navigation.map(([page, texte]) => {
    const href = `#/${espace.base}/${page}`;
    return h("li", {}, h("a", { href, "aria-current": route.page === page ? "page" : null }, texte));
  });
  const contenu = h("main", { id: "contenu", tabindex: "-1" });
  const initiales = (utilisateur.nom || "?").split(/\s+/).filter(Boolean).slice(0, 2).map(m => m[0].toUpperCase()).join("");
  document.body.dataset.espace = espace.base;
  vider(racine,
    h("a", { href: "#contenu", class: "cache", onclick: ev => { ev.preventDefault(); contenu.focus(); } }, "Aller au contenu"),
    h("header", { class: "bandeau" },
      h("div", { class: "bandeau-interieur" },
        h("a", { class: "marque", href: `#/${espace.base}/${espace.accueil}` },
          h("span", { class: "marque-plaque" }, h("img", { class: "marque-logo", src: "/img/logo-inpt.png", alt: "INPT" })),
          h("span", { class: "marque-texte" }, h("span", { class: "marque-nom" }, "Plateforme des stages"), h("span", { class: "marque-sous" }, "Offres, candidatures et suivi des stages"))),
        h("div", { class: "bandeau-utilisateur" },
          h("span", { class: "avatar", "aria-hidden": "true" }, initiales),
          h("div", { class: "identite" }, h("div", { class: "role" }, espace.nom), h("div", { class: "nom" }, utilisateur.nom)),
          h("button", { class: "bouton fantome", onclick: deconnecter }, "Se déconnecter"))),
      h("nav", { class: "navigation", "aria-label": espace.nom }, h("ul", {}, liens))),
    contenu,
    h("footer", { class: "pied" },
      h("div", { class: "pied-frise", "aria-hidden": "true" }, h("img", { src: "/img/zlafa-blanc.png", alt: "" })),
      h("div", { class: "pied-interieur" },
        h("img", { src: "/img/logo-inpt.png", alt: "INPT", class: "pied-logo" }),
        h("span", {}, "Institut National des Postes et Télécommunications · Rabat"),
        h("span", { class: "discret" }, "Données de démonstration fictives"))));
  return contenu;
}

function lireRoute() {
  const morceaux = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  return { base: morceaux[0], page: morceaux[1], id: morceaux[2], suite: morceaux.slice(3) };
}

async function afficher() {
  if (!session.lire()) return pageConnexion();
  if (!utilisateur) {
    try {
      utilisateur = (await api("GET", "/auth/moi")).donnees;
    } catch (err) {
      if (err instanceof ErreurApi && err.statut === 401) return;
      vider(racine, h("main", {}, erreur(err, "Le portail ne répond pas")));
      return;
    }
  }
  const espace = ESPACES[utilisateur.role];
  let route = lireRoute();
  if (route.base !== espace.base || !route.page) {
    location.replace(`#/${espace.base}/${espace.accueil}`);
    route = lireRoute();
  }
  const contenu = cadre(espace, route);
  try {
    await espace.module.afficher(route, contenu, utilisateur);
  } catch (err) {
    vider(contenu, erreur(err));
  }
  window.scrollTo(0, 0);
}

window.addEventListener("hashchange", () => afficher());
window.addEventListener("unhandledrejection", ev => {
  if (ev.reason instanceof ErreurApi) { ev.preventDefault(); return; }
  toast("Une erreur inattendue est survenue dans la page.", "ko");
});
afficher();
