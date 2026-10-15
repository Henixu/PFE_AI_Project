-- Jeu de données FICTIF pour le développement (profil Spring "demo" uniquement).
-- Tous les comptes ont le mot de passe : Demo@1234
-- Le hash BCrypt est calculé par pgcrypto (compatible avec BCryptPasswordEncoder).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, email, password_hash, role) VALUES
    (1, 'admin@demo.soprahr.test',   crypt('Demo@1234', gen_salt('bf', 10)), 'ADMIN'),
    (2, 'rh@demo.soprahr.test',      crypt('Demo@1234', gen_salt('bf', 10)), 'HR'),
    (3, 'manager@demo.soprahr.test', crypt('Demo@1234', gen_salt('bf', 10)), 'MANAGER'),
    (4, 'alice@demo.soprahr.test',   crypt('Demo@1234', gen_salt('bf', 10)), 'EMPLOYEE'),
    (5, 'bob@demo.soprahr.test',     crypt('Demo@1234', gen_salt('bf', 10)), 'EMPLOYEE');

INSERT INTO departments (id, name, description) VALUES
    (1, 'Ressources humaines', 'Gestion du personnel et des congés'),
    (2, 'Développement',       'Équipe de développement logiciel'),
    (3, 'Direction',           'Direction générale');

INSERT INTO employees (id, employee_number, first_name, last_name, job_title, phone, hire_date, user_id, department_id, manager_id) VALUES
    (1, 'EMP-001', 'Sami',  'Admin',   'Administrateur système', '+216 20 000 001', '2020-01-15', 1, 3, NULL),
    (2, 'EMP-002', 'Rania', 'Rh',      'Responsable RH',         '+216 20 000 002', '2020-03-01', 2, 1, 1),
    (3, 'EMP-003', 'Karim', 'Manager', 'Chef d''équipe',         '+216 20 000 003', '2021-06-10', 3, 2, 1),
    (4, 'EMP-004', 'Alice', 'Dupont',  'Développeuse',           '+216 20 000 004', '2022-09-05', 4, 2, 3),
    (5, 'EMP-005', 'Bob',   'Martin',  'Développeur',            '+216 20 000 005', '2023-02-20', 5, 2, 3);

INSERT INTO tasks (id, title, description, priority, status, due_date, assignee_id, created_by) VALUES
    (1, 'Mettre en place l''authentification', 'Connexion JWT et rôles',       'HIGH',   'IN_PROGRESS', CURRENT_DATE + 7,  4, 3),
    (2, 'Créer la page des employés',          'Liste paginée et recherche',   'MEDIUM', 'TODO',        CURRENT_DATE + 14, 5, 3),
    (3, 'Rédiger la documentation API',        NULL,                           'LOW',    'TODO',        CURRENT_DATE + 30, 4, 3),
    (4, 'Corriger le bug du tableau de bord',  'Graphique des congés erroné',  'URGENT', 'DONE',        CURRENT_DATE - 2,  5, 3);

INSERT INTO leave_requests (id, employee_id, type, start_date, end_date, reason, status, decided_by, decided_at, decision_comment) VALUES
    (1, 4, 'ANNUAL', CURRENT_DATE + 20, CURRENT_DATE + 27, 'Vacances',         'PENDING',  NULL, NULL,  NULL),
    (2, 5, 'SICK',   CURRENT_DATE - 10, CURRENT_DATE - 8,  'Grippe',           'APPROVED', 2,    now(), 'Bon rétablissement'),
    (3, 4, 'OTHER',  CURRENT_DATE + 3,  CURRENT_DATE + 3,  'Rendez-vous',      'REJECTED', 2,    now(), 'Période de livraison');

INSERT INTO documents (id, title, file_name, content_type, size_bytes, storage_path, owner_id) VALUES
    (1, 'Règlement intérieur', 'reglement-interieur.pdf', 'application/pdf', 204800, 'demo/reglement-interieur.pdf', 2),
    (2, 'Contrat Alice Dupont', 'contrat-alice.pdf',      'application/pdf', 102400, 'demo/contrat-alice.pdf',       2);

INSERT INTO notifications (id, recipient_id, title, message, is_read) VALUES
    (1, 4, 'Nouvelle tâche',          'La tâche "Mettre en place l''authentification" vous a été affectée.', FALSE),
    (2, 4, 'Congé refusé',            'Votre demande du jour J+3 a été refusée.',                           FALSE),
    (3, 5, 'Congé approuvé',          'Votre arrêt maladie a été validé.',                                   TRUE),
    (4, 2, 'Demande de congé reçue',  'Alice Dupont a soumis une demande de congé annuel.',                  FALSE);

INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, details, ip_address) VALUES
    (1, 1, 'CREATE', 'User',         4, 'Création du compte alice@demo.soprahr.test', '127.0.0.1'),
    (2, 3, 'CREATE', 'Task',         1, 'Création de la tâche #1',                    '127.0.0.1'),
    (3, 2, 'REJECT', 'LeaveRequest', 3, 'Demande de congé refusée',                   '127.0.0.1');

-- Réaligne les séquences d'identité après les insertions à id explicite
SELECT setval(pg_get_serial_sequence('users', 'id'),          (SELECT max(id) FROM users));
SELECT setval(pg_get_serial_sequence('departments', 'id'),    (SELECT max(id) FROM departments));
SELECT setval(pg_get_serial_sequence('employees', 'id'),      (SELECT max(id) FROM employees));
SELECT setval(pg_get_serial_sequence('tasks', 'id'),          (SELECT max(id) FROM tasks));
SELECT setval(pg_get_serial_sequence('leave_requests', 'id'), (SELECT max(id) FROM leave_requests));
SELECT setval(pg_get_serial_sequence('documents', 'id'),      (SELECT max(id) FROM documents));
SELECT setval(pg_get_serial_sequence('notifications', 'id'),  (SELECT max(id) FROM notifications));
SELECT setval(pg_get_serial_sequence('audit_logs', 'id'),     (SELECT max(id) FROM audit_logs));
