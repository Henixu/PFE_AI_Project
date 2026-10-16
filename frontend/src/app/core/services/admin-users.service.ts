import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateUserPayload,
  EmployeeOption,
  PageResponse,
  UserQuery,
  UserSummary
} from '../models/admin-user.models';
import { Role } from '../models/auth.models';

/** Appels de l'API d'administration des comptes (réservée au rôle ADMIN côté serveur). */
@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/admin/users`;

  list(query: UserQuery): Observable<PageResponse<UserSummary>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.search?.trim()) {
      params = params.set('search', query.search.trim());
    }
    if (query.role) {
      params = params.set('role', query.role);
    }
    if (query.enabled !== null && query.enabled !== undefined) {
      params = params.set('enabled', query.enabled);
    }
    return this.http.get<PageResponse<UserSummary>>(this.base, { params });
  }

  create(payload: CreateUserPayload): Observable<UserSummary> {
    return this.http.post<UserSummary>(this.base, payload);
  }

  updateEmail(id: number, email: string): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${this.base}/${id}`, { email });
  }

  changeRole(id: number, role: Role): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.base}/${id}/role`, { role });
  }

  setEnabled(id: number, enabled: boolean): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.base}/${id}/status`, { enabled });
  }

  linkEmployee(id: number, employeeId: number): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${this.base}/${id}/employee`, { employeeId });
  }

  unlinkEmployee(id: number): Observable<UserSummary> {
    return this.http.delete<UserSummary>(`${this.base}/${id}/employee`);
  }

  linkableEmployees(): Observable<EmployeeOption[]> {
    return this.http.get<EmployeeOption[]>(`${this.base}/linkable-employees`);
  }
}
