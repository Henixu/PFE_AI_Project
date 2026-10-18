export interface Employee {
  id: number;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  phone: string | null;
  hireDate: string | null;
  active: boolean;
  departmentId: number | null;
  departmentName: string | null;
  managerId: number | null;
  managerName: string | null;
}

export interface DepartmentOption {
  id: number;
  name: string;
}

export interface EmployeeQuery {
  search: string;
  departmentId: number | null;
  active: boolean | null;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

export interface EmployeePayload {
  employeeNumber: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  phone: string | null;
  hireDate: string | null;
  departmentId: number | null;
  managerId: number | null;
}