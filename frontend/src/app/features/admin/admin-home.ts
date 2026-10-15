import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Page réservée au rôle ADMIN (garde roleGuard) ; contenu à venir. */
@Component({
  selector: 'app-admin-home',
  imports: [RouterLink],
  templateUrl: './admin-home.html'
})
export class AdminHome {}
