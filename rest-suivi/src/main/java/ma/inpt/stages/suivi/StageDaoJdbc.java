package ma.inpt.stages.suivi;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;
import ma.inpt.stages.suivi.Modeles.CompteRendu;
import ma.inpt.stages.suivi.Modeles.Evaluation;
import ma.inpt.stages.suivi.Modeles.NouveauCompteRendu;
import ma.inpt.stages.suivi.Modeles.NouveauStage;
import ma.inpt.stages.suivi.Modeles.NouvelleEvaluation;
import ma.inpt.stages.suivi.Modeles.Operation;
import ma.inpt.stages.suivi.Modeles.Stage;

/** Requêtes JDBC du service S6, sur le seul schéma soa_suivi. */
public final class StageDaoJdbc implements StageDao {

    private static final String COLONNES = "id, candidature_id, etudiant_id, offre_id, entreprise_id, date_debut, date_fin, "
            + "encadrant_entreprise, encadrant_pedagogique, statut, cree_le";

    private final FournisseurConnexion source;

    public StageDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    /** Exécute une écriture et sa ligne d'historique dans une seule transaction. */
    private interface Ecriture<T> {
        T executer(Connection c) throws SQLException;
    }

    private <T> T transaction(Ecriture<T> ecriture, String messageConflit) {
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try {
                T resultat = ecriture.executer(c);
                c.commit();
                return resultat;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw Sql.traduire(e, messageConflit);
        }
    }

