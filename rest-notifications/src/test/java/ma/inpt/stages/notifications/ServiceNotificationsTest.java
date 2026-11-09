package ma.inpt.stages.notifications;

import static ma.inpt.stages.notifications.Doublures.etudiant;
import static ma.inpt.stages.notifications.Doublures.offre;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ma.inpt.stages.common.ErreurMetier;
import ma.inpt.stages.notifications.Modeles.BilanRappels;
import ma.inpt.stages.notifications.Modeles.NouvelleNotification;
import ma.inpt.stages.notifications.Modeles.Notification;
import org.junit.jupiter.api.Test;

class ServiceNotificationsTest {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 9);
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), CASABLANCA);

    private final Doublures.Dao dao = new Doublures.Dao();
    private final Doublures.Boite boite = new Doublures.Boite();
    private final Doublures.Sources sources = new Doublures.Sources();
    private final ServiceNotifications service = new ServiceNotifications(dao, boite, sources, HORLOGE);

    private static NouvelleNotification confirmation(String destinataire) {
        return new NouvelleNotification("CONFIRMATION_DEPOT", destinataire,
                Map.of("nomEtudiant", "Salma El Idrissi", "titreOffre", "Stage AMOA", "entreprise", "NovaPay Maghreb"), 13L, 1L);
    }

    // Envoi

    @Test
    void uneNotificationEnvoyeeEstEnregistreeAvecSonCourriel() {
        Notification n = service.envoyer(confirmation("salma.elidrissi@inpt.ac.ma"));
        assertEquals("ENVOYEE", n.statut());
        assertNull(n.erreur());
        assertEquals("Candidature enregistrée : Stage AMOA", n.sujet());
        assertTrue(n.corps().contains("NovaPay Maghreb"));
        assertEquals(13L, n.candidatureId());
        assertEquals(List.of("salma.elidrissi@inpt.ac.ma"), boite.destinataires);
    }

    @Test
    void unEchecSmtpEstEnregistreSansFaireEchouerLaRequete() {
        boite.enPanne = true;
        Notification n = service.envoyer(confirmation("salma.elidrissi@inpt.ac.ma"));
        assertEquals("ECHEC", n.statut());
        assertTrue(n.erreur().contains("SMTP"));
        assertEquals(1, dao.lignes.size());
    }

    @Test
    void uneDemandeInvalideNEnvoieRienEtNEnregistreRien() {
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES,
                assertThrows(ErreurMetier.class, () -> service.envoyer(confirmation("pas-une-adresse"))).type());
        assertEquals(ErreurMetier.Type.DONNEES_INVALIDES, assertThrows(ErreurMetier.class,
                () -> service.envoyer(new NouvelleNotification("REFUS", "a@inpt.ac.ma", Map.of(), null, null))).type());
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.envoyer(null)).type());
        assertTrue(boite.messages.isEmpty());
        assertTrue(dao.lignes.isEmpty());
    }

    @Test
    void consultationEtListe() {
        long id = service.envoyer(confirmation("a@inpt.ac.ma")).id();
        service.envoyer(confirmation("b@inpt.ac.ma"));
        assertEquals("a@inpt.ac.ma", service.consulter(id).destinataire());
        assertEquals("NOTIFICATION_INTROUVABLE", assertThrows(ErreurMetier.class, () -> service.consulter(99)).code());
        assertEquals(1, service.lister("a@inpt.ac.ma", null, null, null).size());
        assertEquals(2, service.lister(null, null, "CONFIRMATION_DEPOT", null).size());
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.lister(null, null, "SMS", null)).type());
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.lister(null, null, null, 0)).type());
    }

    // Rappels de date limite

    @Test
    void unRappelVaAuxEtudiantsCompatiblesQuiNOntPasCandidate() {
        sources.offres.add(offre(5, AUJOURDHUI.plusDays(2), "2A", "DATA", "AMOA"));
        sources.etudiants.add(etudiant(1, "AMOA", "2A"));   // compatible
        sources.etudiants.add(etudiant(2, "AMOA", "3A"));   // compatible, niveau supérieur
        sources.etudiants.add(etudiant(3, "AMOA", "1A"));   // niveau insuffisant
        sources.etudiants.add(etudiant(4, "ICCN", "3A"));   // filière non acceptée
        sources.etudiants.add(etudiant(5, "DATA", "2A"));   // compatible mais a déjà candidaté
        sources.candidats.put(5L, Set.of(5L));

        BilanRappels bilan = service.executerRappels(3);

        assertEquals(1, bilan.offresConcernees());
        assertEquals(2, bilan.rappelsEnvoyes());
        assertEquals(List.of("e1@inpt.ac.ma", "e2@inpt.ac.ma"), boite.destinataires);
        assertTrue(boite.messages.get(0).texte().contains("11 novembre 2026"), boite.messages.get(0).texte());
        assertEquals(5L, dao.lignes.get(1L).offreId());
    }

    @Test
    void seulesLesOffresDansLaFenetreSontConcernees() {
        sources.offres.add(offre(1, AUJOURDHUI, "1A", "AMOA"));               // dernier jour : oui
        sources.offres.add(offre(2, AUJOURDHUI.plusDays(3), "1A", "AMOA"));   // dans 3 jours : oui
        sources.offres.add(offre(3, AUJOURDHUI.plusDays(4), "1A", "AMOA"));   // dans 4 jours : non
        sources.offres.add(offre(4, AUJOURDHUI.minusDays(1), "1A", "AMOA"));  // passée : non
        sources.etudiants.add(etudiant(1, "AMOA", "1A"));
        BilanRappels bilan = service.executerRappels(3);
        assertEquals(2, bilan.offresConcernees());
        assertEquals(2, bilan.rappelsEnvoyes());
    }

    @Test
    void relancerLesRappelsNeRenvoieRien() {
        sources.offres.add(offre(5, AUJOURDHUI.plusDays(1), "1A", "AMOA"));
        sources.etudiants.add(etudiant(1, "AMOA", "2A"));
        assertEquals(1, service.executerRappels(3).rappelsEnvoyes());
        BilanRappels second = service.executerRappels(3);
        assertEquals(0, second.rappelsEnvoyes());
        assertEquals(1, second.dejaEnvoyes());
        assertEquals(1, boite.messages.size());
    }

    @Test
    void unRappelEnEchecSeraRetenteAuPassageSuivant() {
        sources.offres.add(offre(5, AUJOURDHUI.plusDays(1), "1A", "AMOA"));
        sources.etudiants.add(etudiant(1, "AMOA", "2A"));
        boite.enPanne = true;
        assertEquals(1, service.executerRappels(3).echecs());
        boite.enPanne = false;
        assertEquals(1, service.executerRappels(3).rappelsEnvoyes());
    }

    @Test
    void siUnServiceSourceEstEnPanneAucunCourrielNePart() {
        sources.offres.add(offre(5, AUJOURDHUI.plusDays(1), "1A", "AMOA"));
        sources.offres.add(offre(6, AUJOURDHUI.plusDays(2), "1A", "AMOA"));
        sources.etudiants.add(etudiant(1, "AMOA", "2A"));
        sources.candidaturesEnPanne = true;
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> service.executerRappels(3));
        assertEquals(ErreurMetier.Type.INDISPONIBLE, e.type());
        assertTrue(boite.messages.isEmpty());
        assertTrue(dao.lignes.isEmpty());
    }

    @Test
    void sansOffreConcerneeLesEtudiantsNeSontPasLus() {
        sources.offres.add(offre(3, AUJOURDHUI.plusDays(10), "1A", "AMOA"));
        assertEquals(0, service.executerRappels(3).offresConcernees());
        assertEquals(0, sources.lecturesEtudiants);
    }

    @Test
    void unNombreDeJoursHorsBornesEstRefuse() {
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.executerRappels(-1)).type());
        assertEquals(ErreurMetier.Type.REQUETE_INVALIDE, assertThrows(ErreurMetier.class, () -> service.executerRappels(31)).type());
    }

    // Planification

    @Test
    void lePassageQuotidienEstCalculeDansLeFuseauDuMaroc() {
        ZonedDateTime matin = ZonedDateTime.of(2026, 11, 9, 7, 30, 0, 0, CASABLANCA);
        assertEquals(Duration.ofMinutes(30), PlanificateurRappels.delaiAvant(matin, LocalTime.of(8, 0)));
        ZonedDateTime apres = ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, CASABLANCA);
        assertEquals(Duration.ofHours(24), PlanificateurRappels.delaiAvant(apres, LocalTime.of(8, 0)));
    }
}
