import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { Alert } from '../../../shared/components/alert/alert';
import { AuthShell } from '../../../shared/components/auth-shell/auth-shell';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Alert],
  templateUrl: './forgot-password.html'
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly sentTo = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]]
  });

  protected get emailInvalid(): boolean {
    const c = this.form.controls.email;
    return c.invalid && c.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.auth.forgotPassword(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.sentTo.set(email);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Demande impossible pour le moment. Réessayez dans quelques instants.');
      }
    });
  }
}
