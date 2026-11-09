// Espace responsable des stages : tableau de bord, candidatures, stages (comptes rendus, évaluations), étudiants,
// entreprises, notifications et rappels.
import { envoyer, lire, modifier, remplacer, supprimer } from "./api.js";
import { historiqueStatuts, lettre, panneauEtapes, panneauProcessus } from "./dossier.js";
import { blocStage } from "./espace-etudiant.js";
import {
  action, badge, champ, chargement, confirmer, dateCourte, dateFr, dateHeure, entetePage, FILIERES, h, libelle, message,
  NIVEAUX, nombre, obligatoire, select, tableau, TYPES_NOTIFICATION, valider, vide, vider,
} from "./ui.js";

export const navigation = [
  ["tableau", "Tableau de bord"], ["candidatures", "Candidatures"], ["stages", "Stages"], ["etudiants", "Étudiants"],
  ["entreprises", "Entreprises"], ["notifications", "Notifications"],
];

export async function afficher(route, contenu) {
  vider(contenu, chargement());
  const id = route.id;
  switch (route.page) {
    case "tableau": return tableauDeBord(contenu);
    case "candidatures": return id ? candidature(Number(id), contenu) : candidatures(contenu);
    case "stages": return id ? stage(Number(id), contenu) : stages(contenu);
    case "etudiants": return id === "nouveau" ? formulaireEtudiant(null, contenu) : id ? etudiant(Number(id), contenu) : etudiants(contenu);
    case "entreprises": return id === "nouvelle" ? formulaireEntreprise(null, contenu) : id ? entreprise(Number(id), contenu) : entreprises(contenu);
    case "notifications": return notifications(contenu);
    default: location.replace("#/responsable/tableau");
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Tableau de bord

/** Répartition par statut : tableau, avec un filet de longueur proportionnelle (une seule teinte ; la valeur est écrite). */
function repartition(titre, valeurs, total) {
  const max = Math.max(1, ...Object.values(valeurs));
  return h("section", { class: "panneau" }, h("h2", {}, titre),
    h("table", { class: "tableau repartition" },
      h("caption", { class: "cache" }, titre),
      h("thead", {}, h("tr", {}, h("th", { scope: "col" }, "Statut"), h("th", { scope: "col", class: "nombre" }, "Nombre"), h("th", { scope: "col" }, h("span", { class: "cache" }, "Part")))),
      h("tbody", {}, Object.entries(valeurs).map(([statut, n]) => h("tr", { title: `${libelle(statut)} : ${n}${total ? ` sur ${total}` : ""}` },
        h("td", {}, badge(statut)), h("td", { class: "nombre" }, nombre(n)),
        h("td", { class: "barre" }, h("div", { class: "barre-piste", "aria-hidden": "true" }, n > 0 ? h("div", { class: "barre-valeur", style: `width:${(100 * n) / max}%` }) : null)))))));
}

async function tableauDeBord(contenu) {
  document.title = "Tableau de bord · INPT";
  const [k, services] = await Promise.all([lire("/responsable/tableau-de-bord"), lire("/responsable/services")]);
  const zoneRappels = h("div");
  const boutonRappels = h("button", { class: "bouton", onclick: async () => {
    const bilan = await action(boutonRappels, () => envoyer("/responsable/rappels"));
    vider(zoneRappels, message(bilan.echecs ? "attente" : "ok", "Rappels exécutés",
      `${bilan.offresConcernees} offre(s) concernée(s), ${bilan.rappelsEnvoyes} rappel(s) envoyé(s), ${bilan.dejaEnvoyes} déjà envoyé(s) auparavant, ${bilan.echecs} échec(s).`),
    bilan.details.length ? tableau([
      { titre: "Offre", valeur: d => d.titreOffre }, { titre: "Destinataire", valeur: d => d.destinataire },
      { titre: "Résultat", valeur: d => (d.resultat === "DEJA_ENVOYE" ? h("span", { class: "badge eteint" }, "Déjà envoyé") : badge(d.resultat)) },
    ], bilan.details, "Rappels envoyés") : null);
  } }, "Envoyer les rappels maintenant");

  vider(contenu,
    entetePage("Tableau de bord", `Situation au ${dateFr(new Date())}.`),
    h("div", { class: "indicateurs", role: "list" },
      h("div", { class: "indicateur", role: "listitem" }, h("div", { class: "libelle" }, "Offres actives"), h("div", { class: "valeur" }, nombre(k.offresParStatut.ACTIVE ?? 0)),
        h("div", { class: "precision" }, `sur ${nombre(k.offresTotal)} offres`)),
      h("div", { class: "indicateur", role: "listitem" }, h("div", { class: "libelle" }, "Candidatures"), h("div", { class: "valeur" }, nombre(k.candidaturesTotal)),
        h("div", { class: "precision" }, `${nombre(k.candidaturesParStatut.DEPOSEE + k.candidaturesParStatut.EN_COURS_EVALUATION)} en attente de décision`)),
      h("div", { class: "indicateur", role: "listitem" }, h("div", { class: "libelle" }, "Taux d'acceptation"),
        h("div", { class: "valeur" }, k.tauxAcceptation === null || k.tauxAcceptation === undefined ? "–" : `${String(k.tauxAcceptation).replace(".", ",")} %`),
        h("div", { class: "precision" }, "des candidatures ayant reçu une décision")),
      h("div", { class: "indicateur", role: "listitem" }, h("div", { class: "libelle" }, "Stages en cours"), h("div", { class: "valeur" }, nombre(k.stagesEnCours)),
        h("div", { class: "precision" }, `${nombre(k.stagesParStatut.PLANIFIE)} planifié(s)`))),
    h("div", { class: "grille-egale" },
      repartition("Candidatures par statut", k.candidaturesParStatut, k.candidaturesTotal),
      repartition("Stages par statut", k.stagesParStatut),
      repartition("Offres par statut", k.offresParStatut, k.offresTotal)),
    h("div", { class: "grille-2" },
      h("section", { class: "panneau" }, h("div", { class: "panneau-titre" }, h("h2", {}, "Rappels de date limite"), h("span", { class: "discret" }, "Envoi automatique chaque jour à 8 h")),
        h("p", {}, "Prévient les étudiants actifs et compatibles qui n'ont pas candidaté aux offres fermant dans les trois jours. Un rappel n'est jamais envoyé deux fois."),
        boutonRappels, zoneRappels),
      h("section", { class: "panneau" }, h("h2", {}, "État des services"),
        tableau([{ titre: "Service", valeur: s => s.service }, { titre: "État", valeur: s => badge(s.etat) }], services, "État des services"))));
}

// ---------------------------------------------------------------------------------------------------------------
// Candidatures

async function candidatures(contenu) {
  document.title = "Candidatures · INPT";
  const filtre = select([["", "Tous les statuts"], ["DEPOSEE", "Déposées"], ["EN_COURS_EVALUATION", "En évaluation"], ["ACCEPTEE", "Acceptées"], ["REFUSEE", "Refusées"], ["ANNULEE", "Annulées"]], "");
  const zone = h("div");
  const charger = async () => {
    vider(zone, chargement());
    const liste = await lire(`/responsable/candidatures${filtre.value ? `?statut=${filtre.value}` : ""}`);
    vider(zone, liste.length ? tableau([
      { titre: "Étudiant", valeur: c => [h("a", { class: "lien-ligne", href: `#/responsable/candidatures/${c.id}` }, c.etudiant ? `${c.etudiant.prenom} ${c.etudiant.nom}` : `n° ${c.etudiantId}`),
        c.etudiant ? h("div", { class: "secondaire" }, `${c.etudiant.filiere} · ${c.etudiant.niveau}`) : null] },
      { titre: "Offre", valeur: c => [c.titreOffre, h("div", { class: "secondaire" }, c.entreprise ?? "")] },
      { titre: "Déposée le", valeur: c => dateCourte(c.creeLe) },
      { titre: "Statut", valeur: c => badge(c.statut) },
    ], liste, "Candidatures") : vide("Aucune candidature", "Aucune candidature avec ce statut."));
  };
  filtre.addEventListener("change", charger);
  vider(contenu, entetePage("Candidatures", "Toutes les candidatures enregistrées, avec les coordonnées complètes des étudiants."),
    h("div", { class: "barre-filtres" }, h("div", { class: "champ" }, h("label", {}, "Statut", filtre))), zone);
  await charger();
}

async function candidature(id, contenu) {
  const c = await lire(`/responsable/candidatures/${id}`);
  const e = c.etudiant;
  document.title = `Candidature n° ${id} · INPT`;
  const echecSuivi = c.statut === "ACCEPTEE" && (!c.stage || c.etapes.some(x => ["CREATION_STAGE", "MISE_A_JOUR_ETUDIANT"].includes(x.etape) && x.resultat === "ECHEC"));
  const bouton = h("button", { class: "bouton", onclick: async () => {
    await action(bouton, () => envoyer(`/responsable/candidatures/${id}/suivi/reprendre`), "Suivi relancé.");
    afficher({ page: "candidatures", id: String(id) }, contenu);
  } }, "Relancer le suivi");
  vider(contenu,
    entetePage(e ? `${e.prenom} ${e.nom}` : `Candidature n° ${id}`, `« ${c.titreOffre} » · ${c.entreprise ?? ""}`, h("a", { href: "#/responsable/candidatures" }, "Candidatures"), badge(c.statut)),
    echecSuivi ? message("attente", "Suivi du stage incomplet", "Une étape de suivi a échoué après l'acceptation (création du stage ou statut de l'étudiant). Relancez-la une fois le service rétabli.") : null,
    echecSuivi ? h("div", { style: "margin:-6px 0 18px" }, bouton) : null,
    panneauProcessus(c.etapes, c.statut),
    h("div", { class: "grille-2" },
      h("div", {}, panneauEtapes(c.etapes), h("section", { class: "panneau" }, h("h2", {}, "Lettre de motivation"), lettre(c.lettreMotivation))),
      h("div", {},
        h("section", { class: "panneau" }, h("h2", {}, "Étudiant"),
          e ? h("dl", { class: "fiche" },
            h("dt", {}, "Matricule"), h("dd", { class: "code" }, e.matricule), h("dt", {}, "Filière"), h("dd", {}, `${e.filiere} · ${e.niveau}`),
            h("dt", {}, "Courriel"), h("dd", {}, e.email), h("dt", {}, "Téléphone"), h("dd", {}, e.telephone), h("dt", {}, "Statut"), h("dd", {}, badge(e.statut)))
            : message("attente", null, "Profil momentanément indisponible."),
          e ? h("p", { style: "margin-top:12px" }, h("a", { href: `#/responsable/etudiants/${e.id}` }, "Fiche de l'étudiant")) : null),
        c.stage ? h("section", { class: "panneau" }, h("h2", {}, "Stage"), h("p", {}, badge(c.stage.statut), ` du ${dateFr(c.stage.dateDebut)} au ${dateFr(c.stage.dateFin)}`),
          h("a", { href: `#/responsable/stages/${c.stage.id}` }, "Ouvrir le dossier de stage")) : null,
        h("section", { class: "panneau" }, h("h2", {}, "Historique"), historiqueStatuts(c.historique)))));
}

// ---------------------------------------------------------------------------------------------------------------
// Stages

async function stages(contenu) {
  document.title = "Stages · INPT";
  const filtre = select([["", "Tous"], ["PLANIFIE", "Planifiés"], ["EN_COURS", "En cours"], ["TERMINE", "Terminés"], ["INTERROMPU", "Interrompus"]], "");
  const zone = h("div");
  const charger = async () => {
    vider(zone, chargement());
    const liste = await lire(`/responsable/stages${filtre.value ? `?statut=${filtre.value}` : ""}`);
    vider(zone, liste.length ? tableau([
      { titre: "Stagiaire", valeur: s => h("a", { class: "lien-ligne", href: `#/responsable/stages/${s.id}` }, s.etudiant ? `${s.etudiant.prenom} ${s.etudiant.nom}` : `n° ${s.etudiantId}`) },
      { titre: "Stage", valeur: s => [s.titreOffre, h("div", { class: "secondaire" }, s.entreprise ?? "")] },
      { titre: "Période", valeur: s => `${dateCourte(s.dateDebut)} – ${dateCourte(s.dateFin)}` },
      { titre: "Statut", valeur: s => badge(s.statut) },
    ], liste, "Stages") : vide("Aucun stage", "Aucun stage avec ce statut."));
  };
  filtre.addEventListener("change", charger);
  vider(contenu, entetePage("Stages", "Dossiers créés après acceptation : statut, comptes rendus et évaluations."),
    h("div", { class: "barre-filtres" }, h("div", { class: "champ" }, h("label", {}, "Statut", filtre))), zone);
  await charger();
}

const TRANSITIONS_STAGE = { PLANIFIE: ["EN_COURS", "INTERROMPU"], EN_COURS: ["TERMINE", "INTERROMPU"], TERMINE: [], INTERROMPU: [] };

async function stage(id, contenu) {
  const s = await lire(`/responsable/stages/${id}`);
  document.title = `Stage n° ${id} · INPT`;
  const recharger = () => afficher({ page: "stages", id: String(id) }, contenu);
  const suivant = TRANSITIONS_STAGE[s.statut];
  const nouveauStatut = select(suivant.map(x => [x, libelle(x)]), suivant[0]);
  const commentaireStatut = h("input", { placeholder: "Motif ou précision (facultatif)" });
  const boutonStatut = h("button", { class: "bouton secondaire", type: "submit" }, "Changer le statut");
  const formStatut = suivant.length ? h("form", { class: "barre-filtres", style: "margin:0", onsubmit: async ev => {
    ev.preventDefault();
    await action(boutonStatut, () => modifier(`/responsable/stages/${id}/statut`, { statut: nouveauStatut.value, commentaire: commentaireStatut.value.trim() || null }), "Statut mis à jour.");
    recharger();
  } }, h("div", { class: "champ" }, h("label", {}, "Nouveau statut", nouveauStatut)), h("div", { class: "champ large" }, h("label", {}, "Commentaire", commentaireStatut)), boutonStatut)
    : h("p", { class: "discret" }, "Statut définitif.");

  const suiviPossible = ["EN_COURS", "TERMINE"].includes(s.statut);
  const periode = h("input", { placeholder: "Semaines 5 à 8", maxlength: "60" });
  const contenuCr = h("textarea", { rows: "4" });
  const auteurCr = h("input", { value: s.encadrantPedagogique ?? "" });
  const retourCr = h("div");
  const boutonCr = h("button", { class: "bouton", type: "submit" }, "Enregistrer le compte rendu");
  const formCr = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider([[periode, obligatoire("La période")], [contenuCr, obligatoire("Le contenu")], [auteurCr, obligatoire("L'auteur")]])) return;
    try {
      await action(boutonCr, () => envoyer(`/responsable/stages/${id}/comptes-rendus`, { periode: periode.value.trim(), contenu: contenuCr.value.trim(), auteur: auteurCr.value.trim() }), "Compte rendu enregistré.");
      recharger();
    } catch (err) { vider(retourCr, message("ko", null, err.message, err.details)); }
  } }, h("div", { class: "ligne" }, champ("Période", periode), champ("Auteur", auteurCr)), champ("Contenu", contenuCr), h("div", { class: "actions" }, boutonCr));

  const noteSaisie = h("input", { type: "number", min: "0", max: "20", step: "0.25" });
  const commentaireEval = h("textarea", { rows: "3", maxlength: "1000" });
  const evaluateur = h("input", { value: s.encadrantPedagogique ?? "" });
  const retourEval = h("div");
  const boutonEval = h("button", { class: "bouton", type: "submit" }, "Enregistrer l'évaluation");
  const formEval = h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider([[noteSaisie, v => (v === "" || Number(v) < 0 || Number(v) > 20 ? "La note doit être comprise entre 0 et 20." : null)], [evaluateur, obligatoire("L'évaluateur")]])) return;
    try {
      await action(boutonEval, () => envoyer(`/responsable/stages/${id}/evaluations`, { note: Number(noteSaisie.value), commentaire: commentaireEval.value.trim() || null, evaluateur: evaluateur.value.trim() }), "Évaluation enregistrée.");
      recharger();
    } catch (err) { vider(retourEval, message("ko", null, err.message, err.details)); }
  } }, h("div", { class: "ligne" }, champ("Note sur 20", noteSaisie), champ("Évaluateur", evaluateur)), champ("Commentaire", commentaireEval), h("div", { class: "actions" }, boutonEval));

  const e = s.etudiant;
  vider(contenu,
    entetePage(e ? `${e.prenom} ${e.nom}` : `Stage n° ${id}`, `${s.titreOffre} · ${s.entreprise ?? ""}`, h("a", { href: "#/responsable/stages" }, "Stages"), badge(s.statut)),
    h("section", { class: "panneau" }, h("h2", {}, "Statut du stage"), formStatut),
    blocStage(s),
    suiviPossible ? h("div", { class: "grille-egale" },
      h("section", { class: "panneau" }, h("h2", {}, "Ajouter un compte rendu"), retourCr, formCr),
      h("section", { class: "panneau" }, h("h2", {}, "Ajouter une évaluation"), retourEval, formEval))
      : message("info", null, "Comptes rendus et évaluations s'ajoutent quand le stage est en cours ou terminé."));
}

