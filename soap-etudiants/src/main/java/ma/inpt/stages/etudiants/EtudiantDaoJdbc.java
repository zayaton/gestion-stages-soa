package ma.inpt.stages.etudiants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;

/** Requêtes JDBC du service S1, sur le seul schéma soa_etudiants. */
public final class EtudiantDaoJdbc implements EtudiantDao {

    private static final String COLONNES = "id, matricule, nom, prenom, filiere, niveau, email, telephone, ville, statut";
    private static final String CONFLIT = "Ce matricule ou cette adresse e-mail est déjà utilisé.";

    private final FournisseurConnexion source;

    public EtudiantDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    @Override
    public Etudiant inserer(Etudiant e) {
        String sql = "INSERT INTO etudiant (matricule, nom, prenom, filiere, niveau, email, telephone, ville, statut) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                remplir(p, e);
                p.setString(9, e.statut());
                p.executeUpdate();
                long id;
                try (ResultSet cles = p.getGeneratedKeys()) {
                    cles.next();
                    id = cles.getLong(1);
                }
                ecrireCompetences(c, id, e.competences());
                c.commit();
                return e.avec(id, e.statut());
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw Sql.traduire(ex, CONFLIT);
        }
    }

    @Override
    public Optional<Etudiant> trouver(long id) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT " + COLONNES + " FROM etudiant WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(lire(c, r)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    @Override
    public List<Etudiant> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM etudiant WHERE 1=1");
        List<String> valeurs = new ArrayList<>();
        critere(sql, valeurs, "filiere = ?", f.filiere());
        critere(sql, valeurs, "niveau = ?", f.niveau());
        critere(sql, valeurs, "statut = ?", f.statut());
        critere(sql, valeurs, "ville = ?", f.ville());
        if (f.recherche() != null && !f.recherche().isBlank()) {
            String motif = "%" + f.recherche().trim() + "%";
            sql.append(" AND (nom LIKE ? OR prenom LIKE ? OR matricule LIKE ?)");
            valeurs.addAll(List.of(motif, motif, motif));
        }
        sql.append(" ORDER BY nom, prenom");
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setString(i + 1, valeurs.get(i));
            }
            List<Etudiant> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(lire(c, r));
                }
            }
            return resultat;
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    private static void critere(StringBuilder sql, List<String> valeurs, String condition, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            sql.append(" AND ").append(condition);
            valeurs.add(valeur);
        }
    }

    @Override
    public void modifier(Etudiant e) {
        String sql = "UPDATE etudiant SET matricule=?, nom=?, prenom=?, filiere=?, niveau=?, email=?, telephone=?, ville=?, statut=? WHERE id=?";
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql);
                 PreparedStatement effacer = c.prepareStatement("DELETE FROM etudiant_competence WHERE etudiant_id=?")) {
                remplir(p, e);
                p.setString(9, e.statut());
                p.setLong(10, e.id());
                p.executeUpdate();
                effacer.setLong(1, e.id());
                effacer.executeUpdate();
                ecrireCompetences(c, e.id(), e.competences());
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw Sql.traduire(ex, CONFLIT);
        }
    }

    @Override
    public void supprimer(long id) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM etudiant WHERE id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    @Override
    public void changerStatut(long id, String statut) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("UPDATE etudiant SET statut=? WHERE id=?")) {
            p.setString(1, statut);
            p.setLong(2, id);
            p.executeUpdate();
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    private static void remplir(PreparedStatement p, Etudiant e) throws SQLException {
        p.setString(1, e.matricule());
        p.setString(2, e.nom());
        p.setString(3, e.prenom());
        p.setString(4, e.filiere());
        p.setString(5, e.niveau());
        p.setString(6, e.email());
        p.setString(7, e.telephone());
        p.setString(8, e.ville());
    }

    private static void ecrireCompetences(Connection c, long id, List<String> competences) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO etudiant_competence (etudiant_id, competence) VALUES (?,?)")) {
            for (String competence : competences) {
                p.setLong(1, id);
                p.setString(2, competence.trim());
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private static Etudiant lire(Connection c, ResultSet r) throws SQLException {
        long id = r.getLong("id");
        List<String> competences = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT competence FROM etudiant_competence WHERE etudiant_id=? ORDER BY competence")) {
            p.setLong(1, id);
            try (ResultSet rc = p.executeQuery()) {
                while (rc.next()) {
                    competences.add(rc.getString(1));
                }
            }
        }
        return new Etudiant(id, r.getString("matricule"), r.getString("nom"), r.getString("prenom"), r.getString("filiere"),
                r.getString("niveau"), r.getString("email"), r.getString("telephone"), r.getString("ville"), r.getString("statut"), competences);
    }
}
