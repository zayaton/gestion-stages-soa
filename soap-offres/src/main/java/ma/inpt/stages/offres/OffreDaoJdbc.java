package ma.inpt.stages.offres;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ma.inpt.stages.common.FournisseurConnexion;
import ma.inpt.stages.common.Sql;

/** Requêtes JDBC du service S3, sur le seul schéma soa_offres. */
public final class OffreDaoJdbc implements OffreDao {

    private static final String COLONNES = "id, titre, description, duree_semaines, date_limite, nombre_places, niveau_minimum, ville, entreprise_id, statut";
    private static final String CONFLIT = "Cette offre contredit une contrainte de la base de données.";

    private final FournisseurConnexion source;

    public OffreDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    @Override
    public Offre inserer(Offre o) {
        String sql = "INSERT INTO offre (titre, description, duree_semaines, date_limite, nombre_places, niveau_minimum, ville, entreprise_id, statut) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                remplir(p, o);
                p.executeUpdate();
                long id;
                try (ResultSet cles = p.getGeneratedKeys()) {
                    cles.next();
                    id = cles.getLong(1);
                }
                ecrireListes(c, id, o);
                c.commit();
                return o.avec(id, o.statut());
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw Sql.traduire(ex, CONFLIT);
        }
    }

    @Override
    public Optional<Offre> trouver(long id) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT " + COLONNES + " FROM offre WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(lire(c, r)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    @Override
    public List<Offre> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNES + " FROM offre o WHERE 1=1");
        List<Object> valeurs = new ArrayList<>();
        critere(sql, valeurs, "statut = ?", f.statut());
        critere(sql, valeurs, "ville = ?", f.ville());
        critere(sql, valeurs, "EXISTS (SELECT 1 FROM offre_filiere x WHERE x.offre_id = o.id AND x.filiere = ?)", f.filiere());
        critere(sql, valeurs, "niveau_minimum <= ?", f.niveau());
        critere(sql, valeurs, "EXISTS (SELECT 1 FROM offre_competence x WHERE x.offre_id = o.id AND x.competence = ?)", f.competence());
        if (f.entrepriseId() != null) {
            sql.append(" AND entreprise_id = ?");
            valeurs.add(f.entrepriseId());
        }
        if (f.recherche() != null && !f.recherche().isBlank()) {
            String motif = "%" + f.recherche().trim() + "%";
            sql.append(" AND (titre LIKE ? OR description LIKE ?)");
            valeurs.addAll(List.of(motif, motif));
        }
        if (f.ouvertesAu() != null) {
            sql.append(" AND statut = 'ACTIVE' AND nombre_places > 0 AND date_limite >= ?");
            valeurs.add(Date.valueOf(f.ouvertesAu()));
        }
        sql.append(" ORDER BY date_limite, id");
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setObject(i + 1, valeurs.get(i));
            }
            List<Offre> resultat = new ArrayList<>();
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

    private static void critere(StringBuilder sql, List<Object> valeurs, String condition, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            sql.append(" AND ").append(condition);
            valeurs.add(valeur);
        }
    }

    @Override
    public void modifier(Offre o) {
        String sql = "UPDATE offre SET titre=?, description=?, duree_semaines=?, date_limite=?, nombre_places=?, niveau_minimum=?, ville=?, entreprise_id=?, statut=? WHERE id=?";
        try (Connection c = source.connexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql);
                 PreparedStatement sansCompetences = c.prepareStatement("DELETE FROM offre_competence WHERE offre_id=?");
                 PreparedStatement sansFilieres = c.prepareStatement("DELETE FROM offre_filiere WHERE offre_id=?")) {
                remplir(p, o);
                p.setLong(10, o.id());
                p.executeUpdate();
                sansCompetences.setLong(1, o.id());
                sansCompetences.executeUpdate();
                sansFilieres.setLong(1, o.id());
                sansFilieres.executeUpdate();
                ecrireListes(c, o.id(), o);
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
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM offre WHERE id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    @Override
    public boolean decrementerPlaces(long id) {
        // MySQL applique SET de gauche à droite : le IF voit déjà le nombre de places diminué.
        String sql = "UPDATE offre SET nombre_places = nombre_places - 1, statut = IF(nombre_places = 0, 'POURVUE', statut) "
                + "WHERE id = ? AND statut = 'ACTIVE' AND nombre_places > 0";
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id);
            return p.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    private static void remplir(PreparedStatement p, Offre o) throws SQLException {
        p.setString(1, o.titre());
        p.setString(2, o.description());
        p.setInt(3, o.dureeSemaines());
        p.setDate(4, Date.valueOf(o.dateLimite()));
        p.setInt(5, o.nombrePlaces());
        p.setString(6, o.niveauMinimum());
        p.setString(7, o.ville());
        p.setLong(8, o.entrepriseId());
        p.setString(9, o.statut());
    }

    private static void ecrireListes(Connection c, long id, Offre o) throws SQLException {
        ecrireListe(c, "INSERT IGNORE INTO offre_competence (offre_id, competence) VALUES (?,?)", id, o.competences());
        ecrireListe(c, "INSERT IGNORE INTO offre_filiere (offre_id, filiere) VALUES (?,?)", id, o.filieres());
    }

    private static void ecrireListe(Connection c, String sql, long id, List<String> valeurs) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (String valeur : valeurs) {
                p.setLong(1, id);
                p.setString(2, valeur.trim());
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private static List<String> lireListe(Connection c, String sql, long id) throws SQLException {
        List<String> valeurs = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    valeurs.add(r.getString(1));
                }
            }
        }
        return valeurs;
    }

    private static Offre lire(Connection c, ResultSet r) throws SQLException {
        long id = r.getLong("id");
        return new Offre(id, r.getString("titre"), r.getString("description"),
                lireListe(c, "SELECT competence FROM offre_competence WHERE offre_id=? ORDER BY competence", id),
                r.getInt("duree_semaines"), r.getObject("date_limite", java.time.LocalDate.class), r.getInt("nombre_places"),
                lireListe(c, "SELECT filiere FROM offre_filiere WHERE offre_id=? ORDER BY filiere", id),
                r.getString("niveau_minimum"), r.getString("ville"), r.getLong("entreprise_id"), r.getString("statut"));
    }
}
