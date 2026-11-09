# Appeler les services SOAP : en-tête de sécurité

Chaque appel à S1, S2 ou S3 doit porter un en-tête WS-Security **UsernameToken** avec un mot de passe de type **PasswordText**.
La lecture du WSDL (`?wsdl`) ne demande aucune authentification.

## En-tête à envoyer

À copier tel quel dans `soapenv:Header`, pour SoapUI comme pour Bonita :

```xml
<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
  <wsse:UsernameToken>
    <wsse:Username>bonita</wsse:Username>
    <wsse:Password>Bonita#Ws2026</wsse:Password>
  </wsse:UsernameToken>
</wsse:Security>
```

L'attribut `Type` du mot de passe est facultatif. S'il est présent, il doit valoir `...#PasswordText`.
L'attribut `soapenv:mustUnderstand="1"` est accepté.

## Exemple complet

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:etu="http://stages.inpt.ma/etudiants">
  <soapenv:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>bonita</wsse:Username>
        <wsse:Password>Bonita#Ws2026</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
    <cor:CorrelationId xmlns:cor="http://stages.inpt.ma/commun">demande-42</cor:CorrelationId>
  </soapenv:Header>
  <soapenv:Body>
    <etu:verifierEtudiant>
      <etu:id>1</etu:id>
    </etu:verifierEtudiant>
  </soapenv:Body>
</soapenv:Envelope>
```

`POST http://localhost:8081/ws/etudiants`, avec `Content-Type: text/xml; charset=utf-8` et `SOAPAction: ""`.

`CorrelationId` est facultatif : il permet de suivre une candidature dans les journaux de tous les services (dossier `logs`). L'en-tête HTTP `X-Correlation-Id` est aussi accepté.

## Comptes de service (démonstration)

| Compte | Mot de passe | Utilisé par |
|---|---|---|
| `portail` | `Portail#Ws2026` | Portail |
| `bonita` | `Bonita#Ws2026` | Processus Bonita |
| `services` | `Services#Ws2026` | Appels entre services (S2 vers S3, S3 vers S1 et S2) |
| `soapui` | `SoapUI#Ws2026` | Tests SoapUI |

Le serveur ne stocke que les hachages BCrypt (`soa.ws.comptes` dans `soa-defaults.properties`). Ce paramètre se remplace par la variable d'environnement `SOA_WS_COMPTES`.

## Réponses en cas de refus

| Situation | `faultcode` | Message |
|---|---|---|
| En-tête absent | `wsse:InvalidSecurity` | En-tête de sécurité WS-Security UsernameToken absent. |
| `Username` ou `Password` manquant | `wsse:InvalidSecurity` | Le jeton de sécurité doit contenir Username et Password. |
| Mot de passe de type digest | `wsse:UnsupportedSecurityToken` | Seul le type de mot de passe PasswordText est accepté. |
| Compte inconnu ou mot de passe faux | `wsse:FailedAuthentication` | Identifiants de service invalides. |

Le statut HTTP d'une faute SOAP est **500**, comme le prévoit SOAP 1.1.

## Limite à connaître

Sans HTTPS, ce mot de passe circule en clair sur le réseau. C'est acceptable sur une seule machine pour la démonstration, jamais en production : le rapport explique comment activer TLS.