// ---------------------------------------------------------------------------------------------------------------
// Étudiants

async function etudiants(contenu) {
  document.title = "Étudiants · INPT";
  const recherche = h("input", { type: "search", placeholder: "Nom, prénom ou matricule" });
  const filiere = select([["", "Toutes"], ...FILIERES.map(f => [f, f])], "");
  const statut = select([["", "Tous"], ...["ACTIF", "SUSPENDU", "EN_STAGE", "DIPLOME"].map(s => [s, libelle(s)])], "");
  const zone = h("div");
  const charger = async () => {
    vider(zone, chargement());
    const p = new URLSearchParams();
    if (recherche.value.trim()) p.set("recherche", recherche.value.trim());
    if (filiere.value) p.set("filiere", filiere.value);
    if (statut.value) p.set("statut", statut.value);
    const liste = await lire(`/responsable/etudiants${p.size ? `?${p}` : ""}`);
    vider(zone, liste.length ? tableau([
      { titre: "Étudiant", valeur: x => [h("a", { class: "lien-ligne", href: `#/responsable/etudiants/${x.id}` }, `${x.nom} ${x.prenom}`), h("div", { class: "secondaire code" }, x.matricule)] },
      { titre: "Filière", valeur: x => `${x.filiere} · ${x.niveau}` }, { titre: "Ville", valeur: x => x.ville }, { titre: "Statut", valeur: x => badge(x.statut) },
    ], liste, "Étudiants") : vide("Aucun étudiant", "Aucun étudiant ne correspond à ces critères."));
  };
  vider(contenu, entetePage("Étudiants", "Référentiel des étudiants (service S1).", null, h("a", { class: "bouton", href: "#/responsable/etudiants/nouveau" }, "Ajouter un étudiant")),
    h("form", { class: "barre-filtres", onsubmit: ev => { ev.preventDefault(); charger(); } },
      h("div", { class: "champ large" }, h("label", {}, "Recherche", recherche)), h("div", { class: "champ" }, h("label", {}, "Filière", filiere)),
      h("div", { class: "champ" }, h("label", {}, "Statut", statut)), h("button", { class: "bouton secondaire", type: "submit" }, "Rechercher")),
    zone);
  await charger();
}

