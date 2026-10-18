import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { HealthService } from '../../../core/services/health.service';
import { ToastService } from '../../../core/services/toast.service';
import { Alert } from '../../../shared/components/alert/alert';
import { AuthShell } from '../../../shared/components/auth-shell/auth-shell';
import { Icon } from '../../../shared/components/icon/icon';

type ApiState = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Alert, Icon],
  templateUrl: './login.html'
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly apiState = signal<ApiState>('checking');

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  constructor() {
    inject(HealthService)
      .check()
      .subscribe({
        next: (res) => this.apiState.set(res.status === 'UP' ? 'up' : 'down'),
        error: () => this.apiState.set('down')
      });
  }

  protected invalid(control: 'email' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && c.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (res) => {
        this.toast.success(`Bienvenue${res.user.firstName ? ', ' + res.user.firstName : ''}`, 'Connexion réussie.');
        void this.router.navigateByUrl(this.safeReturnUrl());
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 401
            ? 'E-mail ou mot de passe incorrect.'
            : err.status === 0
              ? 'Serveur injoignable. Réessayez plus tard.'
              : 'Connexion impossible. Réessayez.'
        );
      }
    });
  }

  /** N'accepte qu'un chemin interne (évite une redirection ouverte via ?returnUrl=). */
  private safeReturnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/dashboard';
  }
}
