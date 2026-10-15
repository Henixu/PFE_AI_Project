import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.html'
})
export class ResetPassword {
  private readonly auth = inject(AuthService);

  /** Jeton lu une seule fois depuis l'URL. */
  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', Validators.required]
  });

  protected submit(): void {
    const { password, confirm } = this.form.getRawValue();
    if (this.form.invalid) {
      this.error.set('Le mot de passe doit contenir entre 8 et 72 caractères.');
      return;
    }
    if (password !== confirm) {
      this.error.set('Les deux mots de passe ne correspondent pas.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token!, password).subscribe({
      next: () => this.done.set(true),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 400
            ? 'Lien invalide ou expiré. Refaites une demande.'
            : 'Réinitialisation impossible. Réessayez.'
        );
      }
    });
  }
}