const TRANSITIONS_ETUDIANT = { ACTIF: ["SUSPENDU", "EN_STAGE", "DIPLOME"], SUSPENDU: ["ACTIF"], EN_STAGE: ["ACTIF", "DIPLOME"], DIPLOME: [] };

async function etudiant(id, contenu) {
  const e = await lire(`/responsable/etudiants/${id}`);
  document.title = `${e.prenom} ${e.nom} · INPT`;
  const recharger = () => afficher({ page: "etudiants", id: String(id) }, contenu);
  const suivant = TRANSITIONS_ETUDIANT[e.statut];
  const nouveau = select(suivant.map(x => [x, libelle(x)]), suivant[0]);
  const boutonStatut = h("button", { class: "bouton secondaire", type: "submit" }, "Changer le statut");
  const boutonSuppr = h("button", { class: "bouton danger", onclick: async () => {
    if (!(await confirmer(`Supprimer ${e.prenom} ${e.nom} ?`, "Impossible si l'étudiant a des candidatures ou est en stage.", "Supprimer"))) return;
    await action(boutonSuppr, () => supprimer(`/responsable/etudiants/${id}`), "Étudiant supprimé.");
    location.hash = "#/responsable/etudiants";
  } }, "Supprimer");
  vider(contenu,
    entetePage(`${e.prenom} ${e.nom}`, `${e.matricule} · ${e.filiere} · ${e.niveau}`, h("a", { href: "#/responsable/etudiants" }, "Étudiants"), [badge(e.statut), boutonSuppr]),
    h("div", { class: "grille-2" },
      h("div", {},
        h("section", { class: "panneau" }, h("h2", {}, "Modifier la fiche"), formulaireEtudiantContenu(e, () => recharger())),
        h("section", { class: "panneau" }, h("h2", {}, "Candidatures"),
          e.candidatures.length ? tableau([
            { titre: "Offre", valeur: c => h("a", { href: `#/responsable/candidatures/${c.id}` }, c.titreOffre) },
            { titre: "Déposée le", valeur: c => dateCourte(c.creeLe) }, { titre: "Statut", valeur: c => badge(c.statut) },
          ], e.candidatures, "Candidatures de l'étudiant") : h("p", { class: "discret" }, "Aucune candidature."))),
      h("div", {},
        h("section", { class: "panneau" }, h("h2", {}, "Statut"),
          suivant.length ? h("form", { class: "barre-filtres", style: "margin:0", onsubmit: async ev => {
            ev.preventDefault();
            await action(boutonStatut, () => modifier(`/responsable/etudiants/${id}/statut`, { statut: nouveau.value }), "Statut mis à jour.");
            recharger();
          } }, h("div", { class: "champ" }, h("label", {}, "Nouveau statut", nouveau)), boutonStatut) : h("p", { class: "discret" }, "Statut définitif.")),
        h("section", { class: "panneau" }, h("h2", {}, "Stages"),
          e.stages.length ? e.stages.map(s => h("p", {}, h("a", { href: `#/responsable/stages/${s.id}` }, s.titreOffre), " ", badge(s.statut)))
            : h("p", { class: "discret" }, "Aucun stage.")))));
}

