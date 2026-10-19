export type LeaveType = 'ANNUAL' | 'SICK' | 'UNPAID' | 'OTHER';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export const LEAVE_TYPES: LeaveType[] = ['ANNUAL', 'SICK', 'UNPAID', 'OTHER'];
export const LEAVE_STATUSES: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];

export const LEAVE_TYPE_LABELS: Record<LeaveType, string> = {
  ANNUAL: 'Congé annuel',
  SICK: 'Maladie',
  UNPAID: 'Sans solde',
  OTHER: 'Autre'
};

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  PENDING: 'En attente',
  APPROVED: 'Approuvée',
  REJECTED: 'Refusée',
  CANCELLED: 'Annulée'
};

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeNumber: string;
  departmentName: string | null;
  type: LeaveType;
  startDate: string;
  endDate: string;
  /** Jours calendaires, bornes incluses. */
  durationDays: number;
  reason: string;
  status: LeaveStatus;
  createdAt: string;
  /** Décision : auteur, date et commentaire, conservés une fois la demande traitée. */
  decidedBy: string | null;
  decidedAt: string | null;
  decisionComment: string | null;
  canDecide: boolean;
  canCancel: boolean;
}

export interface LeaveQuery {
  status: LeaveStatus | null;
  search: string;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

export interface LeavePayload {
  type: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
}
