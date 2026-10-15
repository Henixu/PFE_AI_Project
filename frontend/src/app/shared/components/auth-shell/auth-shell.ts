import { Component, input } from '@angular/core';

import { ThemeToggle } from '../theme-toggle/theme-toggle';

/** Mise en page commune des écrans d'authentification (panneau de marque + carte de formulaire). */
@Component({
  selector: 'app-auth-shell',
  imports: [ThemeToggle],
  templateUrl: './auth-shell.html'
})
export class AuthShell {
  readonly heading = input.required<string>();
  readonly subheading = input<string>();
  protected readonly year = new Date().getFullYear();
}
