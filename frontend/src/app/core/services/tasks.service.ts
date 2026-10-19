import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { EmployeeOption, PageResponse } from '../models/admin-user.models';
import { Task, TaskPayload, TaskQuery, TaskStatus } from '../models/task.models';

@Injectable({ providedIn: 'root' })
export class TasksService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/tasks`;

  list(query: TaskQuery): Observable<PageResponse<Task>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    if (query.assigneeId !== null) params = params.set('assigneeId', query.assigneeId);
    if (query.priority) params = params.set('priority', query.priority);
    if (query.status) params = params.set('status', query.status);
    if (query.overdue) params = params.set('overdue', true);
    if (query.mine) params = params.set('mine', true);
    if (query.dueFrom) params = params.set('dueFrom', query.dueFrom);
    if (query.dueTo) params = params.set('dueTo', query.dueTo);
    return this.http.get<PageResponse<Task>>(this.base, { params });
  }

  create(payload: TaskPayload): Observable<Task> {
    return this.http.post<Task>(this.base, payload);
  }

  update(id: number, payload: TaskPayload): Observable<Task> {
    return this.http.put<Task>(`${this.base}/${id}`, payload);
  }

  changeStatus(id: number, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`${this.base}/${id}/status`, { status });
  }

  /** Employés auxquels l'utilisateur connecté peut affecter une tâche. */
  assignees(): Observable<EmployeeOption[]> {
    return this.http.get<EmployeeOption[]>(`${this.base}/assignees`);
  }
}
