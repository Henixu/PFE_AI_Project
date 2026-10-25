import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { PageResponse } from '../../core/models/admin-user.models';
import {
  DOCUMENT_ACCEPT,
  DOCUMENT_EXTENSIONS,
  DOCUMENT_MAX_BYTES,
  DOCUMENT_VISIBILITIES,
  DOCUMENT_VISIBILITY_LABELS,
  DocumentItem,
  DocumentQuery,
  DocumentVisibility,
  fileExtension,
  formatFileSize
} from '../../core/models/document.models';
import { AuthService } from '../../core/services/auth.service';
import { DocumentsService } from '../../core/services/documents.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert } from '../../shared/components/alert/alert';
import { AppShell } from '../../shared/components/app-shell/app-shell';
import { Icon } from '../../shared/components/icon/icon';
import { IconName } from '../../shared/components/icon/icons';

/** Déclenche l'enregistrement d'un fichier reçu sous forme de Blob. */
function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = fileName;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

@Component({
  selector: 'app-documents',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AppShell, Alert, Icon],
  templateUrl: './documents.html'
})
export class Documents {
  private readonly api = inject(DocumentsService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private searchTimer?: ReturnType<typeof setTimeout>;

  protected readonly accept = DOCUMENT_ACCEPT;
  protected readonly extensions = DOCUMENT_EXTENSIONS.map((e) => e.toUpperCase()).join(', ');
  protected readonly maxSize = formatFileSize(DOCUMENT_MAX_BYTES);
  protected readonly visibilities = DOCUMENT_VISIBILITIES;
  protected readonly visibilityLabels = DOCUMENT_VISIBILITY_LABELS;
  protected readonly formatSize = formatFileSize;
  protected readonly extensionOf = fileExtension;

  /** Seuls les RH et administrateurs peuvent publier un document pour tous (le serveur le vérifie aussi). */
  protected readonly canPublish = computed(() => this.auth.hasAnyRole(['ADMIN', 'HR']));

  protected readonly result = signal<PageResponse<DocumentItem> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal<DocumentQuery>({ search: '', mine: false, page: 0, size: 10, sortBy: 'createdAt', direction: 'desc' });

  protected readonly selected = signal<DocumentItem | null>(null);
  protected readonly downloadingId = signal<number | null>(null);

  protected readonly uploadOpen = signal(false);
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);
  protected readonly file = signal<File | null>(null);
  protected readonly fileError = signal<string | null>(null);
  protected readonly fileTouched = signal(false);
  protected readonly form = this.fb.group({
    title: ['', [Validators.maxLength(200)]],
    visibility: this.fb.control<DocumentVisibility>('PRIVATE')
  });

  protected readonly deleting = signal<DocumentItem | null>(null);
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
        this.error.set('Impossible de charger les documents.');
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

  protected patch(value: Partial<DocumentQuery>): void {
    this.query.update((q) => ({ ...q, ...value }));
    this.load();
  }

  protected visibilityClass(visibility: DocumentVisibility): string {
    return visibility === 'INTERNAL' ? 'badge-brand' : 'badge-neutral';
  }

  // ---- téléchargement ----

  protected download(doc: DocumentItem): void {
    this.downloadingId.set(doc.id);
    this.api.download(doc.id).subscribe({
      next: (blob) => {
        saveBlob(blob, doc.fileName);
        this.downloadingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.downloadingId.set(null);
        if (err.status === 404) {
          this.toast.error('Téléchargement impossible', 'Ce document ou son fichier n\'est plus disponible.');
          this.load();
        } else if (err.status !== 403) {
          this.toast.error('Téléchargement impossible', 'Réessayez dans un instant.');
        }
      }
    });
  }

  // ---- ajout ----

  protected openUpload(): void {
    this.uploadError.set(null);
    this.fileError.set(null);
    this.fileTouched.set(false);
    this.file.set(null);
    this.form.reset({ title: '', visibility: 'PRIVATE' });
    this.uploadOpen.set(true);
  }

  protected closeUpload(): void {
    if (!this.uploading()) this.uploadOpen.set(false);
  }

  /** Contrôle rapide côté navigateur (extension, taille) ; le serveur refait tous les contrôles. */
  protected onFileSelected(event: Event): void {
    const chosen = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.fileTouched.set(true);
    this.file.set(null);
    if (!chosen) {
      this.fileError.set('Sélectionnez un fichier.');
    } else if (!DOCUMENT_EXTENSIONS.includes(fileExtension(chosen.name).toLowerCase())) {
      this.fileError.set(`Format non autorisé. Formats acceptés : ${this.extensions}.`);
    } else if (chosen.size === 0) {
      this.fileError.set('Le fichier est vide.');
    } else if (chosen.size > DOCUMENT_MAX_BYTES) {
      this.fileError.set(`Fichier trop volumineux (${this.maxSize} maximum).`);
    } else {
      this.fileError.set(null);
      this.file.set(chosen);
    }
  }

  protected titleInvalid(): boolean {
    const control = this.form.controls.title;
    return control.invalid && control.touched;
  }

  protected upload(): void {
    this.fileTouched.set(true);
    const chosen = this.file();
    if (!chosen) {
      this.fileError.set(this.fileError() ?? 'Sélectionnez un fichier.');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.uploading.set(true);
    this.uploadError.set(null);
    this.api.upload(chosen, value.title, this.canPublish() ? value.visibility : 'PRIVATE').subscribe({
      next: () => {
        this.uploading.set(false);
        this.uploadOpen.set(false);
        this.toast.success('Document ajouté', chosen.name);
        this.query.update((q) => ({ ...q, page: 0 }));
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        this.uploadError.set(this.messageOf(err, 'Envoi impossible. Vérifiez le fichier sélectionné.'));
      }
    });
  }

  // ---- suppression ----

  protected confirmDelete(doc: DocumentItem): void {
    this.deleting.set(doc);
  }

  protected closeDelete(): void {
    if (!this.deleteBusy()) this.deleting.set(null);
  }

  protected remove(): void {
    const doc = this.deleting();
    if (!doc) return;
    this.deleteBusy.set(true);
    this.api.remove(doc.id).subscribe({
      next: () => {
        this.deleteBusy.set(false);
        this.deleting.set(null);
        this.selected.set(null);
        this.toast.success('Document supprimé');
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
