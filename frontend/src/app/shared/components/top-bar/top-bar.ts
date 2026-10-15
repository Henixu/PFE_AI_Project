import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { ThemeToggle } from '../theme-toggle/theme-toggle';

@Component({
  selector: 'app-top-bar',
  imports: [RouterLink, RouterLinkActive, ThemeToggle],
  templateUrl: './top-bar.html'
})
export class TopBar {
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly initials = computed(() => {
    const user = this.auth.user();
    if (!user) {
      return '?';
    }
    const fromName = `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`;
    return (fromName || user.email[0]).toUpperCase();
  });

  protected readonly displayName = computed(() => {
    const user = this.auth.user();
    if (!user) {
      return '';
    }
    return user.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : user.email;
  });

  protected logout(): void {
    this.auth.logout();
    this.toast.info('Déconnecté', 'À bientôt !');
  }
}
