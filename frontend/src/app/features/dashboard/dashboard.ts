import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Role } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { AppShell, ROLE_LABELS } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

interface ModuleTile {
  title: string;
  description: string;
  icon: IconName;
  roles?: Role[];
  /** Route du module s'il est disponible (et rôles autorisés pour y accéder). */
  route?: string;
  routeRoles?: Role[];
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, AppShell, Icon],
  templateUrl: './dashboard.html'
})
export class Dashboard {
  protected readonly auth = inject(AuthService);

  protected readonly greeting = (() => {
    const h = new Date().getHours();
    return h < 18 ? 'Bonjour' : 'Bonsoir';
  })();

  protected readonly today = new Date().toLocaleDateString('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.user()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  /** Modules du backlog : liens actifs quand ils sont disponibles pour le rôle, sinon « à venir ». */
  protected readonly modules = computed(() =>
    MODULES.filter((m) => !m.roles || this.auth.hasAnyRole(m.roles)).map((m) => ({
      ...m,
      link: m.route && (!m.routeRoles || this.auth.hasAnyRole(m.routeRoles)) ? m.route : null
    }))
  );
}

const MODULES: ModuleTile[] = [
  {
    title: 'Employés',
    description: 'Fiches, postes et rattachements.',
    icon: 'users',
    roles: ['ADMIN', 'HR', 'MANAGER'],
    route: '/employees',
    routeRoles: ['ADMIN', 'HR']
  },
  {
    title: 'Départements',
    description: 'Organisation et équipes.',
    icon: 'building',
    roles: ['ADMIN', 'HR'],
    route: '/departments',
    routeRoles: ['ADMIN', 'HR']
  },
  { title: 'Tâches', description: 'Suivi, priorités et échéances.', icon: 'clipboard', route: '/tasks' },
  { title: 'Congés', description: 'Demandes et validations.', icon: 'calendar', route: '/leaves' },
  { title: 'Documents', description: 'Contrats et fichiers partagés.', icon: 'document' },
  { title: 'Notifications', description: 'Alertes et rappels.', icon: 'bell' }
];
