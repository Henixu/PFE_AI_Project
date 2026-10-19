import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { PageResponse } from '../../core/models/admin-user.models';
import { Department, DepartmentQuery } from '../../core/models/department.models';
import { DepartmentsService } from '../../core/services/departments.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

/** Refuse une valeur composée uniquement d'espaces. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { blank: true } : null;
}

@Component({
  selector: 'app-departments',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './departments.html'
})
export class Departments {
  private readonly api = inject(DepartmentsService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly result = signal<PageResponse<Department> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal<DepartmentQuery>({ search: '', page: 0, size: 10, sortBy: 'name', direction: 'asc' });

  /** Fiche détaillée (avec les employés associés). */
  protected readonly selected = signal<Department | null>(null);
  protected readonly detailLoading = signal(false);

  /** Formulaire de création (id null) ou de modification. */
  protected readonly editor = signal<{ id: number | null } | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, notBlank, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(500)]
  });

  protected readonly deleting = signal<Department | null>(null);
  protected readonly deleteBusy = signal(false);

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
    this.api.list(this.query()).subscribe({
      next: (value) => {
        this.result.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les départements.');
      }
    });
  }

  protected search(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.patch({ search: value, page: 0 }), 300);
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

  protected patch(value: Partial<DepartmentQuery>): void {
    this.query.update((q) => ({ ...q, ...value }));
    this.load();
  }

  // ---- consultation ----

  protected openDetail(department: Department): void {
    this.selected.set({ ...department, employees: null });
    this.detailLoading.set(true);
    this.api.get(department.id).subscribe({
      next: (detail) => {
        this.selected.set(detail);
        this.detailLoading.set(false);
      },
      error: () => {
        this.selected.set(null);
        this.detailLoading.set(false);
        this.toast.error('Chargement impossible', 'Le département est introuvable ou inaccessible.');
      }
    });
  }

  // ---- création / modification ----

  protected openCreate(): void {
    this.formError.set(null);
    this.form.reset({ name: '', description: '' });
    this.editor.set({ id: null });
  }

  protected openEdit(department: Department): void {
    this.formError.set(null);
    this.form.reset({ name: department.name, description: department.description ?? '' });
    this.editor.set({ id: department.id });
  }

  protected closeEditor(): void {
    if (!this.saving()) this.editor.set(null);
  }

  protected save(): void {
    const editor = this.editor();
    if (!editor) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload = { name: value.name.trim(), description: value.description.trim() || null };
    this.saving.set(true);
    this.formError.set(null);
    const request = editor.id === null ? this.api.create(payload) : this.api.update(editor.id, payload);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.editor.set(null);
        this.toast.success(editor.id === null ? 'Département créé' : 'Département modifié', 'Les données ont été enregistrées.');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(this.messageOf(err, 'Enregistrement impossible. Vérifiez les données saisies.'));
      }
    });
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  // ---- suppression ----

  protected confirmDelete(department: Department): void {
    this.deleting.set(department);
  }

  protected closeDelete(): void {
    if (!this.deleteBusy()) this.deleting.set(null);
  }

  protected delete(): void {
    const department = this.deleting();
    if (!department || department.employeeCount > 0) return;
    this.deleteBusy.set(true);
    this.api.delete(department.id).subscribe({
      next: () => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        this.toast.success('Département supprimé', department.name);
        const r = this.result();
        if (r && r.content.length === 1 && r.page > 0) this.query.update((q) => ({ ...q, page: q.page - 1 }));
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        this.toast.error('Suppression impossible', this.messageOf(err, 'Réessayez dans un instant.'));
        this.load();
      }
    });
  }

  private messageOf(err: HttpErrorResponse, fallback: string): string {
    return typeof err.error?.message === 'string' ? err.error.message : fallback;
  }
}
