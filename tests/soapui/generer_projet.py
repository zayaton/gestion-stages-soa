#!/usr/bin/env python3
"""Génère le projet SoapUI (Stages-SOA-soapui-project.xml) à partir de la liste des cas ci-dessous.

Le projet produit est versionné : SoapUI suffit pour l'ouvrir et le lancer, Python ne sert qu'à le régénérer.
"""
import os
import uuid
from xml.sax.saxutils import escape

ICI = os.path.dirname(os.path.abspath(__file__))
WSSE = 'http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd'
SERVICES = {
    'S1': ('Etudiants', 'etudiants', 'http://stages.inpt.ma/etudiants', 8081,
           ['ajouterEtudiant', 'consulterEtudiant', 'listerEtudiants', 'modifierEtudiant', 'supprimerEtudiant', 'verifierEtudiant', 'changerStatutEtudiant']),
    'S2': ('Entreprises', 'entreprises', 'http://stages.inpt.ma/entreprises', 8082,
           ['ajouterEntreprise', 'consulterEntreprise', 'listerEntreprises', 'modifierEntreprise', 'supprimerEntreprise', 'verifierEntreprise', 'consulterOffresEntreprise']),
    'S3': ('Offres', 'offres', 'http://stages.inpt.ma/offres', 8083,
           ['creerOffre', 'modifierOffre', 'supprimerOffre', 'consulterOffre', 'listerOffres', 'verifierDisponibilite', 'verifierCompatibilite', 'decrementerPlaces']),
}
JETON = ('<wsse:Security xmlns:wsse="' + WSSE + '" soapenv:mustUnderstand="1"><wsse:UsernameToken>'
         '<wsse:Username>${#Project#wsUtilisateur}</wsse:Username><wsse:Password>${#Project#wsMotDePasse}</wsse:Password>'
         '</wsse:UsernameToken></wsse:Security>')


def uid():
    return str(uuid.uuid4())


def contient(*jetons):
    return [('contains', j) for j in jetons]


def ok(*jetons):
    """Réponse sans faute, conforme au WSDL, contenant les textes indiqués."""
    return [('soap', None), ('notfault', None), ('schema', None)] + list(contient(*jetons))


def faute(element, code=None):
    """Faute typée attendue : son élément dans le détail et son code."""
    assertions = [('soap', None), ('fault', None), ('contains', element)]
    if code:
        assertions.append(('contains', '<code>' + code + '</code>'))
    return assertions + [('absent', 'stackTrace'), ('absent', 'ma.inpt.stages')]


def securite(code):
    return [('soap', None), ('fault', None), ('contains', 'wsse:' + code)]


def etape(service, nom, operation, corps, assertions, entete=JETON):
    return dict(service=service, nom=nom, operation=operation, corps=corps, assertions=assertions, entete=entete)


def op(service, nom, operation, champs, assertions, entete=JETON):
    return etape(service, nom, operation, f'<t:{operation}>{champs}</t:{operation}>', assertions, entete)


def jeton_faux(utilisateur, mot):
    return ('<wsse:Security xmlns:wsse="' + WSSE + '"><wsse:UsernameToken>'
            f'<wsse:Username>{utilisateur}</wsse:Username><wsse:Password>{mot}</wsse:Password>'
            '</wsse:UsernameToken></wsse:Security>')


def cas_securite(service, operation, champs):
    return [
        ('Sécurité : en-tête absent', [op(service, 'Sans en-tête', operation, champs, securite('InvalidSecurity'), entete='')]),
        ('Sécurité : mauvais mot de passe', [op(service, 'Mauvais mot de passe', operation, champs, securite('FailedAuthentication'), entete=jeton_faux('soapui', 'mauvais'))]),
        ('Sécurité : compte inconnu', [op(service, 'Compte inconnu', operation, champs, securite('FailedAuthentication'), entete=jeton_faux('pirate', 'SoapUI#Ws2026'))]),
    ]


