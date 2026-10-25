export type NotificationType = 'GENERAL' | 'TASK_ASSIGNED' | 'LEAVE_DECISION' | 'TASK_DUE';

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  GENERAL: 'Information',
  TASK_ASSIGNED: 'Tâche',
  LEAVE_DECISION: 'Congé',
  TASK_DUE: 'Échéance'
};

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string | null;
  /** Route de l'application à ouvrir depuis la notification (ex. /tasks). */
  link: string | null;
  read: boolean;
  createdAt: string;
  readAt: string | null;
}
