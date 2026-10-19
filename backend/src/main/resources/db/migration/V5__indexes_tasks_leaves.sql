-- Index des filtres et contrôles des modules tâches et congés
CREATE INDEX idx_tasks_due_date ON tasks (due_date);
CREATE INDEX idx_tasks_created_by ON tasks (created_by);
CREATE INDEX idx_leave_employee_dates ON leave_requests (employee_id, start_date, end_date);
