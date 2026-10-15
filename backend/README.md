# SopraHR Interne - Backend

API REST Spring Boot (Java 21) connectée à PostgreSQL.

## Structure

```text
src/main/java/com/soprahr/interne/
├── config/       Configuration (sécurité, CORS, vérification de la base)
├── security/     JWT, filtres, UserDetails
├── controller/   Contrôleurs REST
├── service/      Règles métier
├── repository/   Accès aux données (Spring Data JPA)
├── entity/       Entités JPA (+ enums : Role, TaskStatus, ...)
├── dto/          Objets d'échange et validation
├── mapper/       Conversion entité <-> DTO
└── exception/    Gestion centralisée des erreurs
```

## Connexion à la base

1. Créer la base : `CREATE DATABASE soprahr_interne;`
2. Copier `.env.example` en `.env` et renseigner `DB_USERNAME` / `DB_PASSWORD`
   (ou définir ces variables dans l'environnement).
3. Lancer : `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run`.
4. Vérifier : `GET http://localhost:8080/api/health/db`

## Schéma et migrations (Flyway)

- Le schéma est créé par Flyway au démarrage (`src/main/resources/db/migration`, `V1__create_schema.sql`).
  Hibernate ne fait que le valider (`ddl-auto=validate`) : toute évolution passe par une nouvelle migration `V<n>__*.sql`.
- Base vierge : `CREATE DATABASE soprahr_interne;` puis démarrer l'application, les tables sont créées.
- Ne jamais modifier une migration déjà appliquée.

## Données de démonstration

Jeu fictif (5 comptes, 3 départements, tâches, congés, documents, notifications, audit), activé uniquement avec le profil `demo` :

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Comptes : `admin@`, `rh@`, `manager@`, `alice@`, `bob@` `demo.soprahr.test`, mot de passe `Demo@1234`.
Nécessite l'extension PostgreSQL `pgcrypto` (créée automatiquement par la migration). Pour repartir de zéro :
`DROP DATABASE soprahr_interne; CREATE DATABASE soprahr_interne;`.
