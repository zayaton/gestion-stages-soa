package ma.inpt.stages.entreprises;

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

/** Requêtes JDBC du service S2, sur le seul schéma soa_entreprises. */
public final class EntrepriseDaoJdbc implements EntrepriseDao {

    private static final String COLONNES = "raison_sociale, ice, secteur, adresse, ville, email, telephone, "
            + "contact_nom, contact_fonction, contact_email, contact_telephone, statut";
    private static final String CONFLIT = "Cet ICE ou cette adresse e-mail est déjà utilisé.";

    private final FournisseurConnexion source;

    public EntrepriseDaoJdbc(FournisseurConnexion source) {
        this.source = source;
    }

    @Override
    public Entreprise inserer(Entreprise e) {
        String sql = "INSERT INTO entreprise (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            remplir(p, e);
            p.executeUpdate();
            try (ResultSet cles = p.getGeneratedKeys()) {
                cles.next();
                return e.avec(cles.getLong(1), e.statut());
            }
        } catch (SQLException ex) {
            throw Sql.traduire(ex, CONFLIT);
        }
    }

    @Override
    public Optional<Entreprise> trouver(long id) {
        try (Connection c = source.connexion();
             PreparedStatement p = c.prepareStatement("SELECT id, " + COLONNES + " FROM entreprise WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(lire(r)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    @Override
    public List<Entreprise> lister(Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT id, " + COLONNES + " FROM entreprise WHERE 1=1");
        List<String> valeurs = new ArrayList<>();
        critere(sql, valeurs, "secteur = ?", f.secteur());
        critere(sql, valeurs, "ville = ?", f.ville());
        critere(sql, valeurs, "statut = ?", f.statut());
        if (f.recherche() != null && !f.recherche().isBlank()) {
            critere(sql, valeurs, "raison_sociale LIKE ?", "%" + f.recherche().trim() + "%");
        }
        sql.append(" ORDER BY raison_sociale");
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valeurs.size(); i++) {
                p.setString(i + 1, valeurs.get(i));
            }
            List<Entreprise> resultat = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    resultat.add(lire(r));
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
    public void modifier(Entreprise e) {
        String sql = "UPDATE entreprise SET raison_sociale=?, ice=?, secteur=?, adresse=?, ville=?, email=?, telephone=?, "
                + "contact_nom=?, contact_fonction=?, contact_email=?, contact_telephone=?, statut=? WHERE id=?";
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement(sql)) {
            remplir(p, e);
            p.setLong(13, e.id());
            p.executeUpdate();
        } catch (SQLException ex) {
            throw Sql.traduire(ex, CONFLIT);
        }
    }

    @Override
    public void supprimer(long id) {
        try (Connection c = source.connexion(); PreparedStatement p = c.prepareStatement("DELETE FROM entreprise WHERE id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        } catch (SQLException ex) {
            throw Sql.indisponible(ex);
        }
    }

    private static void remplir(PreparedStatement p, Entreprise e) throws SQLException {
        p.setString(1, e.raisonSociale());
        p.setString(2, e.ice());
        p.setString(3, e.secteur());
        p.setString(4, e.adresse());
        p.setString(5, e.ville());
        p.setString(6, e.email());
        p.setString(7, e.telephone());
        p.setString(8, e.contactNom());
        p.setString(9, e.contactFonction());
        p.setString(10, e.contactEmail());
        p.setString(11, e.contactTelephone());
        p.setString(12, e.statut());
    }

    private static Entreprise lire(ResultSet r) throws SQLException {
        return new Entreprise(r.getLong("id"), r.getString("raison_sociale"), r.getString("ice"), r.getString("secteur"),
                r.getString("adresse"), r.getString("ville"), r.getString("email"), r.getString("telephone"),
                r.getString("contact_nom"), r.getString("contact_fonction"), r.getString("contact_email"),
                r.getString("contact_telephone"), r.getString("statut"));
    }
}