function formulaireEtudiantContenu(e, apres) {
  const s = {
    matricule: h("input", { value: e?.matricule ?? "", placeholder: "INPT-2026-0001" }), nom: h("input", { value: e?.nom ?? "" }), prenom: h("input", { value: e?.prenom ?? "" }),
    filiere: select(FILIERES.map(f => [f, f]), e?.filiere ?? "AMOA"), niveau: select(NIVEAUX.map(n => [n, n]), e?.niveau ?? "1A"),
    email: h("input", { type: "email", value: e?.email ?? "" }), telephone: h("input", { value: e?.telephone ?? "" }), ville: h("input", { value: e?.ville ?? "" }),
    competences: h("input", { value: e?.competences?.join(", ") ?? "" }),
  };
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, e ? "Enregistrer" : "Ajouter l'étudiant");
  return h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    if (!valider([
      [s.matricule, v => (/^INPT-\d{4}-\d{4}$/.test(v) ? null : "Le matricule suit le format INPT-AAAA-NNNN.")],
      [s.nom, obligatoire("Le nom")], [s.prenom, obligatoire("Le prénom")],
      [s.email, v => (/^\S+@\S+\.\S+$/.test(v) ? null : "Adresse électronique invalide.")],
      [s.telephone, v => (/^0[5-7]\d{8}$/.test(v) ? null : "10 chiffres, commençant par 05, 06 ou 07.")], [s.ville, obligatoire("La ville")],
    ])) return;
    const corps = { matricule: s.matricule.value.trim(), nom: s.nom.value.trim(), prenom: s.prenom.value.trim(), filiere: s.filiere.value, niveau: s.niveau.value,
      email: s.email.value.trim(), telephone: s.telephone.value.trim(), ville: s.ville.value.trim(),
      competences: s.competences.value.split(",").map(x => x.trim()).filter(Boolean), statut: e?.statut ?? null };
    try {
      const r = await action(bouton, () => (e ? remplacer(`/responsable/etudiants/${e.id}`, corps) : envoyer("/responsable/etudiants", corps)), e ? "Fiche enregistrée." : "Étudiant ajouté.");
      apres(r);
    } catch (err) { vider(retour, message("ko", "Enregistrement refusé", err.message, err.details)); }
  } }, retour,
  h("div", { class: "ligne" }, champ("Matricule", s.matricule), champ("Nom", s.nom), champ("Prénom", s.prenom)),
  h("div", { class: "ligne" }, champ("Filière", s.filiere), champ("Niveau", s.niveau), champ("Ville", s.ville)),
  h("div", { class: "ligne" }, champ("Courriel", s.email), champ("Téléphone", s.telephone)),
  champ("Compétences", s.competences, "Séparées par des virgules : elles servent au score de compatibilité."),
  h("div", { class: "actions" }, bouton));
}

