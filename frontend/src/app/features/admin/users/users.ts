import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormGroup, FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { RouterLink } from '@angular/router';

import { EmployeeOption, PageResponse, UserQuery, UserSummary } from '../../../core/models/admin-user.models';
import { Role } from '../../../core/models/auth.models';
import { AdminUsersService } from '../../../core/services/admin-users.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Alert } from '../../../shared/components/alert/alert';
import { AppShell } from '../../../shared/components/app-shell/app-shell';
import { Icon } from '../../../shared/components/icon/icon';
import { IconName } from '../../../shared/components/icon/icons';

type DialogKind = 'create' | 'edit' | 'role' | 'status' | 'link';

interface Dialog {
  kind: DialogKind;
  user?: UserSummary;
}

const ROLES: { value: Role; label: string; description: string }[] = [
  { value: 'ADMIN', label: 'Administrateur', description: 'Comptes, rôles et paramètres' },
  { value: 'HR', label: 'Ressources humaines', description: 'Employés et congés' },
  { value: 'MANAGER', label: 'Manager', description: 'Suivi d\'équipe et tâches' },
  { value: 'EMPLOYEE', label: 'Employé', description: 'Profil, tâches et demandes' }
];

const ROLE_BADGES: Record<Role, string> = {
  ADMIN: 'bg-rose-50 text-rose-700 ring-rose-600/20 dark:bg-rose-500/10 dark:text-rose-300 dark:ring-rose-400/20',
  HR: 'bg-violet-50 text-violet-700 ring-violet-600/20 dark:bg-violet-500/10 dark:text-violet-300 dark:ring-violet-400/20',
  MANAGER: 'bg-sky-50 text-sky-700 ring-sky-600/20 dark:bg-sky-500/10 dark:text-sky-300 dark:ring-sky-400/20',
  EMPLOYEE: 'bg-slate-50 text-slate-600 ring-slate-500/20 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-400/20'
};

