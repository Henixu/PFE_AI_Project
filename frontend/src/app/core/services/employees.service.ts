import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DepartmentOption, Employee, EmployeePayload, EmployeeQuery } from '../models/employee.models';
import { PageResponse } from '../models/admin-user.models';

@Injectable({ providedIn: 'root' })
export class EmployeesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/employees`;

  list(query: EmployeeQuery): Observable<PageResponse<Employee>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    if (query.departmentId !== null) params = params.set('departmentId', query.departmentId);
    if (query.active !== null) params = params.set('active', query.active);
    return this.http.get<PageResponse<Employee>>(this.base, { params });
  }

  get(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.base}/${id}`);
  }

  create(payload: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>(this.base, payload);
  }

  update(id: number, payload: EmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`${this.base}/${id}`, payload);
  }

  setActive(id: number, active: boolean): Observable<Employee> {
    return this.http.patch<Employee>(`${this.base}/${id}/status`, null, { params: { active } });
  }

  departments(): Observable<DepartmentOption[]> {
    return this.http.get<DepartmentOption[]>(`${this.base}/departments`);
  }
}