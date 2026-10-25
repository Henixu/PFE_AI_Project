import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/admin-user.models';
import { AppNotification } from '../models/notification.models';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/notifications`;

  /** Compteur de notifications non lues, partagé entre la cloche de l'en-tête et la page de liste. */
  readonly unreadCount = signal(0);

  list(unreadOnly: boolean, page: number, size: number): Observable<PageResponse<AppNotification>> {
    const params = new HttpParams().set('unreadOnly', unreadOnly).set('page', page).set('size', size);
    return this.http.get<PageResponse<AppNotification>>(this.base, { params });
  }

  /** Recharge le compteur ; un échec réseau laisse la valeur précédente. */
  refreshUnreadCount(): void {
    this.http.get<{ count: number }>(`${this.base}/unread-count`).subscribe({
      next: (r) => this.unreadCount.set(r.count),
      error: () => undefined
    });
  }

  markRead(id: number): Observable<AppNotification> {
    return this.http.patch<AppNotification>(`${this.base}/${id}/read`, null).pipe(tap(() => this.refreshUnreadCount()));
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${this.base}/read-all`, null).pipe(tap(() => this.unreadCount.set(0)));
  }
}
