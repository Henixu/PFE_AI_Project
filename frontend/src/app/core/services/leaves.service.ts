import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/admin-user.models';
import { LeavePayload, LeaveQuery, LeaveRequest } from '../models/leave.models';

@Injectable({ providedIn: 'root' })
export class LeavesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/leaves`;

  submit(payload: LeavePayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(this.base, payload);
  }

  /** Demandes de l'utilisateur connecté. */
  mine(query: LeaveQuery): Observable<PageResponse<LeaveRequest>> {
    return this.http.get<PageResponse<LeaveRequest>>(`${this.base}/mine`, { params: this.params(query) });
  }

  /** Demandes à traiter dans le périmètre de l'utilisateur (RH : toutes ; manager : son équipe). */
  toProcess(query: LeaveQuery): Observable<PageResponse<LeaveRequest>> {
    return this.http.get<PageResponse<LeaveRequest>>(this.base, { params: this.params(query) });
  }

  approve(id: number, comment: string): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.base}/${id}/approve`, { comment: comment.trim() || null });
  }

  reject(id: number, comment: string): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.base}/${id}/reject`, { comment: comment.trim() || null });
  }

  cancel(id: number): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.base}/${id}/cancel`, null);
  }

  private params(query: LeaveQuery): HttpParams {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.status) params = params.set('status', query.status);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    return params;
  }
}
