export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'];
export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
  CANCELLED: 'Annulée'
};

export const TASK_PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Basse',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  URGENT: 'Urgente'
};

export interface Task {
  id: number;
  title: string;
  description: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  dueDate: string | null;
  /** Échéance dépassée et tâche ni terminée ni annulée (calculé par le serveur). */
  overdue: boolean;
  assigneeId: number | null;
  assigneeName: string | null;
  createdByEmail: string;
  createdAt: string;
  updatedAt: string;
  /** L'utilisateur connecté peut modifier les champs de la tâche. */
  canEdit: boolean;
  /** Statuts vers lesquels l'utilisateur connecté peut déplacer la tâche. */
  allowedStatuses: TaskStatus[];
}

export interface TaskQuery {
  search: string;
  assigneeId: number | null;
  priority: TaskPriority | null;
  status: TaskStatus | null;
  overdue: boolean;
  mine: boolean;
  dueFrom: string;
  dueTo: string;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

export interface TaskPayload {
  title: string;
  description: string | null;
  priority: TaskPriority;
  dueDate: string | null;
  assigneeId: number | null;
}
