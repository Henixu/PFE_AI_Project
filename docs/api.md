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

## Départements (`ADMIN`, `HR`)

| Méthode | Route | Utilité |
|---|---|---|
| `GET` | `/api/departments` | Liste paginée avec nombre d'employés. Paramètres : `search` (nom, description), `page`, `size` (max 100), `sortBy` (`name`, `createdAt`), `direction` |
| `GET` | `/api/departments/{id}` | Détail avec la liste des employés associés (actifs ou non) |
| `POST` | `/api/departments` | Création : `{name, description?}` → 201, 409 si le nom existe déjà (insensible à la casse) |
| `PUT` | `/api/departments/{id}` | Modification (même contrôle d'unicité) |
| `DELETE` | `/api/departments/{id}` | 204 ; 409 tant que des employés y sont rattachés |

## Tâches (tout utilisateur connecté, selon son périmètre)

Périmètre : `ADMIN`/`HR` voient tout ; un `MANAGER` voit les tâches qu'il a créées ou affectées à lui-même ou à ses collaborateurs directs ;
un `EMPLOYEE` voit celles qu'il a créées ou qui lui sont affectées. Une tâche hors périmètre répond 404.

| Méthode | Route | Utilité |
|---|---|---|
| `GET` | `/api/tasks` | Liste paginée. Filtres : `search`, `assigneeId`, `priority`, `status`, `overdue=true`, `mine=true`, `dueFrom`, `dueTo` (`yyyy-MM-dd`). Tri : `sortBy` (`dueDate`, `title`, `createdAt`, `updatedAt`), `direction` |
| `GET` | `/api/tasks/assignees` | Employés auxquels l'utilisateur peut affecter une tâche (RH : tous les actifs ; manager : lui + son équipe ; employé : lui) |
| `GET` | `/api/tasks/{id}` | Détail |
| `POST` | `/api/tasks` | Création : `{title, description?, priority, dueDate?, assigneeId?}` ; échéance passée refusée (400), affectation hors périmètre refusée (403) |
| `PUT` | `/api/tasks/{id}` | Modification des champs : réservée à `ADMIN`/`HR`, au créateur et au manager du périmètre (403 sinon) |
| `PATCH` | `/api/tasks/{id}/status` | `{status}` : `TODO`→`IN_PROGRESS`/`CANCELLED` ; `IN_PROGRESS`→`TODO`/`DONE`/`CANCELLED` ; `DONE`→`IN_PROGRESS` ; `CANCELLED`→`TODO`. Le responsable simple ne peut ni annuler ni rouvrir (400 si transition interdite) |

Chaque tâche renvoyée porte `overdue` (échéance dépassée et statut `TODO`/`IN_PROGRESS`), `canEdit` et `allowedStatuses` (droits de l'appelant).

## Congés

| Méthode | Route | Accès | Utilité |
|---|---|---|---|
| `POST` | `/api/leaves` | employé lié à un compte | Dépôt : `{type?, startDate, endDate, reason}` → 201, statut `PENDING`. 400 si fin < début ou sans employé associé, 409 si chevauchement avec une demande `PENDING`/`APPROVED` |
| `GET` | `/api/leaves/mine` | connecté | Ses propres demandes (`status`, `page`, `size`, `sortBy`, `direction`) |
| `GET` | `/api/leaves` | `ADMIN`, `HR`, `MANAGER` | Demandes à traiter : tous les employés (`ADMIN`/`HR`) ou les collaborateurs directs (`MANAGER`). Filtres `status`, `employeeId`, `search` |
| `GET` | `/api/leaves/{id}` | propriétaire ou décideur du périmètre | Détail (404 sinon) |
| `PATCH` | `/api/leaves/{id}/approve` | `ADMIN`, `HR`, `MANAGER` | `{comment?}` → `APPROVED` |
| `PATCH` | `/api/leaves/{id}/reject` | `ADMIN`, `HR`, `MANAGER` | `{comment}` obligatoire → `REJECTED` |
| `PATCH` | `/api/leaves/{id}/cancel` | propriétaire | Retire sa demande tant qu'elle est `PENDING` |

Une demande n'est décidée qu'une fois (409 ensuite) et jamais par son propre auteur (400). L'auteur, la date et le commentaire de la décision restent
sur la demande et dans `audit_logs` (`LEAVE_APPROVE`, `LEAVE_REJECT`). Les durées comptent les jours calendaires, bornes incluses.

Authentification : en-tête `Authorization: Bearer <jwt>`. Réponses : `401` sans jeton valide ou expiré, `403` si le rôle est insuffisant.
Règles par route : `/api/admin/**` → `ADMIN` ; `/api/audit/**` → `ADMIN`, `HR` ; le reste exige un utilisateur connecté (`@PreAuthorize` pour les règles plus fines).
La déconnexion est locale (le JWT est sans état et expire seul).

Routes prévues : voir la section 12 du [README](../README.md#12-api-rest).