# ---------- S1 ----------
def etudiant(matricule='INPT-2026-7001', email='test.soapui@inpt.ac.ma', ville='Fès', niveau='2A', statut=''):
    s = f'<t:statut>{statut}</t:statut>' if statut else ''
    return (f'<t:etudiant><t:matricule>{matricule}</t:matricule><t:nom>Soapui</t:nom><t:prenom>Étudiante</t:prenom><t:filiere>AMOA</t:filiere>'
            f'<t:niveau>{niveau}</t:niveau><t:email>{email}</t:email><t:telephone>0612345678</t:telephone><t:ville>{ville}</t:ville>{s}'
            '<t:competence>SQL</t:competence><t:competence>BPMN</t:competence></t:etudiant>')


ID_E = '<t:id>${Ajouter#Response#//*:etudiantCree/*:id}</t:id>'
S1 = [
    ('Cycle de vie complet', [
        op('S1', 'Ajouter', 'ajouterEtudiant', etudiant(), ok('<statut>ACTIF</statut>', 'INPT-2026-7001')),
        op('S1', 'Consulter', 'consulterEtudiant', ID_E, ok('Étudiante', '<competence>BPMN</competence>')),
        op('S1', 'Modifier', 'modifierEtudiant', ID_E + etudiant(ville='Agadir'), ok('<ville>Agadir</ville>')),
        op('S1', 'Changer le statut', 'changerStatutEtudiant', ID_E + '<t:statut>SUSPENDU</t:statut>', ok('<statut>SUSPENDU</statut>')),
        op('S1', 'Supprimer', 'supprimerEtudiant', ID_E, ok()),
        op('S1', 'Consulter après suppression', 'consulterEtudiant', ID_E, faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
    ]),
    ('Lister avec filtres', [
        op('S1', 'Sans filtre', 'listerEtudiants', '', ok('INPT-2024-0001', 'INPT-2025-0020')),
        op('S1', 'Par filière et niveau', 'listerEtudiants', '<t:filtre><t:filiere>AMOA</t:filiere><t:niveau>2A</t:niveau></t:filtre>', ok('Salma', 'Youssef') + [('absent', 'Imane')]),
        op('S1', 'Par statut', 'listerEtudiants', '<t:filtre><t:statut>SUSPENDU</t:statut></t:filtre>', ok('Sara') + [('absent', 'Salma')]),
        op('S1', 'Par recherche', 'listerEtudiants', '<t:filtre><t:recherche>Benjelloun</t:recherche></t:filtre>', ok('Youssef') + [('absent', 'Salma')]),
    ]),
    ('R1 : verifierEtudiant', [
        op('S1', 'Étudiant actif', 'verifierEtudiant', '<t:id>1</t:id>', ok('<eligible>true</eligible>', '<statut>ACTIF</statut>')),
        op('S1', 'Étudiant suspendu', 'verifierEtudiant', '<t:id>17</t:id>', ok('<eligible>false</eligible>', '<regle>R1</regle>', 'suspendu')),
        op('S1', 'Étudiant en stage', 'verifierEtudiant', '<t:id>16</t:id>', ok('<eligible>false</eligible>', '<regle>R1</regle>', 'déjà en stage')),
        op('S1', 'Étudiant diplômé', 'verifierEtudiant', '<t:id>18</t:id>', ok('<eligible>false</eligible>', '<regle>R1</regle>', 'diplômé')),
        op('S1', 'Étudiant inconnu', 'verifierEtudiant', '<t:id>9999</t:id>', ok('<existe>false</existe>', '<eligible>false</eligible>')),
    ]),
    ('EtudiantIntrouvableFault', [
        op('S1', 'Consulter', 'consulterEtudiant', '<t:id>9999</t:id>', faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
        op('S1', 'Modifier', 'modifierEtudiant', '<t:id>9999</t:id>' + etudiant(), faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
        op('S1', 'Supprimer', 'supprimerEtudiant', '<t:id>9999</t:id>', faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
        op('S1', 'Changer le statut', 'changerStatutEtudiant', '<t:id>9999</t:id><t:statut>ACTIF</t:statut>', faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
    ]),
    ('DonneesInvalidesFault', [
        op('S1', 'Matricule mal formé', 'ajouterEtudiant', etudiant(matricule='ABC'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('format INPT-AAAA-NNNN')),
        op('S1', 'Niveau inconnu', 'ajouterEtudiant', etudiant(niveau='9Z'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('1A, 2A ou 3A')),
        op('S1', 'E-mail invalide', 'ajouterEtudiant', etudiant(email='pas-un-email'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('adresse e-mail est invalide')),
        op('S1', 'Statut inventé', 'ajouterEtudiant', etudiant(statut='EN_VACANCES'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES')),
        op('S1', 'Champs manquants', 'ajouterEtudiant', '<t:etudiant><t:nom>Seul</t:nom></t:etudiant>', faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('matricule est obligatoire')),
        op('S1', 'Identifiant non numérique', 'consulterEtudiant', '<t:id>abc</t:id>', faute('donneesInvalidesFault', 'DONNEES_INVALIDES')),
    ]),
    ('ConflitFault', [
        op('S1', 'Matricule déjà utilisé', 'ajouterEtudiant', etudiant(matricule='INPT-2024-0001'), faute('conflitFault', 'CONFLIT_DONNEES')),
        op('S1', 'E-mail déjà utilisé', 'ajouterEtudiant', etudiant(email='salma.elidrissi@inpt.ac.ma'), faute('conflitFault', 'CONFLIT_DONNEES')),
        op('S1', 'Transition interdite', 'changerStatutEtudiant', '<t:id>18</t:id><t:statut>ACTIF</t:statut>', faute('conflitFault', 'TRANSITION_INTERDITE')),
        op('S1', 'Supprimer un étudiant en stage', 'supprimerEtudiant', '<t:id>16</t:id>', faute('conflitFault', 'ETUDIANT_EN_STAGE')),
    ]),
    ('Opération inconnue', [
        etape('S1', 'Opération inconnue', 'consulterEtudiant', '<t:inconnue/>', [('soap', None), ('fault', None), ('contains', "n'existe pas")]),
    ]),
] + cas_securite('S1', 'consulterEtudiant', '<t:id>1</t:id>')


# ---------- S2 ----------
def entreprise(ice='009000111000022', email='contact@test-soapui.ma', statut='', rs='Société Générale de Tests'):
    s = f'<t:statut>{statut}</t:statut>' if statut else ''
    return (f'<t:entreprise><t:raisonSociale>{rs}</t:raisonSociale><t:ice>{ice}</t:ice><t:secteur>Conseil</t:secteur><t:adresse>1 rue des Tests</t:adresse>'
            f'<t:ville>Rabat</t:ville><t:email>{email}</t:email><t:telephone>0537000000</t:telephone><t:responsable><t:nom>Hind Test</t:nom>'
            f'<t:fonction>RH</t:fonction><t:email>hind@test-soapui.ma</t:email><t:telephone>0661000000</t:telephone></t:responsable>{s}</t:entreprise>')


ID_N = '<t:id>${Ajouter#Response#//*:entrepriseCreee/*:id}</t:id>'
S2 = [
    ('Cycle de vie complet', [
        op('S2', 'Ajouter', 'ajouterEntreprise', entreprise(), ok('<statut>ACTIVE</statut>', 'Hind Test')),
        op('S2', 'Consulter', 'consulterEntreprise', ID_N, ok('Société Générale de Tests')),
        op('S2', 'Vérifier active', 'verifierEntreprise', ID_N, ok('<active>true</active>')),
        op('S2', 'Modifier', 'modifierEntreprise', ID_N + entreprise(statut='INACTIVE'), ok('<statut>INACTIVE</statut>')),
        op('S2', 'Vérifier inactive', 'verifierEntreprise', ID_N, ok('<active>false</active>', '<regle>R2</regle>')),
        op('S2', 'Offres de la nouvelle entreprise', 'consulterOffresEntreprise', ID_N, ok() + [('absent', '<offre>')]),
        op('S2', 'Supprimer', 'supprimerEntreprise', ID_N, ok()),
    ]),
    ('Lister avec filtres', [
        op('S2', 'Sans filtre', 'listerEntreprises', '', ok('NovaPay Maghreb', 'Médina Systèmes')),
        op('S2', 'Par ville', 'listerEntreprises', '<t:filtre><t:ville>Casablanca</t:ville></t:filtre>', ok('NovaPay', 'Zellige') + [('absent', 'Médina')]),
        op('S2', 'Par statut', 'listerEntreprises', '<t:filtre><t:statut>INACTIVE</t:statut></t:filtre>', ok('Médina') + [('absent', 'NovaPay')]),
    ]),
    ('R2 : verifierEntreprise', [
        op('S2', 'Entreprise active', 'verifierEntreprise', '<t:id>1</t:id>', ok('<referencee>true</referencee>', '<active>true</active>')),
        op('S2', 'Entreprise inactive', 'verifierEntreprise', '<t:id>8</t:id>', ok('<referencee>true</referencee>', '<active>false</active>', '<regle>R2</regle>')),
        op('S2', 'Entreprise inconnue', 'verifierEntreprise', '<t:id>9999</t:id>', ok('<referencee>false</referencee>', '<regle>R2</regle>')),
    ]),
    ('consulterOffresEntreprise : appel de S3', [
        op('S2', 'Offres de Détroit Data', 'consulterOffresEntreprise', '<t:id>7</t:id>', ok('Ingénieur données', 'Data scientist')),
    ]),
    ('EntrepriseIntrouvableFault', [
        op('S2', 'Consulter', 'consulterEntreprise', '<t:id>9999</t:id>', faute('entrepriseIntrouvableFault', 'ENTREPRISE_INTROUVABLE')),
        op('S2', 'Offres', 'consulterOffresEntreprise', '<t:id>9999</t:id>', faute('entrepriseIntrouvableFault', 'ENTREPRISE_INTROUVABLE')),
        op('S2', 'Supprimer', 'supprimerEntreprise', '<t:id>9999</t:id>', faute('entrepriseIntrouvableFault', 'ENTREPRISE_INTROUVABLE')),
    ]),
    ('DonneesInvalidesFault', [
        op('S2', 'ICE mal formé', 'ajouterEntreprise', entreprise(ice='123'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('15 chiffres')),
        op('S2', 'Statut inventé', 'ajouterEntreprise', entreprise(statut='FERMEE'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES')),
        op('S2', 'Sans responsable', 'ajouterEntreprise', '<t:entreprise><t:raisonSociale>Seule</t:raisonSociale></t:entreprise>', faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('responsable de contact')),
    ]),
    ('ConflitFault', [
        op('S2', 'ICE déjà utilisé', 'ajouterEntreprise', entreprise(ice='001526374000081'), faute('conflitFault', 'CONFLIT_DONNEES')),
        op('S2', 'Supprimer une entreprise avec offres', 'supprimerEntreprise', '<t:id>1</t:id>', faute('conflitFault', 'ENTREPRISE_AVEC_OFFRES')),
    ]),
] + cas_securite('S2', 'consulterEntreprise', '<t:id>1</t:id>')


# ---------- S3 ----------
def offre(statut='', places=1, entreprise_id=1, date='2099-12-31', titre='Stage de test SoapUI'):
    s = f'<t:statut>{statut}</t:statut>' if statut else ''
    return (f'<t:offre><t:titre>{titre}</t:titre><t:description>Mission décrite pour le test.</t:description><t:competence>SQL</t:competence>'
            f'<t:dureeSemaines>12</t:dureeSemaines><t:dateLimite>{date}</t:dateLimite><t:nombrePlaces>{places}</t:nombrePlaces><t:filiere>AMOA</t:filiere>'
            f'<t:niveauMinimum>1A</t:niveauMinimum><t:ville>Fès</t:ville><t:entrepriseId>{entreprise_id}</t:entrepriseId>{s}</t:offre>')


ID_O = '<t:id>${Creer#Response#//*:offreCreee/*:id}</t:id>'
S3 = [
    ('Cycle de vie : brouillon puis suppression', [
        op('S3', 'Creer', 'creerOffre', offre(), ok('<statut>BROUILLON</statut>')),
        op('S3', 'Consulter', 'consulterOffre', ID_O, ok('Stage de test SoapUI', '<competence>SQL</competence>')),
        op('S3', 'Modifier', 'modifierOffre', ID_O + offre(titre='Titre modifié', places=3), ok('Titre modifié', '<nombrePlaces>3</nombrePlaces>')),
        op('S3', 'Supprimer', 'supprimerOffre', ID_O, ok()),
        op('S3', 'Consulter après suppression', 'consulterOffre', ID_O, faute('offreIntrouvableFault', 'OFFRE_INTROUVABLE')),
    ]),
    ('R10 : décompte des places jusqu\'à POURVUE', [
        op('S3', 'Creer', 'creerOffre', offre(statut='ACTIVE', places=2), ok('<statut>ACTIVE</statut>')),
        op('S3', 'Première place', 'decrementerPlaces', '<t:offreId>${Creer#Response#//*:offreCreee/*:id}</t:offreId>', ok('<nombrePlaces>1</nombrePlaces>', '<statut>ACTIVE</statut>')),
        op('S3', 'Dernière place', 'decrementerPlaces', '<t:offreId>${Creer#Response#//*:offreCreee/*:id}</t:offreId>', ok('<nombrePlaces>0</nombrePlaces>', '<statut>POURVUE</statut>')),
        op('S3', 'Plus de place', 'decrementerPlaces', '<t:offreId>${Creer#Response#//*:offreCreee/*:id}</t:offreId>', faute('conflitFault', 'PLUS_DE_PLACE')),
    ]),
    ('Lister avec filtres', [
        op('S3', 'Sans filtre', 'listerOffres', '', ok('Paiement mobile', 'Sécurité des systèmes')),
        op('S3', 'Par filière', 'listerOffres', '<t:filtre><t:filiere>ICCN</t:filiere></t:filtre>', ok('réseaux 5G') + [('absent', 'Paiement mobile')]),
        op('S3', 'Par entreprise', 'listerOffres', '<t:filtre><t:entrepriseId>7</t:entrepriseId></t:filtre>', ok('Ingénieur données') + [('absent', 'Paiement mobile')]),
        op('S3', 'Ouvertes seulement', 'listerOffres', '<t:filtre><t:ouvertesSeulement>true</t:ouvertesSeulement></t:filtre>', ok('Paiement mobile') + [('absent', 'Sécurité des systèmes'), ('absent', 'Consultant décisionnel'), ('absent', 'Chef de projet junior')]),
    ]),
    ('R2, R3, R4 : verifierDisponibilite', [
        op('S3', 'Offre disponible', 'verifierDisponibilite', '<t:offreId>1</t:offreId>', ok('<disponible>true</disponible>', '<entrepriseId>1</entrepriseId>')),
        op('S3', 'R2 brouillon', 'verifierDisponibilite', '<t:offreId>11</t:offreId>', ok('<disponible>false</disponible>', '<regle>R2</regle>')),
        op('S3', 'R2 pourvue', 'verifierDisponibilite', '<t:offreId>15</t:offreId>', ok('<disponible>false</disponible>', '<regle>R2</regle>')),
        op('S3', 'R3 date dépassée', 'verifierDisponibilite', '<t:offreId>13</t:offreId>', ok('<disponible>false</disponible>', '<regle>R3</regle>', 'date limite')),
        op('S3', 'R4 plus de place', 'verifierDisponibilite', '<t:offreId>14</t:offreId>', ok('<disponible>false</disponible>', '<regle>R4</regle>', 'plus de place')),
        op('S3', 'Offre inconnue', 'verifierDisponibilite', '<t:offreId>9999</t:offreId>', ok('<existe>false</existe>', '<disponible>false</disponible>')),
    ]),
    ('R7 : verifierCompatibilite (appel de S1)', [
        op('S3', 'Compatible', 'verifierCompatibilite', '<t:offreId>1</t:offreId><t:etudiantId>1</t:etudiantId>', ok('<compatible>true</compatible>', '<scoreCompetences>100</scoreCompetences>')),
        op('S3', 'R7 niveau insuffisant', 'verifierCompatibilite', '<t:offreId>1</t:offreId><t:etudiantId>5</t:etudiantId>', ok('<compatible>false</compatible>', '<niveauSuffisant>false</niveauSuffisant>', '<regle>R7</regle>')),
        op('S3', 'R7 filière non acceptée', 'verifierCompatibilite', '<t:offreId>1</t:offreId><t:etudiantId>6</t:etudiantId>', ok('<compatible>false</compatible>', '<filiereAcceptee>false</filiereAcceptee>', '<regle>R7</regle>')),
        op('S3', 'R3 date dépassée', 'verifierCompatibilite', '<t:offreId>13</t:offreId><t:etudiantId>1</t:etudiantId>', ok('<compatible>false</compatible>', '<regle>R3</regle>')),
        op('S3', 'Score faible sans blocage', 'verifierCompatibilite', '<t:offreId>10</t:offreId><t:etudiantId>3</t:etudiantId>', ok('<compatible>true</compatible>', '<scoreCompetences>0</scoreCompetences>')),
    ]),
    ('OffreIntrouvableFault', [
        op('S3', 'Consulter', 'consulterOffre', '<t:id>9999</t:id>', faute('offreIntrouvableFault', 'OFFRE_INTROUVABLE')),
        op('S3', 'Décompter', 'decrementerPlaces', '<t:offreId>9999</t:offreId>', faute('offreIntrouvableFault', 'OFFRE_INTROUVABLE')),
        op('S3', 'Compatibilité', 'verifierCompatibilite', '<t:offreId>9999</t:offreId><t:etudiantId>1</t:etudiantId>', faute('offreIntrouvableFault', 'OFFRE_INTROUVABLE')),
    ]),
    ('EtudiantIntrouvableFault', [
        op('S3', 'Compatibilité', 'verifierCompatibilite', '<t:offreId>1</t:offreId><t:etudiantId>9999</t:etudiantId>', faute('etudiantIntrouvableFault', 'ETUDIANT_INTROUVABLE')),
    ]),
    ('DonneesInvalidesFault', [
        op('S3', 'Offre vide', 'creerOffre', '<t:offre><t:titre>Seule</t:titre></t:offre>', faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('date limite', 'au moins une filière')),
        op('S3', 'Date limite passée', 'creerOffre', offre(date='2001-01-01'), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('ne peut pas être passée')),
        op('S3', 'Sans place', 'creerOffre', offre(places=0), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('au moins une place')),
        op('S3', 'R2 entreprise inactive', 'creerOffre', offre(entreprise_id=8), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient("n'est pas active")),
        op('S3', 'R2 entreprise inconnue', 'creerOffre', offre(entreprise_id=9999), faute('donneesInvalidesFault', 'DONNEES_INVALIDES') + contient('Aucune entreprise')),
    ]),
    ('ConflitFault', [
        op('S3', 'Supprimer une offre publiée', 'supprimerOffre', '<t:id>1</t:id>', faute('conflitFault', 'OFFRE_PUBLIEE')),
        op('S3', 'Modifier une offre clôturée', 'modifierOffre', '<t:id>16</t:id>' + offre(entreprise_id=7), faute('conflitFault', 'OFFRE_CLOTUREE')),
        op('S3', 'Décompter un brouillon', 'decrementerPlaces', '<t:offreId>11</t:offreId>', faute('conflitFault', 'PLUS_DE_PLACE')),
    ]),
] + cas_securite('S3', 'consulterOffre', '<t:id>1</t:id>')

# ---------- Pannes réelles : scripts\tests-soapui.ps1 arrête ce qu'il faut avant de les lancer ----------
PANNE_S3 = [
    ('S2 avec S3 arrêté', [
        op('S2', 'Offres de l entreprise', 'consulterOffresEntreprise', '<t:id>1</t:id>', faute('serviceIndisponibleFault', 'SERVICE_OFFRES_INDISPONIBLE')),
        op('S2', 'S2 reste joignable', 'verifierEntreprise', '<t:id>1</t:id>', ok('<active>true</active>')),
    ]),
]
PANNE_S1 = [
    ('S3 avec S1 arrêté', [op('S3', 'Compatibilité', 'verifierCompatibilite', '<t:offreId>1</t:offreId><t:etudiantId>1</t:etudiantId>', faute('serviceIndisponibleFault', 'SERVICE_ETUDIANTS_INDISPONIBLE'))]),
]
PANNE_BASE = [
    ('S1 sans base', [op('S1', 'Consulter', 'consulterEtudiant', '<t:id>1</t:id>', faute('serviceIndisponibleFault', 'BASE_INDISPONIBLE'))]),
    ('S2 sans base', [op('S2', 'Consulter', 'consulterEntreprise', '<t:id>1</t:id>', faute('serviceIndisponibleFault', 'BASE_INDISPONIBLE'))]),
    ('S3 sans base', [op('S3', 'Consulter', 'consulterOffre', '<t:id>1</t:id>', faute('serviceIndisponibleFault', 'BASE_INDISPONIBLE'))]),
]
SUITES = [
    ('S1 Étudiants', S1), ('S2 Entreprises', S2), ('S3 Offres', S3),
    ('Panne S3 arrêté', PANNE_S3), ('Panne S1 arrêté', PANNE_S1), ('Panne base arrêtée', PANNE_BASE),
]


def assertion_xml(genre, valeur):
    if genre == 'soap':
        return '<con:assertion type="SOAP Response" name="Réponse SOAP" id="%s"/>' % uid()
    # Dans SoapUI, les identifiants de ces deux assertions sont inversés par rapport à leur nom.
    if genre == 'fault':
        return '<con:assertion type="Not SOAP Fault Assertion" name="Faute SOAP attendue" id="%s"/>' % uid()
    if genre == 'notfault':
        return '<con:assertion type="SOAP Fault Assertion" name="Aucune faute SOAP" id="%s"/>' % uid()
    if genre == 'schema':
        return '<con:assertion type="Schema Compliance" name="Conforme au WSDL" id="%s"><con:configuration/></con:assertion>' % uid()
    if genre in ('contains', 'absent'):
        type_ = 'Simple Contains' if genre == 'contains' else 'Simple NotContains'
        nom = ('Contient : ' if genre == 'contains' else 'Ne contient pas : ') + valeur
        return (f'<con:assertion type="{type_}" name="{escape(nom, {chr(34): "&quot;"})}" id="{uid()}"><con:configuration>'
                f'<token>{escape(valeur)}</token><ignoreCase>false</ignoreCase><useRegEx>false</useRegEx></con:configuration></con:assertion>')
    raise ValueError(genre)


def etape_xml(e):
    _, chemin, ns, _, _ = SERVICES[e['service']]
    interface = SERVICES[e['service']][0] + 'Binding'
    enveloppe = (f'<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t="{ns}">\n'
                 f'   <soapenv:Header>{e["entete"]}</soapenv:Header>\n   <soapenv:Body>{e["corps"]}</soapenv:Body>\n</soapenv:Envelope>')
    assertions = ''.join(assertion_xml(g, v) for g, v in e['assertions'])
    nom = escape(e['nom'], {'"': '&quot;'})
    return (f'<con:testStep type="request" name="{nom}" id="{uid()}"><con:settings/>'
            '<con:config xsi:type="con:RequestStep" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            f'<con:interface>{interface}</con:interface><con:operation>{e["operation"]}</con:operation>'
            f'<con:request name="{nom}" id="{uid()}"><con:settings/><con:encoding>UTF-8</con:encoding>'
            f'<con:endpoint>${{#Project#url{e["service"]}}}</con:endpoint><con:request><![CDATA[{enveloppe}]]></con:request>'
            f'{assertions}<con:credentials><con:authType>No Authorization</con:authType></con:credentials>'
            '<con:jmsConfig JMSDeliveryMode="PERSISTENT"/><con:jmsPropertyConfig/><con:wsaConfig mustUnderstand="NONE" version="200508"/>'
            '<con:wsrmConfig version="1.2"/></con:request></con:config></con:testStep>')


def interface_xml(service):
    nom, chemin, ns, port, operations = SERVICES[service]
    ops = ''.join(f'<con:operation id="{uid()}" isOneWay="false" action="" name="{o}" bindingOperationName="{o}" type="Request-Response" '
                  'inputName="" receivesAttachments="false" sendsAttachments="false" anonymous="optional"><con:settings/></con:operation>' for o in operations)
    return (f'<con:interface xsi:type="con:WsdlInterface" id="{uid()}" wsaVersion="NONE" name="{nom}Binding" type="wsdl" '
            f'bindingName="{{{ns}}}{nom}Binding" soapVersion="1_1" anonymous="optional" definition="http://localhost:{port}/ws/{chemin}?wsdl" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><con:settings/>'
            f'<con:endpoints><con:endpoint>http://localhost:{port}/ws/{chemin}</con:endpoint></con:endpoints>{ops}</con:interface>')


def propriete(nom, valeur):
    return f'<con:property><con:name>{nom}</con:name><con:value>{escape(valeur)}</con:value></con:property>'


def projet():
    x = ['<?xml version="1.0" encoding="UTF-8"?>\n']
    x.append(f'<con:soapui-project id="{uid()}" activeEnvironment="Default" name="Stages SOA - services SOAP" resourceRoot="" '
             'soapui-version="5.7.2" abortOnError="false" runType="SEQUENTIAL" xmlns:con="http://eviware.com/soapui/config"><con:settings/>\n')
    for service in SERVICES:
        x.append(interface_xml(service) + '\n')
    total_cas = total_etapes = 0
    for nom, cas in SUITES:
        x.append(f'<con:testSuite id="{uid()}" name="{escape(nom)}"><con:settings/><con:runType>SEQUENTIAL</con:runType>\n')
        for titre, etapes in cas:
            total_cas += 1
            total_etapes += len(etapes)
            t = escape(titre, {'"': '&quot;'})
            x.append(f'<con:testCase id="{uid()}" failOnError="false" failTestCaseOnErrors="true" keepSession="false" maxResults="0" name="{t}" '
                     'searchProperties="true"><con:settings/>' + ''.join(etape_xml(e) for e in etapes) + '<con:properties/></con:testCase>\n')
        x.append('<con:properties/></con:testSuite>\n')
    x.append('<con:properties>')
    x.append(propriete('wsUtilisateur', 'soapui') + propriete('wsMotDePasse', 'SoapUI#Ws2026'))
    for service in SERVICES:
        _, chemin, _, port, _ = SERVICES[service]
        x.append(propriete('url' + service, f'http://localhost:{port}/ws/{chemin}'))
    x.append('</con:properties><con:wssContainer/><con:oAuth2ProfileContainer/><con:oAuth1ProfileContainer/></con:soapui-project>\n')
    with open(os.path.join(ICI, 'Stages-SOA-soapui-project.xml'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(''.join(x))
    print(f'{len(SUITES)} suites, {total_cas} cas de test, {total_etapes} requêtes')


if __name__ == '__main__':
    projet()
