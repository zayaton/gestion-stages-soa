package ma.inpt.stages.candidatures;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.candidatures.Modeles.Candidature;
import ma.inpt.stages.candidatures.Modeles.Etape;
import ma.inpt.stages.candidatures.Modeles.Historique;
import ma.inpt.stages.candidatures.Modeles.NouvelleCandidature;
import ma.inpt.stages.candidatures.Modeles.Soumission;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;

/** Requêtes JDBC du service S4, sur le seul schéma soa_candidatures. */
public final class CandidatureDaoJdbc implements CandidatureDao {

    private static final String COLONNES = "id, soumission_id, etudiant_id, offre_id, entreprise_id, lettre_motivation, score_competences, statut, cree_le, modifie_le";
    private static final String COLONNES_SOUMISSION = "s.id, s.etudiant_id, s.offre_id, s.lettre_motivation, s.statut, s.regle_rejetee, s.motif_rejet, "
            + "s.bonita_case_id, s.cree_le, (SELECT c.id FROM candidature c WHERE c.soumission_id = s.id) AS candidature_id";

    private final FournisseurConnexion source;

    public CandidatureDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    // Soumissions

    @Override
    public Soumission creerSoumission(long etudiantId, long offreId, String lettre) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("INSERT INTO soumission (etudiant_id, offre_id, lettre_motivation) VALUES (?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, etudiantId);
            p.setLong(2, offreId);
            p.setString(3, lettre);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                r.next();
                return trouverSoumission(r.getLong(1)).orElseThrow();
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public Optional<Soumission> trouverSoumission(long id) {
        List<Soumission> s = soumissions("WHERE s.id = ?", id);
        return s.isEmpty() ? Optional.empty() : Optional.of(s.get(0));
    }

    @Override
    public List<Soumission> soumissionsEtudiant(long etudiantId) {
        return soumissions("WHERE s.etudiant_id = ? ORDER BY s.cree_le DESC, s.id DESC", etudiantId);
    }

    private List<Soumission> soumissions(String condition, long valeur) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT " + COLONNES_SOUMISSION + " FROM soumission s " + condition)) {
            p.setLong(1, valeur);
            List<Soumission> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long id = r.getLong("id");
                    resultat.add(new Soumission(id, r.getLong("etudiant_id"), r.getLong("offre_id"), r.getString("lettre_motivation"),
                            r.getString("statut"), r.getString("regle_rejetee"), r.getString("motif_rejet"), longOuNull(r, "bonita_case_id"),
                            longOuNull(r, "candidature_id"), r.getObject("cree_le", LocalDateTime.class), etapes(c, id)));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void terminerSoumission(long id, String statut, String regle, String motif) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("UPDATE soumission SET statut = ?, regle_rejetee = ?, motif_rejet = ? WHERE id = ?")) {
            p.setString(1, statut);
            p.setString(2, regle);
            p.setString(3, motif == null ? null : motif.substring(0, Math.min(500, motif.length())));
            p.setLong(4, id);
            p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void definirCasBonita(long soumissionId, long casId) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("UPDATE soumission SET bonita_case_id = ? WHERE id = ?")) {
            p.setLong(1, casId);
            p.setLong(2, soumissionId);
            p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void ajouterEtape(long soumissionId, String etape, String resultat, String detail, String acteur) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("INSERT INTO etape_processus (soumission_id, etape, resultat, detail, acteur) VALUES (?,?,?,?,?)")) {
            p.setLong(1, soumissionId);
            p.setString(2, etape);
            p.setString(3, resultat);
            p.setString(4, detail == null ? null : detail.substring(0, Math.min(500, detail.length())));
            p.setString(5, acteur);
            p.executeUpdate();
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public List<Etape> etapes(long soumissionId) {
        try (Connection c = source.connexion()) {
            return etapes(c, soumissionId);
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static List<Etape> etapes(Connection c, long soumissionId) throws SQLException {
        List<Etape> resultat = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT etape, resultat, detail, date_etape, acteur FROM etape_processus WHERE soumission_id = ? ORDER BY date_etape, id")) {
            p.setLong(1, soumissionId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(new Etape(r.getString(1), r.getString(2), r.getString(3), r.getObject(4, LocalDateTime.class), r.getString(5)));
                }
            }
        }
        return resultat;
    }

    // Candidatures

    @Override
    public Enregistrement enregistrer(NouvelleCandidature n, int limiteActives, String acteur) {
        String verrou = "soa-candidatures-etudiant-" + n.etudiantId();
        try (Connection c = source.connexion()) {
            if (!verrouiller(c, verrou)) {
                throw ErreurMetier.indisponible("BASE_OCCUPEE", "La base de données est momentanément occupée : réessayez.");
            }
            try {
                c.setAutoCommit(false);
                try {
                    Enregistrement e = enregistrerSousVerrou(c, n, limiteActives, acteur);
                    c.commit();
                    return e;
                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            } finally {
                deverrouiller(c, verrou);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Filet de sécurité : l'index unique uq_candidature_r5 a refusé un doublon.
            return new Enregistrement(null, "R5", candidatureNonAnnulee(n.etudiantId(), n.offreId()).map(Candidature::id).orElse(null));
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private Enregistrement enregistrerSousVerrou(Connection c, NouvelleCandidature n, int limite, String acteur) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id FROM candidature WHERE etudiant_id = ? AND offre_id = ? AND statut <> 'ANNULEE'")) {
            p.setLong(1, n.etudiantId());
            p.setLong(2, n.offreId());
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    return new Enregistrement(null, "R5", r.getLong(1));
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM candidature WHERE etudiant_id = ? AND statut IN ('DEPOSEE','EN_COURS_EVALUATION')")) {
            p.setLong(1, n.etudiantId());
            try (ResultSet r = p.executeQuery()) {
                r.next();
                if (r.getInt(1) >= limite) {
                    return new Enregistrement(null, "R6", null);
                }
            }
        }
        long id;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO candidature (soumission_id, etudiant_id, offre_id, entreprise_id, "
                + "lettre_motivation, score_competences) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            if (n.soumissionId() == null) {
                p.setNull(1, Types.BIGINT);
            } else {
                p.setLong(1, n.soumissionId());
            }
            p.setLong(2, n.etudiantId());
            p.setLong(3, n.offreId());
            p.setLong(4, n.entrepriseId());
            p.setString(5, n.lettreMotivation());
            if (n.scoreCompetences() == null) {
                p.setNull(6, Types.TINYINT);
            } else {
                p.setInt(6, n.scoreCompetences());
            }
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                r.next();
                id = r.getLong(1);
            }
        }
        historiser(c, id, null, "DEPOSEE", acteur, "Candidature enregistrée");
        return new Enregistrement(lire(c, id).orElseThrow(), null, null);
    }

    private static boolean verrouiller(Connection c, String nom) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT GET_LOCK(?, 5)")) {
            p.setString(1, nom);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getInt(1) == 1;
            }
        }
    }

    private static void deverrouiller(Connection c, String nom) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            p.setString(1, nom);
            p.executeQuery().close();
        }
    }

    @Override
    public Optional<Candidature> trouver(long id) {
        try (Connection c = source.connexion()) {
            return lire(c, id);
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static Optional<Candidature> lire(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT " + COLONNES + " FROM candidature WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(candidature(r)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Candidature> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM candidature WHERE 1=1");
        List<Object> valeurs = new ArrayList<>();
        if (f.statut() != null) {
            sql.append(" AND statut = ?");
            valeurs.add(f.statut());
        }
        if (f.etudiantId() != null) {
            sql.append(" AND etudiant_id = ?");
            valeurs.add(f.etudiantId());
        }
        if (f.offreId() != null) {
            sql.append(" AND offre_id = ?");
            valeurs.add(f.offreId());
        }
        if (f.entrepriseId() != null) {
            sql.append(" AND entreprise_id = ?");
            valeurs.add(f.entrepriseId());
        }
        sql.append(" ORDER BY cree_le DESC, id DESC");
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setObject(i + 1, valeurs.get(i));
            }
            List<Candidature> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(candidature(r));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public Optional<Candidature> candidatureNonAnnulee(long etudiantId, long offreId) {
        return lister(new Filtre(null, etudiantId, offreId, null)).stream().filter(c -> !"ANNULEE".equals(c.statut())).findFirst();
    }

    @Override
    public int compterActives(long etudiantId) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM candidature WHERE etudiant_id = ? AND statut IN ('DEPOSEE','EN_COURS_EVALUATION')")) {
            p.setLong(1, etudiantId);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public boolean changerStatut(long id, String attendu, String nouveau, String acteur, String commentaire) {
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("UPDATE candidature SET statut = ? WHERE id = ? AND statut = ?")) {
                p.setString(1, nouveau);
                p.setLong(2, id);
                p.setString(3, attendu);
                if (p.executeUpdate() != 1) {
                    c.rollback();
                    return false;
                }
                historiser(c, id, attendu, nouveau, acteur, commentaire);
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void ajouterHistorique(long id, String ancien, String nouveau, String acteur, String commentaire) {
        try (Connection c = source.connexion()) {
            historiser(c, id, ancien, nouveau, acteur, commentaire);
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static void historiser(Connection c, long id, String ancien, String nouveau, String acteur, String commentaire) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO candidature_historique (candidature_id, ancien_statut, nouveau_statut, acteur, commentaire) "
                + "VALUES (?,?,?,?,?)")) {
            p.setLong(1, id);
            p.setString(2, ancien);
            p.setString(3, nouveau);
            p.setString(4, acteur);
            p.setString(5, commentaire == null ? null : commentaire.substring(0, Math.min(1000, commentaire.length())));
            p.executeUpdate();
        }
    }

    @Override
    public List<Historique> historique(long id) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(
                "SELECT ancien_statut, nouveau_statut, date_changement, acteur, commentaire FROM candidature_historique "
                        + "WHERE candidature_id = ? ORDER BY date_changement, id")) {
            p.setLong(1, id);
            List<Historique> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(new Historique(r.getString(1), r.getString(2), r.getObject(3, LocalDateTime.class), r.getString(4), r.getString(5)));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static Long longOuNull(ResultSet r, String colonne) throws SQLException {
        long v = r.getLong(colonne);
        return r.wasNull() ? null : v;
    }

    private static Candidature candidature(ResultSet r) throws SQLException {
        int score = r.getInt("score_competences");
        Integer scoreOuNull = r.wasNull() ? null : score;
        return new Candidature(r.getLong("id"), longOuNull(r, "soumission_id"), r.getLong("etudiant_id"), r.getLong("offre_id"),
                r.getLong("entreprise_id"), r.getString("lettre_motivation"), scoreOuNull, r.getString("statut"),
                r.getObject("cree_le", LocalDateTime.class), r.getObject("modifie_le", LocalDateTime.class));
    }
}