function formulaireEtudiant(_, contenu) {
  document.title = "Ajouter un étudiant · INPT";
  vider(contenu, entetePage("Ajouter un étudiant", null, h("a", { href: "#/responsable/etudiants" }, "Étudiants")),
    h("section", { class: "panneau", style: "max-width:860px" }, formulaireEtudiantContenu(null, r => { location.hash = `#/responsable/etudiants/${r.id}`; })),
    message("info", null, "Le compte de connexion de l'étudiant n'est pas créé automatiquement (limite connue de la démonstration)."));
}

// ---------------------------------------------------------------------------------------------------------------
// Entreprises

async function entreprises(contenu) {
  document.title = "Entreprises · INPT";
  const liste = await lire("/responsable/entreprises");
  vider(contenu, entetePage("Entreprises partenaires", "Référentiel des entreprises (service S2).", null, h("a", { class: "bouton", href: "#/responsable/entreprises/nouvelle" }, "Ajouter une entreprise")),
    h("section", { class: "panneau" }, tableau([
      { titre: "Entreprise", valeur: x => [h("a", { class: "lien-ligne", href: `#/responsable/entreprises/${x.id}` }, x.raisonSociale), h("div", { class: "secondaire" }, x.secteur)] },
      { titre: "Ville", valeur: x => x.ville }, { titre: "Contact", valeur: x => (x.responsable ? [x.responsable.nom, h("div", { class: "secondaire" }, x.responsable.fonction)] : "–") },
      { titre: "ICE", valeur: x => h("span", { class: "code" }, x.ice) }, { titre: "Statut", valeur: x => badge(x.statut) },
    ], liste, "Entreprises")));
}

