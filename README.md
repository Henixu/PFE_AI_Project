import pypandoc

readme = r"""# SopraHR Interne

Application web de gestion interne d’entreprise développée avec **Angular**, **Spring Boot** et **PostgreSQL**.

L’objectif de **SopraHR Interne** est de centraliser les informations et les activités internes d’une entreprise dans une seule plateforme. Selon son rôle, chaque utilisateur peut consulter ou gérer les employés, les départements, les tâches, les demandes de congés et les documents. Un tableau de bord permet également de suivre les principales statistiques.

> **Statut du projet :** en cours de conception et de développement. Les fonctionnalités décrites dans ce README représentent le périmètre prévu ; elles ne sont pas nécessairement toutes implémentées à ce stade.

---

## Sommaire

- [1. Objectifs](#1-objectifs)
- [2. Fonctionnalités](#2-fonctionnalités)
- [3. Rôles et permissions](#3-rôles-et-permissions)
- [4. Technologies utilisées](#4-technologies-utilisées)
- [5. Architecture](#5-architecture)
- [6. Modules de l’application](#6-modules-de-lapplication)
- [7. Modèle de données](#7-modèle-de-données)
- [8. Prérequis](#8-prérequis)
- [9. Installation](#9-installation)
- [10. Configuration](#10-configuration)
- [11. Lancement du projet](#11-lancement-du-projet)
- [12. API REST](#12-api-rest)
- [13. Sécurité](#13-sécurité)
- [14. Tests](#14-tests)
- [15. Organisation du dépôt](#15-organisation-du-dépôt)
- [16. Améliorations prévues](#16-améliorations-prévues)

---

## 1. Objectifs

SopraHR Interne vise à :

- centraliser les informations relatives aux employés et aux départements ;
- faciliter l’organisation et le suivi des tâches ;
- simplifier la création et le traitement des demandes de congés ;
- permettre le partage contrôlé de documents internes ;
- afficher des statistiques utiles aux responsables ;
- protéger les données grâce à une authentification et à des permissions par rôle ;
- conserver un historique des actions importantes pour faciliter le suivi.

L’application est conçue selon une architecture séparant le frontend, le backend et la base de données. Cette séparation facilite la maintenance, les tests et l’évolution du projet.

## 2. Fonctionnalités

### 2.1 Authentification et comptes

- Connexion avec adresse e-mail et mot de passe.
- Gestion sécurisée des mots de passe.
- Authentification par JWT pour les appels à l’API.
- Accès aux fonctionnalités selon le rôle de l’utilisateur.
- Consultation et modification des informations autorisées du profil.

La création de comptes et l’attribution des rôles doivent être contrôlées. En production, l’inscription publique ne doit pas permettre à un utilisateur de devenir administrateur.

### 2.2 Gestion des employés

- Ajouter, consulter, modifier et désactiver un employé.
- Enregistrer les coordonnées et les informations professionnelles.
- Associer un employé à un département.
- Rechercher des employés et filtrer les résultats.
- Afficher les informations détaillées d’un employé.

Exemples d’informations : nom, prénom, e-mail professionnel, téléphone, poste, date d’embauche, statut et département.

### 2.3 Gestion des départements

- Créer et modifier un département.
- Consulter la liste des départements.
- Afficher les employés associés à un département.
- Rechercher et filtrer les départements.

La suppression d’un département doit tenir compte des employés qui y sont encore rattachés.

### 2.4 Gestion des tâches

- Créer une tâche et lui donner un titre et une description.
- Affecter une tâche à un employé.
- Définir une priorité et une date limite.
- Mettre à jour son état d’avancement.
- Rechercher et filtrer les tâches.

États prévus : `TODO`, `IN_PROGRESS`, `DONE` et `CANCELLED`.

Priorités prévues : `LOW`, `MEDIUM`, `HIGH` et `URGENT`.

### 2.5 Gestion des congés

- Permettre à un employé de soumettre une demande.
- Enregistrer les dates et le motif de la demande.
- Consulter l’état d’une demande.
- Autoriser les personnes habilitées à approuver ou à refuser la demande.
- Conserver la décision et son auteur.

États prévus : `PENDING`, `APPROVED` et `REJECTED`.

Les dates doivent être validées : la date de fin ne peut pas précéder la date de début. Des règles supplémentaires, comme la détection de demandes qui se chevauchent, peuvent être ajoutées.

### 2.6 Gestion des documents

- Ajouter un document.
- Consulter les métadonnées du document.
- Télécharger un document si l’utilisateur y est autorisé.
- Supprimer un document selon les permissions prévues.

Les fichiers sont stockés dans un emplacement dédié ou dans un stockage adapté. PostgreSQL conserve principalement leurs métadonnées et leur référence de stockage. Les types et tailles de fichiers doivent être contrôlés.

### 2.7 Notifications

L’application pourra informer les utilisateurs d’événements importants, par exemple :

- attribution d’une nouvelle tâche ;
- approbation ou refus d’une demande de congé ;
- échéance prochaine d’une tâche ;
- ajout d’une notification par un responsable.

Une notification peut être marquée comme lue.

### 2.8 Tableau de bord

Le tableau de bord présentera des indicateurs calculés à partir des données réelles, par exemple :

- nombre d’employés actifs ;
- nombre de départements ;
- tâches par état ;
- tâches en retard ;
- demandes de congés en attente ;
- répartition des employés par département ;
- activités récentes autorisées à l’utilisateur.

Les chiffres affichés seront fournis par le backend et non écrits en dur dans le frontend.

### 2.9 Historique des actions (audit)

Un journal d’audit pourra enregistrer les opérations importantes : création ou modification d’un employé, décision sur un congé ou changement de rôle.

Les entrées peuvent contenir l’utilisateur à l’origine de l’action, le type d’action, l’entité concernée, sa référence, une description et la date. Les mots de passe, jetons et autres secrets ne doivent jamais être enregistrés dans ces journaux.

### 2.10 Recherche, pagination et export

- Recherche et filtres sur les listes.
- Tri des résultats.
- Pagination côté serveur pour les listes volumineuses.
- Export de rapports en PDF ou Excel, selon le module et les besoins.

---

## 3. Rôles et permissions

Les rôles prévus sont les suivants :

| Rôle | Responsabilités principales |
|---|---|
| `ADMIN` | Administration des comptes, rôles et paramètres ; accès aux fonctions d’administration. |
| `HR` | Gestion des employés et traitement des demandes de congés selon les permissions définies. |
| `MANAGER` | Suivi de son équipe et gestion des tâches ; traitement des demandes uniquement si autorisé. |
| `EMPLOYEE` | Consultation de son profil, accès à ses tâches et soumission de demandes de congés. |

Les permissions doivent être vérifiées **dans le backend** avec Spring Security. Masquer un bouton ou une page dans Angular ne suffit pas à sécuriser une opération. L’accès aux documents et aux données d’une équipe doit aussi être vérifié côté serveur.

---

## 4. Technologies utilisées

### Frontend

- **Angular** : interface web et navigation entre les pages.
- **TypeScript** : typage et logique du frontend.
- **Angular Material** : composants d’interface tels que tableaux, formulaires, menus et fenêtres de dialogue.
- **RxJS** : gestion des flux asynchrones.
- **HttpClient** : communication avec l’API REST.

### Backend

- **Java 21** : langage de programmation prévu.
- **Spring Boot** : application backend.
- **Spring Web** : création des API REST.
- **Spring Data JPA / Hibernate** : accès aux données et mapping objet-relationnel.
- **Spring Security** : authentification et autorisation.
- **JWT** : transmission du jeton d’authentification.
- **Bean Validation** : validation des données reçues.
- **Maven** : gestion des dépendances et construction du backend.
- **Lombok** (optionnel) : réduction du code répétitif.

### Base de données et outils

- **PostgreSQL** : stockage relationnel.
- **Postman** : tests manuels des API.
- **Git et GitHub** : versionnement du code.
- **Swagger / OpenAPI** : documentation interactive de l’API, à ajouter.
- **JUnit et Mockito** : tests du backend.

Les versions exactes des dépendances doivent être fixées dans les fichiers de configuration du projet.

---

## 5. Architecture

```text
Utilisateur
    |
    v
Angular (interface web)
    |
    | HTTP / HTTPS - JSON
    v
Spring Boot (API REST)
    |
    +-- Controllers : réception des requêtes
    +-- Services : règles métier
    +-- Repositories : accès aux données
    +-- Security : authentification et autorisation
    +-- DTO / Validation : échanges et contrôle des données
    |
    v
PostgreSQL
```

Le frontend ne se connecte jamais directement à PostgreSQL. Il envoie des requêtes HTTP au backend. Le backend valide les demandes, vérifie les permissions et accède à la base de données.

### Exemple de traitement

1. Un utilisateur ouvre la page des employés dans Angular.
2. Angular appelle `GET /api/employees`.
3. Spring Security vérifie l’authentification et les permissions.
4. Le contrôleur appelle le service métier.
5. Le service utilise le repository pour lire les données.
6. Spring Boot renvoie une réponse JSON au frontend.

---

## 6. Modules de l’application

| Module | Pages prévues |
|---|---|
| Authentification | Connexion, profil |
| Tableau de bord | Indicateurs, graphiques, activités récentes |
| Employés | Liste, détails, création, modification |
| Départements | Liste, détails, création, modification |
| Tâches | Liste ou tableau Kanban, détails, affectation |
| Congés | Mes demandes, nouvelle demande, traitement des demandes |
| Documents | Liste, ajout, téléchargement |
| Notifications | Liste, lecture des notifications |
| Administration | Comptes, rôles, paramètres |
| Audit | Historique des actions autorisées |

---

## 7. Modèle de données

Le modèle de données initial pourra comprendre les tables suivantes :

| Table | Rôle |
|---|---|
| `users` | Comptes de connexion, e-mail, mot de passe haché, rôle et état du compte. |
| `employees` | Informations professionnelles des employés. |
| `departments` | Départements de l’entreprise. |
| `tasks` | Tâches, priorité, état, échéance et affectation. |
| `leave_requests` | Demandes de congés et décisions. |
| `documents` | Métadonnées et référence de stockage des fichiers. |
| `notifications` | Notifications destinées aux utilisateurs. |
| `audit_logs` | Historique des opérations importantes. |

### Relations principales

- Un département peut regrouper plusieurs employés.
- Un employé peut se voir affecter plusieurs tâches.
- Un employé peut soumettre plusieurs demandes de congés.
- Un utilisateur peut recevoir plusieurs notifications.
- Un utilisateur peut effectuer plusieurs actions enregistrées dans le journal d’audit.
- Les tâches et les demandes de congés référencent les utilisateurs responsables de leur création ou de leur traitement, selon le modèle retenu.

Les clés primaires, clés étrangères, contraintes d’unicité et règles de suppression doivent être définies explicitement. Il faut éviter de supprimer automatiquement un historique métier important lorsqu’un compte est désactivé.

---

## 8. Prérequis

Installer les outils suivants sur la machine :

- JDK 21 ;
- Node.js et npm, dans des versions compatibles avec la version d’Angular choisie ;
- Angular CLI ;
- PostgreSQL ;
- Git ;
- un éditeur comme IntelliJ IDEA ou VS Code ;
- Postman, pour tester les API.

Vérifier les installations :

```bash
java -version
node -v
npm -v
ng version
git --version
psql --version
```

Les commandes ci-dessous supposent que le backend Spring Boot et le frontend Angular ont déjà été créés dans les dossiers `backend/` et `frontend/`.

---

## 9. Installation

### 9.1 Récupérer le projet

```bash
git clone <URL_DU_DEPOT_GITHUB>
cd SopraHR-Interne
```

Remplacer `<URL_DU_DEPOT_GITHUB>` par l’adresse réelle du dépôt.

### 9.2 Préparer PostgreSQL

Dans PostgreSQL, créer la base :

```sql
CREATE DATABASE soprahr_interne;
```

Créer un utilisateur dédié si nécessaire, puis lui accorder uniquement les permissions requises sur la base et le schéma. Les commandes exactes dépendent de la configuration PostgreSQL locale.

### 9.3 Installer le backend

```bash
cd backend
```

Avec Maven Wrapper, sous Windows :

```powershell
.\mvnw.cmd clean install
```

Sous Linux ou macOS :

```bash
./mvnw clean install
```

Si le dépôt ne contient pas de Maven Wrapper, utiliser Maven installé localement.

### 9.4 Installer le frontend

Dans un autre terminal :

```bash
cd frontend
npm install
```

---

## 10. Configuration

### 10.1 Configuration backend

Configurer la connexion PostgreSQL et les secrets avec des variables d’environnement. Par exemple, le fichier `application.properties` peut utiliser :

```properties
spring.application.name=soprahr-interne
server.port=${SERVER_PORT:8080}

spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/soprahr_interne}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
```

`ddl-auto=update` peut être pratique pendant le développement initial. Pour une application déployée, préférer des migrations versionnées avec Flyway ou Liquibase et configurer Hibernate pour valider le schéma.

Les variables `DB_USERNAME`, `DB_PASSWORD` et le secret JWT doivent être définis dans l’environnement avant le lancement. Ne jamais publier de vrais secrets dans GitHub.

### 10.2 Configuration frontend

Configurer l’URL de l’API dans l’environnement Angular utilisé par le projet, par exemple :

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

Adapter le chemin et le fichier à la version d’Angular et à l’organisation réellement utilisée.

---

## 11. Lancement du projet

### 11.1 Démarrer PostgreSQL

Vérifier que le service PostgreSQL est démarré et que la base `soprahr_interne` est accessible.

### 11.2 Démarrer Spring Boot

Depuis `backend/`, sous Windows :

```powershell
.\mvnw.cmd spring-boot:run
```

Sous Linux ou macOS :

```bash
./mvnw spring-boot:run
```

Le backend est prévu pour être accessible à l’adresse :

`http://localhost:8080`

### 11.3 Démarrer Angular

Depuis `frontend/` :

```bash
ng serve
```

L’interface est généralement accessible à l’adresse :

`http://localhost:4200`

Ces adresses sont les valeurs de développement par défaut ; elles peuvent changer selon la configuration du projet.

---

## 12. API REST

Les routes ci-dessous constituent une proposition d’API. Elles doivent être alignées sur les contrôleurs effectivement développés.

| Méthode | Route | Utilité |
|---|---|---|
| `POST` | `/api/auth/login` | Connexion |
| `GET` | `/api/employees` | Lister les employés |
| `GET` | `/api/employees/{id}` | Consulter un employé |
| `POST` | `/api/employees` | Créer un employé |
| `PUT` | `/api/employees/{id}` | Modifier un employé |
| `DELETE` | `/api/employees/{id}` | Supprimer ou désactiver selon les règles |
| `GET` | `/api/departments` | Lister les départements |
| `POST` | `/api/departments` | Créer un département |
| `GET` | `/api/tasks` | Lister les tâches autorisées |
| `POST` | `/api/tasks` | Créer une tâche |
| `PUT` | `/api/tasks/{id}` | Modifier une tâche |
| `GET` | `/api/leaves` | Consulter les demandes autorisées |
| `POST` | `/api/leaves` | Soumettre une demande de congé |
| `PUT` | `/api/leaves/{id}/approve` | Approuver une demande |
| `PUT` | `/api/leaves/{id}/reject` | Refuser une demande |
| `GET` | `/api/dashboard/statistics` | Obtenir les statistiques |

Pour les listes volumineuses, prévoir la pagination, par exemple avec les paramètres `page`, `size` et `sort`. Les routes d’administration et les opérations de modification doivent être protégées par les permissions correspondantes.

---

## 13. Sécurité

Mesures à mettre en place :

- hacher les mots de passe avec BCrypt ;
- utiliser des jetons JWT signés et une durée d’expiration adaptée ;
- vérifier les rôles et les permissions côté backend ;
- valider toutes les données reçues par l’API ;
- configurer CORS pour les origines autorisées ;
- protéger les secrets avec des variables d’environnement ;
- contrôler le type et la taille des fichiers téléversés ;
- empêcher l’accès aux données d’un autre employé sans autorisation ;
- éviter d’enregistrer des mots de passe ou des jetons dans les logs ;
- utiliser HTTPS en production.

Le stockage du JWT côté frontend doit être choisi en tenant compte des risques XSS et CSRF. Le choix entre cookie sécurisé et stockage côté navigateur doit être cohérent avec la configuration de sécurité retenue.

---

## 14. Tests

### Tests backend

Prévoir des tests pour :

- les services métier ;
- les repositories ;
- les contrôleurs REST ;
- la validation des données ;
- l’authentification ;
- les permissions de chaque rôle ;
- les demandes de congés ;
- les erreurs et les cas limites.

Exécuter les tests Maven :

```bash
./mvnw test
```

Sous Windows PowerShell :

```powershell
.\mvnw.cmd test
```

### Tests frontend

Exécuter les tests configurés dans le projet Angular :

```bash
ng test
```

Vérifier notamment la connexion, les formulaires, les services HTTP, les guards et les principaux parcours utilisateur.

### Tests manuels

Avec Postman, tester les cas suivants :

1. Connexion avec des identifiants valides et invalides.
2. Appel d’une route protégée sans jeton.
3. Appel d’une route avec un rôle insuffisant.
4. Création et modification d’un employé.
5. Soumission et traitement d’une demande de congé.
6. Validation des entrées incorrectes.
7. Contrôle des accès aux documents.

---

## 15. Organisation du dépôt

Organisation proposée :

```text
SopraHR-Interne/
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── README.md
├── frontend/
│   ├── src/
│   ├── package.json
│   └── README.md
├── database/
│   ├── migrations/
│   └── diagrams/
├── docs/
│   ├── architecture.md
│   ├── api.md
│   └── screenshots/
├── .gitignore
└── README.md
```

Cette arborescence est indicative. Elle peut être adaptée à la structure réelle du dépôt.

---

## 16. Améliorations prévues

Les améliorations peuvent être développées progressivement :

1. Finaliser l’authentification JWT et les permissions.
2. Terminer les modules employés et départements.
3. Ajouter les tâches et leur suivi.
4. Ajouter les demandes de congés et leur workflow.
5. Mettre en place l’upload sécurisé des documents.
6. Développer les notifications.
7. Construire le tableau de bord avec des statistiques calculées.
8. Ajouter l’audit, la recherche et la pagination.
9. Ajouter l’export PDF/Excel et la documentation Swagger.
10. Renforcer les tests, préparer les migrations de base de données et déployer l’application.

---

## Conclusion

**SopraHR Interne** est un projet de gestion interne qui met en pratique le développement frontend avec Angular, la création d’API REST avec Spring Boot, la persistance des données avec PostgreSQL et la sécurisation des accès avec Spring Security et JWT.

Le développement doit se faire module par module, en validant chaque partie avant de passer à la suivante. Le README décrit la cible fonctionnelle et les procédures prévues ; il faudra le mettre à jour à mesure que les fonctionnalités sont réellement développées.
"""

output_path = "/mnt/data/README_SopraHR_Interne.md"
pypandoc.convert_text(readme, "md", format="md", outputfile=output_path, extra_args=["--standalone"])
print(f"Fichier créé : {output_path}")
