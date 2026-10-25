-- Modules documents et notifications

-- Documents : portée de visibilité. PRIVATE = propriétaire + ADMIN/HR ; INTERNAL = tout utilisateur connecté.
ALTER TABLE documents
    ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE documents
    ADD CONSTRAINT ck_documents_visibility CHECK (visibility IN ('PRIVATE', 'INTERNAL'));
CREATE INDEX idx_documents_visibility ON documents (visibility);

-- Notifications : catégorie, lien d'ouverture dans l'interface et clé de déduplication des alertes planifiées.
ALTER TABLE notifications
    ADD COLUMN type      VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN link      VARCHAR(200),
    ADD COLUMN dedup_key VARCHAR(100);
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN ('GENERAL', 'TASK_ASSIGNED', 'LEAVE_DECISION', 'TASK_DUE'));

-- Une même alerte (ex. échéance d'une tâche) n'est créée qu'une fois par destinataire, même en cas de double exécution.
CREATE UNIQUE INDEX uq_notifications_dedup ON notifications (recipient_id, dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
