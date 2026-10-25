import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Role } from '../../../core/models/auth.models';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Icon } from '../icon/icon';
import { IconName } from '../icon/icons';
import { NotificationBell } from '../notification-bell/notification-bell';
import { ThemeToggle } from '../theme-toggle/theme-toggle';

interface NavItem {
  label: string;
  route: string;
  icon: IconName;
  roles?: Role[];
  exact?: boolean;
}

const NAV: { title?: string; items: NavItem[] }[] = [
  {
    items: [
      { label: 'Tableau de bord', route: '/dashboard', icon: 'squares' },
      { label: 'Employés', route: '/employees', icon: 'users', roles: ['ADMIN', 'HR'] },
      { label: 'Départements', route: '/departments', icon: 'building', roles: ['ADMIN', 'HR'] },
      { label: 'Tâches', route: '/tasks', icon: 'clipboard' },
      { label: 'Documents', route: '/documents', icon: 'document' }
    ]
  },
  {
    title: 'Congés',
    items: [
      { label: 'Mes congés', route: '/leaves', icon: 'calendar', exact: true },
      { label: 'Demandes à traiter', route: '/leaves/review', icon: 'clock', roles: ['ADMIN', 'HR', 'MANAGER'] }
    ]
  },
  {
    title: 'Administration',
    items: [
      { label: 'Vue d\'ensemble', route: '/admin', icon: 'shield-check', roles: ['ADMIN'], exact: true },
      { label: 'Comptes utilisateurs', route: '/admin/users', icon: 'user-plus', roles: ['ADMIN'] }
    ]
  }
];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  HR: 'Ressources humaines',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employé'
};

/** Mise en page des écrans connectés : barre latérale de navigation + en-tête. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, ThemeToggle, NotificationBell, Icon],
  templateUrl: './app-shell.html'
})
export class AppShell {
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly menuOpen = signal(false);

  protected readonly nav = computed(() =>
    NAV.map((group) => ({
      ...group,
      items: group.items.filter((i) => !i.roles || this.auth.hasAnyRole(i.roles))
    })).filter((group) => group.items.length > 0)
  );

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

  protected readonly roleLabel = computed(() => {
    const role = this.auth.user()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  protected logout(): void {
    this.auth.logout();
    this.toast.info('Déconnexion', 'Vous avez été déconnecté.');
  }
}
