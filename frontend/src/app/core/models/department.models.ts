export interface DepartmentEmployee {
  id: number;
  employeeNumber: string;
  fullName: string;
  jobTitle: string | null;
  active: boolean;
}

export interface Department {
  id: number;
  name: string;
  description: string | null;
  employeeCount: number;
  createdAt: string;
  updatedAt: string;
  /** Renseigné uniquement pour le détail d'un département. */
  employees: DepartmentEmployee[] | null;
}

export interface DepartmentQuery {
  search: string;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

export interface DepartmentPayload {
  name: string;
  description: string | null;
}
