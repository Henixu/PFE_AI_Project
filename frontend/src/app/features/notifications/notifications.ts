import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PageResponse } from '../../core/models/admin-user.models';
import { AppNotification, NOTIFICATION_TYPE_LABELS, NotificationType } from '../../core/models/notification.models';
import { NotificationsService } from '../../core/services/notifications.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

const TYPE_ICONS: Record<NotificationType, IconName> = {
  GENERAL: 'information-circle',
  TASK_ASSIGNED: 'clipboard',
  LEAVE_DECISION: 'calendar',
  TASK_DUE: 'clock'
};

@Component({
  selector: 'app-notifications',
  imports: [RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './notifications.html'
})
export class Notifications {
  private readonly api = inject(NotificationsService);
  private readonly toast = inject(ToastService);

  protected readonly unreadCount = this.api.unreadCount;
  protected readonly typeLabels = NOTIFICATION_TYPE_LABELS;

  protected readonly result = signal<PageResponse<AppNotification> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly unreadOnly = signal(false);
  protected readonly page = signal(0);
  protected readonly size = 10;
  protected readonly busy = signal(false);

  constructor() {
    this.api.refreshUnreadCount();
    this.load();
  }

  protected iconOf(type: NotificationType): IconName {
    return TYPE_ICONS[type];
  }

  protected from(): number {
    const r = this.result();
    return r && r.totalElements ? r.page * r.size + 1 : 0;
  }

  protected to(): number {
    const r = this.result();
    return r ? r.page * r.size + r.content.length : 0;
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.unreadOnly(), this.page(), this.size).subscribe({
      next: (value) => {
        this.result.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les notifications.');
      }
    });
  }

  protected filter(unreadOnly: boolean): void {
    this.unreadOnly.set(unreadOnly);
    this.page.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    const r = this.result();
    if (r && page >= 0 && page < r.totalPages) {
      this.page.set(page);
      this.load();
    }
  }

  /** Marque la notification comme lue (persisté côté serveur) et met la ligne à jour sans recharger la page. */
  protected markRead(notification: AppNotification): void {
    if (notification.read) return;
    this.api.markRead(notification.id).subscribe({
      next: (updated) => {
        this.result.update((r) =>
          r && { ...r, content: r.content.map((n) => (n.id === updated.id ? updated : n)) }
        );
        if (this.unreadOnly()) this.load();
      },
      error: () => this.toast.error('Action impossible', 'Réessayez dans un instant.')
    });
  }

  protected markAllRead(): void {
    this.busy.set(true);
    this.api.markAllRead().subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: () => {
        this.busy.set(false);
        this.toast.error('Action impossible', 'Réessayez dans un instant.');
      }
    });
  }
}
