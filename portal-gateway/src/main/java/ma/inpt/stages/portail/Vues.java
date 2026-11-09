package ma.inpt.stages.portail;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import ma.inpt.stages.common.Dates;
import ma.inpt.stages.contrats.entreprises.Entreprise;
import ma.inpt.stages.contrats.entreprises.ResponsableContact;
import ma.inpt.stages.contrats.etudiants.Etudiant;
import ma.inpt.stages.contrats.offres.Compatibilite;
import ma.inpt.stages.contrats.offres.Offre;

/** Données échangées avec l'interface, en JSON. Conversion depuis et vers les types générés des services SOAP. */
public final class Vues {

    private Vues() {
    }

    public record EtudiantVue(Long id, String matricule, String nom, String prenom, String filiere, String niveau, String email,
                              String telephone, String ville, String statut, List<String> competences) {

        static EtudiantVue de(Etudiant e) {
            return new EtudiantVue(e.getId(), e.getMatricule(), e.getNom(), e.getPrenom(), e.getFiliere(), e.getNiveau(), e.getEmail(),
                    e.getTelephone(), e.getVille(), e.getStatut(), List.copyOf(e.getCompetence()));
        }

        /** Version vue par une entreprise : courriel et téléphone masqués. */
        EtudiantVue masquee() {
            return new EtudiantVue(id, matricule, nom, prenom, filiere, niveau, Masquage.email(email), Masquage.telephone(telephone), ville,
                    statut, competences);
        }

        Etudiant versSoap() {
            Etudiant x = new Etudiant();
            x.setMatricule(matricule);
            x.setNom(nom);
            x.setPrenom(prenom);
            x.setFiliere(filiere);
            x.setNiveau(niveau);
            x.setEmail(email);
            x.setTelephone(telephone);
            x.setVille(ville);
            x.setStatut(statut);
            if (competences != null) {
                x.getCompetence().addAll(competences);
            }
            return x;
        }

        String nomComplet() {
            return prenom + " " + nom;
        }
    }

    public record ContactVue(String nom, String fonction, String email, String telephone) {
    }

    public record EntrepriseVue(Long id, String raisonSociale, String ice, String secteur, String adresse, String ville, String email,
                                String telephone, ContactVue responsable, String statut) {

        static EntrepriseVue de(Entreprise e) {
            ResponsableContact r = e.getResponsable();
            ContactVue c = r == null ? null : new ContactVue(r.getNom(), r.getFonction(), r.getEmail(), r.getTelephone());
            return new EntrepriseVue(e.getId(), e.getRaisonSociale(), e.getIce(), e.getSecteur(), e.getAdresse(), e.getVille(), e.getEmail(),
                    e.getTelephone(), c, e.getStatut());
        }

        Entreprise versSoap() {
            Entreprise x = new Entreprise();
            x.setRaisonSociale(raisonSociale);
            x.setIce(ice);
            x.setSecteur(secteur);
            x.setAdresse(adresse);
            x.setVille(ville);
            x.setEmail(email);
            x.setTelephone(telephone);
            x.setStatut(statut);
            if (responsable != null) {
                ResponsableContact r = new ResponsableContact();
                r.setNom(responsable.nom());
                r.setFonction(responsable.fonction());
                r.setEmail(responsable.email());
                r.setTelephone(responsable.telephone());
                x.setResponsable(r);
            }
            return x;
        }
    }

    public record CompatibiliteVue(boolean compatible, boolean filiereAcceptee, boolean niveauSuffisant, boolean dateLimiteRespectee,
                                   int score, String motif) {

        static CompatibiliteVue de(Compatibilite c) {
            return new CompatibiliteVue(c.isCompatible(), c.isFiliereAcceptee(), c.isNiveauSuffisant(), c.isDateLimiteRespectee(),
                    c.getScoreCompetences(), c.getMotif());
        }
    }

    public record OffreVue(Long id, String titre, String description, List<String> competences, int dureeSemaines, LocalDate dateLimite,
                           int nombrePlaces, List<String> filieres, String niveauMinimum, String ville, long entrepriseId, String entreprise,
                           String statut, CompatibiliteVue compatibilite) {

        static OffreVue de(Offre o, String entreprise, CompatibiliteVue compatibilite) {
            return new OffreVue(o.getId(), o.getTitre(), o.getDescription(), List.copyOf(o.getCompetence()), o.getDureeSemaines(),
                    Dates.versLocalDate(o.getDateLimite()), o.getNombrePlaces(), List.copyOf(o.getFiliere()), o.getNiveauMinimum(),
                    o.getVille(), o.getEntrepriseId(), entreprise, o.getStatut(), compatibilite);
        }

        /** L'entreprise vient toujours de la session : une entreprise ne peut pas publier pour une autre. */
        Offre versSoap(long entrepriseImposee) {
            Offre x = new Offre();
            x.setTitre(titre);
            x.setDescription(description);
            if (competences != null) {
                x.getCompetence().addAll(competences);
            }
            x.setDureeSemaines(dureeSemaines);
            x.setDateLimite(Dates.versXml(dateLimite));
            x.setNombrePlaces(nombrePlaces);
            if (filieres != null) {
                x.getFiliere().addAll(filieres);
            }
            x.setNiveauMinimum(niveauMinimum);
            x.setVille(ville);
            x.setEntrepriseId(entrepriseImposee);
            x.setStatut(statut);
            return x;
        }
    }

    // Corps des requêtes de l'interface

    public record Connexion(String identifiant, String motDePasse) {
    }

    public record Candidater(Long offreId, String lettreMotivation) {
    }

    public record Decision(@NotBlank(message = "La décision est obligatoire.") String decision, String commentaire) {
    }

    public record ChangementStatut(String statut, String commentaire) {
    }
}
