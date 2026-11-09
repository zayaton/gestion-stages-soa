// Appels à l'API du portail. Le jeton de session vit dans sessionStorage (fermé avec l'onglet)
// et part dans l'en-tête Authorization ; une session expirée ramène à la page de connexion.

const CLE = "inpt.session";

export const session = {
  lire() {
    try { return JSON.parse(sessionStorage.getItem(CLE)); } catch { return null; }
  },
  enregistrer(s) { sessionStorage.setItem(CLE, JSON.stringify(s)); },
  effacer() { sessionStorage.removeItem(CLE); },
};

/** Erreur renvoyée par le portail : même forme que le corps JSON {code, message, details}. */
export class ErreurApi extends Error {
  constructor(statut, corps) {
    super(corps?.message || "Le serveur n'a pas pu traiter la demande.");
    this.statut = statut;
    this.code = corps?.code || "ERREUR";
    this.details = Array.isArray(corps?.details) ? corps.details : [];
  }
}

let surExpiration = () => {};
export function quandSessionExpire(rappel) { surExpiration = rappel; }

export async function api(methode, chemin, corps) {
  const s = session.lire();
  const entetes = { Accept: "application/json" };
  if (corps !== undefined) entetes["Content-Type"] = "application/json";
  if (s?.jeton) entetes.Authorization = `Bearer ${s.jeton}`;
  let reponse;
  try {
    reponse = await fetch(`/api${chemin}`, { method: methode, headers: entetes, body: corps === undefined ? undefined : JSON.stringify(corps) });
  } catch {
    throw new ErreurApi(0, { code: "RESEAU", message: "Le portail ne répond pas. Vérifiez qu'il est démarré, puis réessayez." });
  }
  const texte = await reponse.text();
  let json = null;
  try { json = texte ? JSON.parse(texte) : null; } catch { json = null; }
  if (reponse.status === 401 && chemin !== "/auth/connexion") {
    session.effacer();
    surExpiration();
  }
  if (!reponse.ok) throw new ErreurApi(reponse.status, json);
  return { statut: reponse.status, donnees: json };
}

export const lire = async chemin => (await api("GET", chemin)).donnees;
export const envoyer = async (chemin, corps) => (await api("POST", chemin, corps ?? {})).donnees;
export const remplacer = async (chemin, corps) => (await api("PUT", chemin, corps)).donnees;
export const modifier = async (chemin, corps) => (await api("PATCH", chemin, corps)).donnees;
export const supprimer = async chemin => (await api("DELETE", chemin)).donnees;
