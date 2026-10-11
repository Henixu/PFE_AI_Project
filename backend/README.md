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