function formulaireEntrepriseContenu(e, apres) {
  const r = e?.responsable ?? {};
  const s = {
    raisonSociale: h("input", { value: e?.raisonSociale ?? "" }), ice: h("input", { value: e?.ice ?? "", maxlength: "15" }), secteur: h("input", { value: e?.secteur ?? "" }),
    adresse: h("input", { value: e?.adresse ?? "" }), ville: h("input", { value: e?.ville ?? "" }), email: h("input", { type: "email", value: e?.email ?? "" }),
    telephone: h("input", { value: e?.telephone ?? "" }), contactNom: h("input", { value: r.nom ?? "" }), contactFonction: h("input", { value: r.fonction ?? "" }),
    contactEmail: h("input", { type: "email", value: r.email ?? "" }), contactTelephone: h("input", { value: r.telephone ?? "" }),
    statut: select([["ACTIVE", "Active"], ["INACTIVE", "Inactive"]], e?.statut ?? "ACTIVE"),
  };
  const retour = h("div");
  const bouton = h("button", { class: "bouton", type: "submit" }, e ? "Enregistrer" : "Ajouter l'entreprise");
  return h("form", { class: "formulaire", novalidate: true, onsubmit: async ev => {
    ev.preventDefault();
    const regles = Object.entries(s).filter(([k]) => k !== "statut").map(([, x]) => [x, v => (v ? null : "Ce champ est obligatoire.")]);
    regles.push([s.ice, v => (/^\d{15}$/.test(v) ? null : "L'ICE compte exactement 15 chiffres.")]);
    if (!valider(regles)) return;
    const v = k => s[k].value.trim();
    const corps = { raisonSociale: v("raisonSociale"), ice: v("ice"), secteur: v("secteur"), adresse: v("adresse"), ville: v("ville"), email: v("email"), telephone: v("telephone"),
      responsable: { nom: v("contactNom"), fonction: v("contactFonction"), email: v("contactEmail"), telephone: v("contactTelephone") }, statut: s.statut.value };
    try {
      const res = await action(bouton, () => (e ? remplacer(`/responsable/entreprises/${e.id}`, corps) : envoyer("/responsable/entreprises", corps)), e ? "Entreprise enregistrée." : "Entreprise ajoutée.");
      apres(res);
    } catch (err) { vider(retour, message("ko", "Enregistrement refusé", err.message, err.details)); }
  } }, retour,
  h("div", { class: "ligne" }, champ("Raison sociale", s.raisonSociale), champ("ICE", s.ice, "15 chiffres."), champ("Statut", s.statut)),
  h("div", { class: "ligne" }, champ("Secteur", s.secteur), champ("Adresse", s.adresse), champ("Ville", s.ville)),
  h("div", { class: "ligne" }, champ("Courriel", s.email), champ("Téléphone", s.telephone)),
  h("h3", { style: "margin:8px 0 0" }, "Responsable de contact"),
  h("div", { class: "ligne" }, champ("Nom", s.contactNom), champ("Fonction", s.contactFonction)),
  h("div", { class: "ligne" }, champ("Courriel", s.contactEmail), champ("Téléphone", s.contactTelephone)),
  h("div", { class: "actions" }, bouton));
}

