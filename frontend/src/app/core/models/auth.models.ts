export type Role = 'ADMIN' | 'HR' | 'MANAGER' | 'EMPLOYEE';

export interface UserProfile {
  id: number;
  email: string;
  role: Role;
  firstName: string | null;
  lastName: string | null;
  jobTitle: string | null;
  department: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: UserProfile;
}

export interface MessageResponse {
  message: string;
}
