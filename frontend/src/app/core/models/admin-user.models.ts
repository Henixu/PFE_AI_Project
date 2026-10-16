import { Role } from './auth.models';

export interface UserSummary {
  id: number;
  email: string;
  role: Role;
  enabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  employeeId: number | null;
  employeeName: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EmployeeOption {
  id: number;
  employeeNumber: string;
  fullName: string;
  jobTitle: string | null;
}

export interface UserQuery {
  search?: string;
  role?: Role | '';
  enabled?: boolean | null;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

export interface CreateUserPayload {
  email: string;
  password: string;
  role: Role;
  enabled: boolean;
  employeeId: number | null;
}