    private static void tracer(Connection c, long stageId, String operation, String detail, String acteur) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO stage_historique (stage_id, operation, detail, acteur) VALUES (?,?,?,?)")) {
            p.setLong(1, stageId);
            p.setString(2, operation);
            p.setString(3, detail);
            p.setString(4, acteur);
            p.executeUpdate();
        }
    }

    private static long cle(PreparedStatement p) throws SQLException {
        try (ResultSet r = p.getGeneratedKeys()) {
            r.next();
            return r.getLong(1);
        }
    }

    @Override
    public Stage inserer(NouveauStage s, String acteur) {
        long id = transaction(c -> {
            String sql = "INSERT INTO stage (candidature_id, etudiant_id, offre_id, entreprise_id, date_debut, date_fin, "
                    + "encadrant_entreprise, encadrant_pedagogique) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, s.candidatureId());
                p.setLong(2, s.etudiantId());
                p.setLong(3, s.offreId());
                p.setLong(4, s.entrepriseId());
                p.setDate(5, Date.valueOf(s.dateDebut()));
                p.setDate(6, Date.valueOf(s.dateFin()));
                p.setString(7, s.encadrantEntreprise());
                p.setString(8, s.encadrantPedagogique());
                p.executeUpdate();
                long nouveau = cle(p);
                tracer(c, nouveau, "CREATION", "Dossier de stage créé après acceptation de la candidature " + s.candidatureId(), acteur);
                return nouveau;
            }
        }, "Un stage existe déjà pour cette candidature.");
        return trouver(id).orElseThrow();
    }

    @Override
    public Optional<Stage> trouver(long id) {
        return un("SELECT " + COLONNES + " FROM stage WHERE id = ?", id);
    }

    @Override
    public Optional<Stage> trouverParCandidature(long candidatureId) {
        return un("SELECT " + COLONNES + " FROM stage WHERE candidature_id = ?", candidatureId);
    }

    private Optional<Stage> un(String sql, long valeur) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, valeur);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(lire(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public List<Stage> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM stage WHERE 1=1");
        List<Object> valeurs = new ArrayList<>();
        if (f.statut() != null && !f.statut().isBlank()) {
            sql.append(" AND statut = ?");
            valeurs.add(f.statut());
        }
        if (f.etudiantId() != null) {
            sql.append(" AND etudiant_id = ?");
            valeurs.add(f.etudiantId());
        }
        if (f.entrepriseId() != null) {
            sql.append(" AND entreprise_id = ?");
            valeurs.add(f.entrepriseId());
        }
        sql.append(" ORDER BY date_debut DESC, id DESC");
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setObject(i + 1, valeurs.get(i));
            }
            List<Stage> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(lire(r));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public void changerStatut(long id, String ancien, String nouveau, String commentaire, String acteur) {
        transaction(c -> {
            try (PreparedStatement p = c.prepareStatement("UPDATE stage SET statut = ? WHERE id = ?")) {
                p.setString(1, nouveau);
                p.setLong(2, id);
                p.executeUpdate();
            }
            String detail = ancien + " → " + nouveau + (commentaire == null || commentaire.isBlank() ? "" : " : " + commentaire.trim());
            tracer(c, id, "CHANGEMENT_STATUT", detail, acteur);
            return null;
        }, "Conflit sur le stage.");
    }

    @Override
    public CompteRendu ajouterCompteRendu(long stageId, NouveauCompteRendu cr, String acteur) {
        long id = transaction(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO compte_rendu (stage_id, periode, contenu, auteur) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, stageId);
                p.setString(2, cr.periode().trim());
                p.setString(3, cr.contenu().trim());
                p.setString(4, cr.auteur().trim());
                p.executeUpdate();
                long nouveau = cle(p);
                tracer(c, stageId, "COMPTE_RENDU", "Compte rendu déposé : " + cr.periode().trim(), acteur);
                return nouveau;
            }
        }, "Conflit sur le compte rendu.");
        return comptesRendus(stageId).stream().filter(x -> x.id() == id).findFirst().orElseThrow();
    }

    @Override
    public List<CompteRendu> comptesRendus(long stageId) {
        List<CompteRendu> resultat = new ArrayList<>();
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT id, stage_id, periode, contenu, auteur, cree_le FROM compte_rendu WHERE stage_id = ? ORDER BY cree_le, id")) {
            p.setLong(1, stageId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(new CompteRendu(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5),
                            r.getObject(6, LocalDateTime.class)));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public Evaluation ajouterEvaluation(long stageId, NouvelleEvaluation ev, String acteur) {
        long id = transaction(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO evaluation (stage_id, note, commentaire, evaluateur) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, stageId);
                p.setBigDecimal(2, ev.note());
                p.setString(3, ev.commentaire());
                p.setString(4, ev.evaluateur().trim());
                p.executeUpdate();
                long nouveau = cle(p);
                tracer(c, stageId, "EVALUATION", "Note attribuée : " + ev.note().toPlainString().replace('.', ',') + "/20", acteur);
                return nouveau;
            }
        }, "Conflit sur l'évaluation.");
        return evaluations(stageId).stream().filter(x -> x.id() == id).findFirst().orElseThrow();
    }

    @Override
    public List<Evaluation> evaluations(long stageId) {
        List<Evaluation> resultat = new ArrayList<>();
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT id, stage_id, note, commentaire, evaluateur, cree_le FROM evaluation WHERE stage_id = ? ORDER BY cree_le, id")) {
            p.setLong(1, stageId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(new Evaluation(r.getLong(1), r.getLong(2), r.getBigDecimal(3), r.getString(4), r.getString(5),
                            r.getObject(6, LocalDateTime.class)));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    @Override
    public List<Operation> historique(long stageId) {
        List<Operation> resultat = new ArrayList<>();
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT operation, detail, date_operation, acteur FROM stage_historique WHERE stage_id = ? ORDER BY date_operation, id")) {
            p.setLong(1, stageId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(new Operation(r.getString(1), r.getString(2), r.getObject(3, LocalDateTime.class), r.getString(4)));
                }
            }
            return resultat;
        } catch (SQLException e) {
            throw Sql.indisponible(e);
        }
    }

    private static Stage lire(ResultSet r) throws SQLException {
        return new Stage(r.getLong("id"), r.getLong("candidature_id"), r.getLong("etudiant_id"), r.getLong("offre_id"),
                r.getLong("entreprise_id"), r.getObject("date_debut", LocalDate.class), r.getObject("date_fin", LocalDate.class),
                r.getString("encadrant_entreprise"), r.getString("encadrant_pedagogique"), r.getString("statut"),
                r.getObject("cree_le", LocalDateTime.class));
    }
}
