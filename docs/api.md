# API REST

| Méthode | Route | Accès | Utilité |
|---|---|---|---|
| `GET` | `/api/health` | public | État de l'API |
| `GET` | `/api/health/db` | `ADMIN` | État de la connexion PostgreSQL |
| `POST` | `/api/auth/login` | public | Connexion : `{email, password}` → `{token, tokenType, expiresAt, user}` |
| `GET` | `/api/auth/me` | connecté | Profil du compte connecté |
| `POST` | `/api/auth/forgot-password` | public | Demande de lien de réinitialisation (réponse identique que le compte existe ou non) |
| `POST` | `/api/auth/reset-password` | public | `{token, newPassword}` ; jeton valable 30 min, à usage unique |

## Administration des comptes (rôle `ADMIN` uniquement)

| Méthode | Route | Utilité |
|---|---|---|
| `GET` | `/api/admin/users` | Liste paginée. Paramètres : `search` (e-mail), `role`, `enabled`, `page`, `size` (max 100), `sortBy` (`email`, `role`, `enabled`, `createdAt`, `lastLoginAt`), `direction` |
| `GET` | `/api/admin/users/{id}` | Détail d'un compte |
| `POST` | `/api/admin/users` | Création : `{email, password, role, enabled?, employeeId?}` → 201, 409 si l'e-mail existe |
| `PUT` | `/api/admin/users/{id}` | Modification de l'e-mail : `{email}` |
| `PATCH` | `/api/admin/users/{id}/role` | Changement de rôle : `{role}` (audité) |
| `PATCH` | `/api/admin/users/{id}/status` | Activation / désactivation : `{enabled}` (audité) |
| `PUT` | `/api/admin/users/{id}/employee` | Association à un employé : `{employeeId}` (409 si l'un des deux est déjà associé) |
| `DELETE` | `/api/admin/users/{id}/employee` | Retire l'association |
| `GET` | `/api/admin/users/linkable-employees` | Employés sans compte |

Garde-fous : on ne peut ni changer son propre rôle, ni se désactiver, ni retirer le dernier administrateur actif (400).
Un compte désactivé ne peut plus se connecter et son jeton en cours est refusé (401). Les créations, changements de rôle,
activations/désactivations et associations sont inscrits dans `audit_logs` (jamais de mot de passe).

Authentification : en-tête `Authorization: Bearer <jwt>`. Réponses : `401` sans jeton valide ou expiré, `403` si le rôle est insuffisant.
Règles par route : `/api/admin/**` → `ADMIN` ; `/api/audit/**` → `ADMIN`, `HR` ; le reste exige un utilisateur connecté (`@PreAuthorize` pour les règles plus fines).
La déconnexion est locale (le JWT est sans état et expire seul).

Routes prévues : voir la section 12 du [README](../README.md#12-api-rest).