function formulaireEntreprise(_, contenu) {
  document.title = "Ajouter une entreprise · INPT";
  vider(contenu, entetePage("Ajouter une entreprise", null, h("a", { href: "#/responsable/entreprises" }, "Entreprises")),
    h("section", { class: "panneau", style: "max-width:900px" }, formulaireEntrepriseContenu(null, r => { location.hash = `#/responsable/entreprises/${r.id}`; })));
}

async function entreprise(id, contenu) {
  const e = await lire(`/responsable/entreprises/${id}`);
  document.title = `${e.raisonSociale} · INPT`;
  const boutonSuppr = h("button", { class: "bouton danger", onclick: async () => {
    if (!(await confirmer(`Supprimer ${e.raisonSociale} ?`, "Impossible si l'entreprise a publié des offres : passez-la plutôt en « Inactive ».", "Supprimer"))) return;
    await action(boutonSuppr, () => supprimer(`/responsable/entreprises/${id}`), "Entreprise supprimée.");
    location.hash = "#/responsable/entreprises";
  } }, "Supprimer");
  vider(contenu, entetePage(e.raisonSociale, `${e.secteur} · ${e.ville}`, h("a", { href: "#/responsable/entreprises" }, "Entreprises"), [badge(e.statut), boutonSuppr]),
    h("div", { class: "grille-2" },
      h("section", { class: "panneau" }, h("h2", {}, "Fiche"), formulaireEntrepriseContenu(e, () => afficher({ page: "entreprises", id: String(id) }, contenu))),
      h("section", { class: "panneau" }, h("h2", {}, "Offres publiées"),
        e.offres.length ? tableau([{ titre: "Offre", valeur: o => o.titre }, { titre: "Date limite", valeur: o => dateCourte(o.dateLimite) }, { titre: "Statut", valeur: o => badge(o.statut) }], e.offres, "Offres")
          : h("p", { class: "discret" }, "Aucune offre."))));
}

