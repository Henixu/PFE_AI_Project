import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { PageResponse } from '../../core/models/admin-user.models';
import {
  LEAVE_STATUSES,
  LEAVE_STATUS_LABELS,
  LEAVE_TYPE_LABELS,
  LeaveQuery,
  LeaveRequest,
  LeaveStatus
} from '../../core/models/leave.models';
import { AuthService } from '../../core/services/auth.service';
import { LeavesService } from '../../core/services/leaves.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

type Decision = 'approve' | 'reject';

/** Demandes de congé à traiter : tout le personnel pour ADMIN/RH, l'équipe directe pour un manager. */
@Component({
  selector: 'app-leave-review',
  imports: [FormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './leave-review.html'
})
export class LeaveReview {
  private readonly api = inject(LeavesService);
  private readonly toast = inject(ToastService);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly auth = inject(AuthService);
  protected readonly statuses = LEAVE_STATUSES;
  protected readonly typeLabels = LEAVE_TYPE_LABELS;
  protected readonly statusLabels = LEAVE_STATUS_LABELS;

  protected readonly result = signal<PageResponse<LeaveRequest> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal<LeaveQuery>({ status: 'PENDING', search: '', page: 0, size: 10, sortBy: 'createdAt', direction: 'desc' });

  protected readonly selected = signal<LeaveRequest | null>(null);

  /** Décision en cours de saisie. Le motif est obligatoire pour refuser. */
  protected readonly deciding = signal<{ request: LeaveRequest; decision: Decision } | null>(null);
  protected comment = '';
  protected readonly commentTouched = signal(false);
  protected readonly busy = signal(false);
  protected readonly decisionError = signal<string | null>(null);

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
    this.api.toProcess(this.query()).subscribe({
      next: (value) => {
        this.result.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les demandes.');
      }
    });
  }

  protected search(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.patch({ search: value, page: 0 }), 300);
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

  // ---- décision ----

  protected openDecision(request: LeaveRequest, decision: Decision): void {
    this.comment = '';
    this.commentTouched.set(false);
    this.decisionError.set(null);
    this.deciding.set({ request, decision });
  }

  protected closeDecision(): void {
    if (!this.busy()) this.deciding.set(null);
  }

  protected commentMissing(): boolean {
    return this.deciding()?.decision === 'reject' && this.comment.trim() === '' && this.commentTouched();
  }

  protected confirmDecision(): void {
    const current = this.deciding();
    if (!current) return;
    this.commentTouched.set(true);
    if (current.decision === 'reject' && this.comment.trim() === '') return;
    this.busy.set(true);
    this.decisionError.set(null);
    const call =
      current.decision === 'approve'
        ? this.api.approve(current.request.id, this.comment)
        : this.api.reject(current.request.id, this.comment);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.deciding.set(null);
        this.toast.success(current.decision === 'approve' ? 'Demande approuvée' : 'Demande refusée', 'La décision a été enregistrée.');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.decisionError.set(typeof err.error?.message === 'string' ? err.error.message : 'Décision impossible. Réessayez.');
        if (err.status === 409) this.load(); // déjà traitée par quelqu'un d'autre : rafraîchir la liste
      }
    });
  }
}
