import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Alert } from '../../shared/components/alert/alert';
import { TopBar } from '../../shared/components/top-bar/top-bar';

interface AdminSection {
  title: string;
  description: string;
  icon: string;
}

/** Page réservée au rôle ADMIN (garde roleGuard) ; les sections arrivent avec l'EPIC administration. */
@Component({
  selector: 'app-admin-home',
  imports: [RouterLink, TopBar, Alert],
  templateUrl: './admin-home.html'
})
export class AdminHome {
  protected readonly sections: AdminSection[] = [
    {
      title: 'Comptes utilisateurs',
      description: 'Créer, activer ou désactiver les comptes.',
      icon: 'M18 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 1 1-6.75 0 3.375 3.375 0 0 1 6.75 0ZM3 19.235v-.11a6.375 6.375 0 0 1 12.75 0v.109A12.318 12.318 0 0 1 9.374 21c-2.331 0-4.512-.645-6.374-1.766Z'
    },
    {
      title: 'Rôles et permissions',
      description: 'Attribuer ADMIN, HR, MANAGER ou EMPLOYEE.',
      icon: 'M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z'
    },
    {
      title: 'Journal d\'audit',
      description: 'Historique des actions sensibles.',
      icon: 'M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z'
    },
    {
      title: 'Paramètres',
      description: 'Configuration générale de la plateforme.',
      icon: 'M10.5 6h9.75M10.5 6a1.5 1.5 0 1 1-3 0m3 0a1.5 1.5 0 1 0-3 0M3.75 6H7.5m3 12h9.75m-9.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-3.75 0H7.5m9-6h3.75m-3.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-9.75 0h9.75'
    }
  ];
}