// ---------------------------------------------------------------------------------------------------------------
// Notifications

async function notifications(contenu) {
  document.title = "Notifications · INPT";
  const destinataire = h("input", { type: "search", placeholder: "adresse@inpt.ac.ma" });
  const type = select([["", "Tous les types"], ...Object.entries(TYPES_NOTIFICATION)], "");
  const zone = h("div");
  const charger = async () => {
    vider(zone, chargement());
    const p = new URLSearchParams();
    if (destinataire.value.trim()) p.set("destinataire", destinataire.value.trim());
    if (type.value) p.set("type", type.value);
    const liste = await lire(`/responsable/notifications${p.size ? `?${p}` : ""}`);
    vider(zone, liste.length ? h("section", { class: "panneau" }, liste.map(n => {
      const cadreCourriel = h("div");
      const repli = h("details", { class: "repli" },
        h("summary", {}, h("span", { class: "sujet" }, n.sujet), h("span", { class: "discret" }, `${n.destinataire} · ${dateHeure(n.dateEnvoi)}`), badge(n.statut)),
        n.erreur ? message("ko", null, n.erreur) : null, cadreCourriel);
      repli.addEventListener("toggle", () => {
        if (repli.open && !cadreCourriel.firstChild) cadreCourriel.append(h("iframe", { class: "courriel", sandbox: "", title: n.sujet, srcdoc: n.corps }));
      });
      return repli;
    })) : vide("Aucune notification", "Aucun courriel ne correspond à ces critères."));
  };
  vider(contenu, entetePage("Notifications", "Courriels envoyés par la plateforme (service S5) : statut d'envoi et contenu."),
    h("form", { class: "barre-filtres", onsubmit: ev => { ev.preventDefault(); charger(); } },
      h("div", { class: "champ large" }, h("label", {}, "Destinataire", destinataire)), h("div", { class: "champ" }, h("label", {}, "Type", type)),
      h("button", { class: "bouton secondaire", type: "submit" }, "Rechercher")),
    zone);
  await charger();
}

