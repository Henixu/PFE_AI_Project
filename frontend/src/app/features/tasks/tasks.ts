import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EmployeeOption, PageResponse } from '../../core/models/admin-user.models';
import {
  TASK_PRIORITIES,
  TASK_PRIORITY_LABELS,
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  Task,
  TaskPriority,
  TaskQuery,
  TaskStatus
} from '../../core/models/task.models';
import { TasksService } from '../../core/services/tasks.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

/** Taille de la requête en vue Kanban (maximum accepté par l'API) : toutes les colonnes sont alimentées d'un seul appel. */
const KANBAN_SIZE = 100;

function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { blank: true } : null;
}

/** Date du jour au format yyyy-MM-dd, en heure locale (comparable aux valeurs d'un <input type="date">). */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type ViewMode = 'list' | 'kanban';

@Component({
  selector: 'app-tasks',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './tasks.html'
})
export class Tasks {
  private readonly api = inject(TasksService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly statuses = TASK_STATUSES;
  protected readonly priorities = TASK_PRIORITIES;
  protected readonly statusLabels = TASK_STATUS_LABELS;
  protected readonly priorityLabels = TASK_PRIORITY_LABELS;

  protected readonly view = signal<ViewMode>('list');
  protected readonly result = signal<PageResponse<Task> | null>(null);
  protected readonly assignees = signal<EmployeeOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal<TaskQuery>({
    search: '',
    assigneeId: null,
    priority: null,
    status: null,
    overdue: false,
    mine: false,
    dueFrom: '',
    dueTo: '',
    page: 0,
    size: 10,
    sortBy: 'dueDate',
    direction: 'asc'
  });

  /** Colonnes du Kanban : les tâches reçues réparties par statut. */
  protected readonly columns = computed(() => {
    const tasks = this.result()?.content ?? [];
    return TASK_STATUSES.map((status) => ({ status, tasks: tasks.filter((t) => t.status === status) }));
  });
  protected readonly kanbanTruncated = computed(() => {
    const r = this.result();
    return this.view() === 'kanban' && !!r && r.totalElements > r.content.length;
  });

  // Glisser-déposer du Kanban
  protected readonly dragging = signal<Task | null>(null);
  protected readonly dragOver = signal<TaskStatus | null>(null);

  protected readonly selected = signal<Task | null>(null);

  /** Formulaire de création (task null) ou de modification. */
  protected readonly editor = signal<{ task: Task | null } | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.group({
    title: ['', [Validators.required, notBlank, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(4000)],
    priority: this.fb.control<TaskPriority>('MEDIUM', Validators.required),
    dueDate: ['', Validators.pattern(/^$|^\d{4}-\d{2}-\d{2}$/)],
    assigneeId: this.fb.control<number | null>(null)
  });

  constructor() {
    this.load();
    this.api.assignees().subscribe({ next: (items) => this.assignees.set(items) });
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
    const q = this.query();
    const effective: TaskQuery =
      this.view() === 'kanban' ? { ...q, status: null, page: 0, size: KANBAN_SIZE } : q;
    this.api.list(effective).subscribe({
      next: (value) => {
        this.result.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les tâches.');
      }
    });
  }

  protected setView(mode: ViewMode): void {
    if (this.view() === mode) return;
    this.view.set(mode);
    this.result.set(null);
    this.load();
  }

  // ---- filtres et tri ----

  protected search(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.patch({ search: value, page: 0 }), 300);
  }

  protected assignee(value: string): void {
    this.patch({ assigneeId: value ? Number(value) : null, page: 0 });
  }

  protected priority(value: string): void {
    this.patch({ priority: (value || null) as TaskPriority | null, page: 0 });
  }

  protected status(value: string): void {
    this.patch({ status: (value || null) as TaskStatus | null, page: 0 });
  }

  protected resetFilters(): void {
    this.query.update((q) => ({
      ...q,
      search: '',
      assigneeId: null,
      priority: null,
      status: null,
      overdue: false,
      mine: false,
      dueFrom: '',
      dueTo: '',
      page: 0
    }));
    this.load();
  }

  protected hasFilters(): boolean {
    const q = this.query();
    return !!(q.search || q.assigneeId !== null || q.priority || q.status || q.overdue || q.mine || q.dueFrom || q.dueTo);
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

  protected patch(value: Partial<TaskQuery>): void {
    this.query.update((q) => ({ ...q, ...value }));
    this.load();
  }

  // ---- statut ----

  protected move(task: Task, value: string): void {
    const status = value as TaskStatus;
    if (!value || status === task.status) return;
    if (!task.allowedStatuses.includes(status)) {
      this.toast.warning('Déplacement non autorisé', `Cette tâche ne peut pas passer de « ${TASK_STATUS_LABELS[task.status]} » à « ${TASK_STATUS_LABELS[status]} ».`);
      return;
    }
    this.api.changeStatus(task.id, status).subscribe({
      next: (updated) => {
        this.result.update((r) => (r ? { ...r, content: r.content.map((t) => (t.id === updated.id ? updated : t)) } : r));
        this.toast.success('Statut mis à jour', `« ${updated.title} » : ${TASK_STATUS_LABELS[updated.status]}.`);
      },
      error: (err: HttpErrorResponse) => {
        this.toast.error('Changement de statut impossible', this.messageOf(err, 'Réessayez dans un instant.'));
        this.load();
      }
    });
  }

  protected onDragStart(event: DragEvent, task: Task): void {
    if (!task.allowedStatuses.length) {
      event.preventDefault();
      return;
    }
    event.dataTransfer?.setData('text/plain', String(task.id));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    this.dragging.set(task);
  }

  protected onDragEnd(): void {
    this.dragging.set(null);
    this.dragOver.set(null);
  }

  /** Une colonne n'accepte le dépôt que si la transition est autorisée pour la tâche déplacée. */
  protected canDrop(status: TaskStatus): boolean {
    const task = this.dragging();
    return !!task && task.allowedStatuses.includes(status);
  }

  protected onDragOver(event: DragEvent, status: TaskStatus): void {
    if (!this.canDrop(status)) return;
    event.preventDefault();
    this.dragOver.set(status);
  }

  protected onDrop(event: DragEvent, status: TaskStatus): void {
    event.preventDefault();
    const task = this.dragging();
    this.onDragEnd();
    if (task && task.status !== status) this.move(task, status);
  }

  // ---- affichage ----

  protected priorityClass(priority: TaskPriority): string {
    return { LOW: 'badge-neutral', MEDIUM: 'badge-brand', HIGH: 'badge-warning', URGENT: 'badge-danger' }[priority];
  }

  protected statusClass(status: TaskStatus): string {
    return { TODO: 'badge-neutral', IN_PROGRESS: 'badge-brand', DONE: 'badge-success', CANCELLED: 'badge-neutral' }[status];
  }

  // ---- création / modification ----

  protected openCreate(): void {
    this.formError.set(null);
    const only = this.assignees();
    this.form.reset({
      title: '',
      description: '',
      priority: 'MEDIUM',
      dueDate: '',
      assigneeId: only.length === 1 ? only[0].id : null
    });
    this.editor.set({ task: null });
  }

  protected openEdit(task: Task): void {
    this.formError.set(null);
    this.form.reset({
      title: task.title,
      description: task.description ?? '',
      priority: task.priority,
      dueDate: task.dueDate ?? '',
      assigneeId: task.assigneeId
    });
    this.editor.set({ task });
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
    const original = editor.task;
    if (value.dueDate && value.dueDate < todayIso() && value.dueDate !== original?.dueDate) {
      this.formError.set("L'échéance ne peut pas être dans le passé.");
      return;
    }
    const payload = {
      title: value.title.trim(),
      description: value.description.trim() || null,
      priority: value.priority,
      dueDate: value.dueDate || null,
      assigneeId: value.assigneeId
    };
    this.saving.set(true);
    this.formError.set(null);
    const request = original ? this.api.update(original.id, payload) : this.api.create(payload);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.editor.set(null);
        this.toast.success(original ? 'Tâche modifiée' : 'Tâche créée', 'Les données ont été enregistrées.');
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

  private messageOf(err: HttpErrorResponse, fallback: string): string {
    return typeof err.error?.message === 'string' ? err.error.message : fallback;
  }
}
