import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Employee, EmployeePayload, EmployeeQuery } from '../../core/models/employee.models';
import { EmployeesService } from '../../core/services/employees.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { TopBar } from '../../shared/components/top-bar/top-bar';
import { PageResponse } from '../../core/models/admin-user.models';

@Component({
  selector: 'app-employees',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, TopBar, Alert],
  templateUrl: './employees.html'
})
export class Employees {
  private readonly api = inject(EmployeesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly result = signal<PageResponse<Employee> | null>(null);
  protected readonly departments = signal<{ id: number; name: string }[]>([]);
  protected readonly selected = signal<Employee | null>(null);
  protected readonly editing = signal<Employee | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly query = signal<EmployeeQuery>({ search: '', departmentId: null, active: true, page: 0, size: 10, sortBy: 'lastName', direction: 'asc' });
  protected readonly form = this.fb.group({
    employeeNumber: ['', [Validators.required, Validators.maxLength(20)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    jobTitle: ['', Validators.maxLength(150)],
    phone: ['', Validators.maxLength(30)],
    hireDate: ['', Validators.pattern(/^$|^\d{4}-\d{2}-\d{2}$/)],
    departmentId: this.fb.control<number | null>(null),
    managerId: this.fb.control<number | null>(null)
  });
  protected readonly from = () => { const r = this.result(); return r && r.totalElements ? r.page * r.size + 1 : 0; };
  protected readonly to = () => { const r = this.result(); return r ? r.page * r.size + r.content.length : 0; };

  constructor() {
    this.load();
    this.api.departments().subscribe({ next: (items) => this.departments.set(items) });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.query()).subscribe({ next: (value) => { this.result.set(value); this.loading.set(false); }, error: () => { this.loading.set(false); this.error.set('Impossible de charger les employés.'); } });
  }

  protected search(value: string): void { clearTimeout(this.searchTimer); this.searchTimer = setTimeout(() => this.patch({ search: value, page: 0 }), 300); }
  protected status(value: string): void { this.patch({ active: value === '' ? null : value === 'true', page: 0 }); }
  protected department(value: string): void { this.patch({ departmentId: value ? Number(value) : null, page: 0 }); }
  protected sort(column: string): void { const q = this.query(); this.patch({ sortBy: column, direction: q.sortBy === column && q.direction === 'asc' ? 'desc' : 'asc', page: 0 }); }
  protected sortIcon(column: string): string { const q = this.query(); return q.sortBy !== column ? '↕' : q.direction === 'asc' ? '↑' : '↓'; }
  protected goTo(page: number): void { const r = this.result(); if (r && page >= 0 && page < r.totalPages) this.patch({ page }); }
  protected patch(value: Partial<EmployeeQuery>): void { this.query.update((q) => ({ ...q, ...value })); this.load(); }

  protected openCreate(): void { this.editing.set({ id: 0, employeeNumber: '', firstName: '', lastName: '', jobTitle: null, phone: null, hireDate: null, active: true, departmentId: null, departmentName: null, managerId: null, managerName: null }); this.form.reset({ employeeNumber: '', firstName: '', lastName: '', jobTitle: '', phone: '', hireDate: '', departmentId: null, managerId: null }); }
  protected openEdit(employee: Employee): void { this.editing.set(employee); this.form.reset({ employeeNumber: employee.employeeNumber, firstName: employee.firstName, lastName: employee.lastName, jobTitle: employee.jobTitle ?? '', phone: employee.phone ?? '', hireDate: employee.hireDate ?? '', departmentId: employee.departmentId, managerId: employee.managerId }); }
  protected closeEditor(): void { if (!this.saving()) this.editing.set(null); }
  protected save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    const payload = this.form.getRawValue() as EmployeePayload;
    const current = this.editing()!;
    const request = current.id ? this.api.update(current.id, payload) : this.api.create(payload);
    request.subscribe({ next: () => { this.saving.set(false); this.editing.set(null); this.toast.success(current.id ? 'Employé modifié' : 'Employé créé', 'Les données ont été enregistrées.'); this.load(); }, error: (err: HttpErrorResponse) => { this.saving.set(false); this.toast.error('Enregistrement impossible', err.error?.message ?? 'Vérifiez les données saisies.'); } });
  }
  protected toggle(employee: Employee): void { this.api.setActive(employee.id, !employee.active).subscribe({ next: () => { this.toast.success(employee.active ? 'Employé désactivé' : 'Employé réactivé', 'L’historique métier est conservé.'); this.load(); }, error: () => this.toast.error('Action impossible', 'Réessayez dans un instant.') }); }
  protected invalid(name: string): boolean { const control = this.form.get(name); return !!control && control.invalid && control.touched; }
}