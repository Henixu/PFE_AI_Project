import { Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { NotificationsService } from '../../../core/services/notifications.service';
import { Icon } from '../icon/icon';

/** Délai entre deux relevés du compteur de notifications non lues. */
const REFRESH_MS = 60_000;

/** Cloche de l'en-tête : lien vers la liste, avec le nombre de notifications non lues. */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink, Icon],
  templateUrl: './notification-bell.html'
})
export class NotificationBell {
  private readonly api = inject(NotificationsService);

  protected readonly count = this.api.unreadCount;
  protected readonly badge = computed(() => (this.count() > 99 ? '99+' : String(this.count())));
  protected readonly label = computed(() =>
    this.count() > 0 ? `Notifications (${this.count()} non lue(s))` : 'Notifications'
  );

  constructor() {
    // Premier relevé immédiat, puis un relevé par minute tant que la page est affichée.
    timer(0, REFRESH_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.api.refreshUnreadCount());
  }
}
