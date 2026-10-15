import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { HealthService } from './core/services/health.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('SopraHR Interne');
  protected readonly apiStatus = signal('Vérification de l\'API...');

  constructor() {
    inject(HealthService)
      .check()
      .subscribe({
        next: (res) => this.apiStatus.set(`API : ${res.status}`),
        error: () => this.apiStatus.set('API : injoignable')
      });
  }
}
