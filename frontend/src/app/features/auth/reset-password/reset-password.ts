import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Alert } from '../../../shared/components/alert/alert';
import { AuthShell } from '../../../shared/components/auth-shell/auth-shell';
import { Icon } from '../../../shared/components/icon/icon';

const STRENGTH_LABELS = ['Trop faible', 'Faible', 'Moyen', 'Bon', 'Excellent'];

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Alert, Icon],
  templateUrl: './reset-password.html'
})
export class ResetPassword {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  /** Jeton lu une seule fois depuis l'URL. */
  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', Validators.required]
  });

  private readonly password = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  private readonly confirm = toSignal(this.form.controls.confirm.valueChanges, { initialValue: '' });

  /** Score indicatif de 0 à 4 (longueur, casse, chiffres, symboles). */
  protected readonly strength = computed(() => {
    const p = this.password();
    if (!p) {
      return 0;
    }
    let score = 0;
    if (p.length >= 8) score++;
    if (p.length >= 12) score++;
    if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++;
    if (/\d/.test(p) && /[^A-Za-z0-9]/.test(p)) score++;
    return Math.min(score, 4);
  });
  protected readonly strengthLabel = computed(() => STRENGTH_LABELS[this.strength()]);
  protected readonly mismatch = computed(() => this.confirm().length > 0 && this.confirm() !== this.password());

  protected passwordInvalid(): boolean {
    const c = this.form.controls.password;
    return c.invalid && c.touched;
  }

  protected submit(): void {
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token!, this.form.getRawValue().password).subscribe({
      next: () => {
        this.done.set(true);
        this.toast.success('Mot de passe mis à jour', 'Vous pouvez maintenant vous connecter.');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 400
            ? 'Ce lien est invalide, expiré ou déjà utilisé. Refaites une demande.'
            : 'Réinitialisation impossible. Réessayez.'
        );
      }
    });
  }
}
