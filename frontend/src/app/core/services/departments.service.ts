import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/admin-user.models';
import { Department, DepartmentPayload, DepartmentQuery } from '../models/department.models';

@Injectable({ providedIn: 'root' })
export class DepartmentsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/departments`;

  list(query: DepartmentQuery): Observable<PageResponse<Department>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    return this.http.get<PageResponse<Department>>(this.base, { params });
  }

  get(id: number): Observable<Department> {
    return this.http.get<Department>(`${this.base}/${id}`);
  }

  create(payload: DepartmentPayload): Observable<Department> {
    return this.http.post<Department>(this.base, payload);
  }

  update(id: number, payload: DepartmentPayload): Observable<Department> {
    return this.http.put<Department>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
