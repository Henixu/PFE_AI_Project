# API REST

| Méthode | Route | Accès | Utilité |
|---|---|---|---|
| `GET` | `/api/health` | public | État de l'API |
| `GET` | `/api/health/db` | `ADMIN` | État de la connexion PostgreSQL |
| `POST` | `/api/auth/login` | public | Connexion : `{email, password}` → `{token, tokenType, expiresAt, user}` |
| `GET` | `/api/auth/me` | connecté | Profil du compte connecté |
| `POST` | `/api/auth/forgot-password` | public | Demande de lien de réinitialisation (réponse identique que le compte existe ou non) |
| `POST` | `/api/auth/reset-password` | public | `{token, newPassword}` ; jeton valable 30 min, à usage unique |

Authentification : en-tête `Authorization: Bearer <jwt>`. Réponses : `401` sans jeton valide ou expiré, `403` si le rôle est insuffisant.
Règles par route : `/api/admin/**` → `ADMIN` ; `/api/audit/**` → `ADMIN`, `HR` ; le reste exige un utilisateur connecté (`@PreAuthorize` pour les règles plus fines).
La déconnexion est locale (le JWT est sans état et expire seul).

Routes prévues : voir la section 12 du [README](../README.md#12-api-rest).
