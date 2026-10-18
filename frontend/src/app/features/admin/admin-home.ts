import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

interface AdminSection {
  title: string;
  description: string;
  icon: IconName;
  /** Route de la section si elle est disponible. */
  route?: string;
}

/** Page réservée au rôle ADMIN (garde roleGuard) ; les sections arrivent avec l'EPIC administration. */
@Component({
  selector: 'app-admin-home',
  imports: [RouterLink, AppShell, Alert, Icon],
  templateUrl: './admin-home.html'
})
export class AdminHome {
  protected readonly sections: AdminSection[] = [
    {
      title: 'Comptes utilisateurs',
      description: 'Créer, modifier, activer ou désactiver les comptes et attribuer les rôles.',
      route: '/admin/users',
      icon: 'user-plus'
    },
    { title: 'Rôles et permissions', description: 'Matrice détaillée des droits par rôle.', icon: 'shield-check' },
    { title: 'Journal d\'audit', description: 'Historique des actions sensibles.', icon: 'clock' },
    { title: 'Paramètres', description: 'Configuration générale de la plateforme.', icon: 'sliders' }
  ];
}
