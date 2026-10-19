import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { PageResponse } from '../../core/models/admin-user.models';
import {
  LEAVE_STATUSES,
  LEAVE_STATUS_LABELS,
  LEAVE_TYPES,
  LEAVE_TYPE_LABELS,
  LeaveQuery,
  LeaveRequest,
  LeaveStatus,
  LeaveType
} from '../../core/models/leave.models';
import { LeavesService } from '../../core/services/leaves.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

/** La date de fin doit être postérieure ou égale à la date de début. */
function validRange(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value as string;
  const end = group.get('endDate')?.value as string;
  return start && end && end < start ? { range: true } : null;
}

function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { blank: true } : null;
}

@Component({
  selector: 'app-leaves',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './leaves.html'
})
export class Leaves {
  private readonly api = inject(LeavesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly types = LEAVE_TYPES;
  protected readonly statuses = LEAVE_STATUSES;
  protected readonly typeLabels = LEAVE_TYPE_LABELS;
  protected readonly statusLabels = LEAVE_STATUS_LABELS;

  protected readonly result = signal<PageResponse<LeaveRequest> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal<LeaveQuery>({ status: null, search: '', page: 0, size: 10, sortBy: 'createdAt', direction: 'desc' });

  protected readonly selected = signal<LeaveRequest | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.group(
    {
      type: this.fb.control<LeaveType>('ANNUAL', Validators.required),
      startDate: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)]],
      endDate: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)]],
      reason: ['', [Validators.required, notBlank, Validators.maxLength(500)]]
    },
    { validators: validRange }
  );

  protected readonly cancelling = signal<LeaveRequest | null>(null);
  protected readonly cancelBusy = signal(false);

  constructor() {
    this.load();
  }

  protected from(): number {
    const r = this.result();
    return r && r.totalElements ? r.page * r.size + 1 : 0;
  }

  protected to(): number {
    const r = this.result();
    return r ? r.page * r.size + r.content.length : 0;
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.mine(this.query()).subscribe({
      next: (value) => {
        this.result.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger vos demandes.');
      }
    });
  }

  protected status(value: string): void {
    this.patch({ status: (value || null) as LeaveStatus | null, page: 0 });
  }

  protected sort(column: string): void {
    const q = this.query();
    this.patch({ sortBy: column, direction: q.sortBy === column && q.direction === 'asc' ? 'desc' : 'asc', page: 0 });
  }

  protected sortIcon(column: string): IconName {
    const q = this.query();
    return q.sortBy !== column ? 'chevron-up-down' : q.direction === 'asc' ? 'chevron-up' : 'chevron-down';
  }

  protected goTo(page: number): void {
    const r = this.result();
    if (r && page >= 0 && page < r.totalPages) this.patch({ page });
  }

  protected patch(value: Partial<LeaveQuery>): void {
    this.query.update((q) => ({ ...q, ...value }));
    this.load();
  }

  protected statusClass(status: LeaveStatus): string {
    return { PENDING: 'badge-warning', APPROVED: 'badge-success', REJECTED: 'badge-danger', CANCELLED: 'badge-neutral' }[status];
  }

  // ---- dépôt ----

  protected openForm(): void {
    this.formError.set(null);
    this.form.reset({ type: 'ANNUAL', startDate: '', endDate: '', reason: '' });
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    if (!this.saving()) this.formOpen.set(false);
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected rangeInvalid(): boolean {
    return this.form.hasError('range') && !!this.form.get('endDate')?.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.api.submit({ ...value, reason: value.reason.trim() }).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.toast.success('Demande envoyée', 'Elle est en attente de validation.');
        this.query.update((q) => ({ ...q, page: 0 }));
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(this.messageOf(err, 'Envoi impossible. Vérifiez les données saisies.'));
      }
    });
  }

  // ---- annulation ----

  protected confirmCancel(request: LeaveRequest): void {
    this.cancelling.set(request);
  }

  protected closeCancel(): void {
    if (!this.cancelBusy()) this.cancelling.set(null);
  }

  protected cancel(): void {
    const request = this.cancelling();
    if (!request) return;
    this.cancelBusy.set(true);
    this.api.cancel(request.id).subscribe({
      next: () => {
        this.cancelBusy.set(false);
        this.cancelling.set(null);
        this.toast.success('Demande annulée');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.cancelBusy.set(false);
        this.cancelling.set(null);
        this.toast.error('Annulation impossible', this.messageOf(err, 'Réessayez dans un instant.'));
        this.load();
      }
    });
  }

  private messageOf(err: HttpErrorResponse, fallback: string): string {
    return typeof err.error?.message === 'string' ? err.error.message : fallback;
  }
}
