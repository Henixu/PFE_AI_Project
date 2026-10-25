import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/admin-user.models';
import { DocumentItem, DocumentQuery, DocumentVisibility } from '../models/document.models';

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/documents`;

  list(query: DocumentQuery): Observable<PageResponse<DocumentItem>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('direction', query.direction);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    if (query.mine) params = params.set('mine', true);
    return this.http.get<PageResponse<DocumentItem>>(this.base, { params });
  }

  /** Envoi multipart : l'en-tête Content-Type (avec sa frontière) est posé par le navigateur. */
  upload(file: File, title: string, visibility: DocumentVisibility): Observable<DocumentItem> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (title.trim()) body.append('title', title.trim());
    body.append('visibility', visibility);
    return this.http.post<DocumentItem>(this.base, body);
  }

  /** Le téléchargement passe par HttpClient pour que le jeton soit joint (un simple lien ne le ferait pas). */
  download(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/download`, { responseType: 'blob' });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