@Component({
  selector: 'app-admin-users',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './users.html'
})
export class AdminUsers {
  private readonly api = inject(AdminUsersService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly roles = ROLES;
  protected readonly roleBadges = ROLE_BADGES;
  protected readonly sizes = [10, 25, 50];

  // ---- liste ----
  protected readonly result = signal<PageResponse<UserSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly listError = signal<string | null>(null);
  protected readonly query = signal<UserQuery>({
    search: '',
    role: '',
    enabled: null,
    page: 0,
    size: 10,
    sortBy: 'createdAt',
    direction: 'desc'
  });
  protected readonly currentUserId = computed(() => this.auth.user()?.id ?? null);
  protected readonly from = computed(() => {
    const r = this.result();
    return r && r.totalElements > 0 ? r.page * r.size + 1 : 0;
  });
  protected readonly to = computed(() => {
    const r = this.result();
    return r ? r.page * r.size + r.content.length : 0;
  });

  // ---- boîtes de dialogue ----
  protected readonly dialog = signal<Dialog | null>(null);
  protected readonly saving = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly employees = signal<EmployeeOption[]>([]);
  protected readonly showPassword = signal(false);

  protected readonly createForm = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: this.fb.control<Role>('EMPLOYEE', Validators.required),
    enabled: [true],
    employeeId: this.fb.control<number | null>(null)
  });
  protected readonly editForm = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]]
  });
  protected readonly selectedRole = signal<Role>('EMPLOYEE');
  protected readonly selectedEmployee = signal<number | null>(null);

  constructor() {
    this.load();
  }

  // ---- chargement ----

  protected load(): void {
    this.loading.set(true);
    this.listError.set(null);
    this.api.list(this.query()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        // 401/403 sont déjà gérés par l'interceptor
        if (err.status !== 401 && err.status !== 403) {
          this.listError.set('Impossible de charger les utilisateurs. Réessayez.');
        }
      }
    });
  }

  protected onSearch(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.patchQuery({ search: value, page: 0 }), 300);
  }

  protected onRoleFilter(value: string): void {
    this.patchQuery({ role: value as Role | '', page: 0 });
  }

  protected onStatusFilter(value: string): void {
    this.patchQuery({ enabled: value === '' ? null : value === 'true', page: 0 });
  }

  protected onSize(value: string): void {
    this.patchQuery({ size: Number(value), page: 0 });
  }

  protected sort(column: string): void {
    const q = this.query();
    this.patchQuery({
      sortBy: column,
      direction: q.sortBy === column && q.direction === 'asc' ? 'desc' : 'asc',
      page: 0
    });
  }

  protected sortIcon(column: string): IconName {
    const q = this.query();
    return q.sortBy !== column ? 'chevron-up-down' : q.direction === 'asc' ? 'chevron-up' : 'chevron-down';
  }

  protected goTo(page: number): void {
    const r = this.result();
    if (r && page >= 0 && page < r.totalPages) {
      this.patchQuery({ page });
    }
  }

  private patchQuery(patch: Partial<UserQuery>): void {
    this.query.update((q) => ({ ...q, ...patch }));
    this.load();
  }

  // ---- dialogues ----

  protected open(kind: DialogKind, user?: UserSummary): void {
    this.dialogError.set(null);
    this.saving.set(false);
    this.showPassword.set(false);
    switch (kind) {
      case 'create':
        this.createForm.reset({ email: '', password: '', role: 'EMPLOYEE', enabled: true, employeeId: null });
        this.loadEmployees();
        break;
      case 'edit':
        this.editForm.reset({ email: user!.email });
        break;
      case 'role':
        this.selectedRole.set(user!.role);
        break;
      case 'link':
        this.selectedEmployee.set(null);
        this.loadEmployees();
        break;
    }
    this.dialog.set({ kind, user });
  }

  protected close(): void {
    if (!this.saving()) {
      this.dialog.set(null);
    }
  }

  private loadEmployees(): void {
    this.api.linkableEmployees().subscribe({
      next: (list) => this.employees.set(list),
      error: () => this.employees.set([])
    });
  }

  protected fieldInvalid(form: 'create' | 'edit', name: string): boolean {
    const group: FormGroup = form === 'create' ? this.createForm : this.editForm;
    const c = group.get(name);
    return !!c && c.invalid && c.touched;
  }

  protected submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.run(this.api.create(this.createForm.getRawValue()), (u) =>
      this.toast.success('Compte créé', `${u.email} peut maintenant se connecter.`)
    );
  }

  protected submitEdit(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.run(this.api.updateEmail(this.dialog()!.user!.id, this.editForm.getRawValue().email), () =>
      this.toast.success('Compte modifié', 'L\'adresse e-mail a été mise à jour.')
    );
  }

  protected submitRole(): void {
    const user = this.dialog()!.user!;
    const role = this.selectedRole();
    if (role === user.role) {
      this.dialog.set(null);
      return;
    }
    this.run(this.api.changeRole(user.id, role), () =>
      this.toast.success('Rôle modifié', `${user.email} est maintenant ${this.roleLabel(role)}.`)
    );
  }

  protected submitStatus(): void {
    const user = this.dialog()!.user!;
    const enabled = !user.enabled;
    this.run(this.api.setEnabled(user.id, enabled), () =>
      enabled
        ? this.toast.success('Compte activé', `${user.email} peut se connecter.`)
        : this.toast.warning('Compte désactivé', `${user.email} ne peut plus se connecter.`)
    );
  }

  protected submitLink(): void {
    const employeeId = this.selectedEmployee();
    if (employeeId === null) {
      this.dialogError.set('Choisissez un employé.');
      return;
    }
    this.run(this.api.linkEmployee(this.dialog()!.user!.id, employeeId), (u) =>
      this.toast.success('Employé associé', `${u.email} est associé à ${u.employeeName}.`)
    );
  }

  protected unlink(): void {
    this.run(this.api.unlinkEmployee(this.dialog()!.user!.id), (u) =>
      this.toast.info('Association retirée', `${u.email} n'est plus lié à un employé.`)
    );
  }

  /** Exécute une action d'écriture : ferme le dialogue et recharge la liste en cas de succès. */
  private run(request: Observable<UserSummary>, onSuccess: (u: UserSummary) => void): void {
    this.saving.set(true);
    this.dialogError.set(null);
    request.subscribe({
      next: (u) => {
        this.saving.set(false);
        this.dialog.set(null);
        onSuccess(u);
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.dialogError.set(this.messageOf(err));
      }
    });
  }

  private messageOf(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Serveur injoignable. Réessayez plus tard.';
    }
    const body = err.error as { message?: string; fieldErrors?: Record<string, string> } | null;
    const field = body?.fieldErrors ? Object.values(body.fieldErrors)[0] : null;
    return field ?? body?.message ?? 'Opération impossible. Réessayez.';
  }

  protected roleLabel(role: Role): string {
    return ROLES.find((r) => r.value === role)?.label ?? role;
  }

  protected initials(user: UserSummary): string {
    return (user.employeeName?.split(' ').map((p) => p[0]).join('').slice(0, 2) ?? user.email.slice(0, 2)).toUpperCase();
  }
}
