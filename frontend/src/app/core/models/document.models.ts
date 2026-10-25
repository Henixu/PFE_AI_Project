export type DocumentVisibility = 'PRIVATE' | 'INTERNAL';

export const DOCUMENT_VISIBILITIES: DocumentVisibility[] = ['PRIVATE', 'INTERNAL'];

export const DOCUMENT_VISIBILITY_LABELS: Record<DocumentVisibility, string> = {
  PRIVATE: 'Privé',
  INTERNAL: 'Tous les employés'
};

/** Formats acceptés par le serveur (liste blanche) ; le contrôle réel reste côté serveur. */
export const DOCUMENT_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg', 'docx', 'xlsx', 'txt'];
export const DOCUMENT_ACCEPT = DOCUMENT_EXTENSIONS.map((e) => `.${e}`).join(',');
/** Doit rester aligné sur app.documents.max-size-mb (10 Mo par défaut). */
export const DOCUMENT_MAX_BYTES = 10 * 1024 * 1024;

export interface DocumentItem {
  id: number;
  title: string;
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  visibility: DocumentVisibility;
  ownerEmail: string;
  ownerName: string;
  createdAt: string;
  /** L'utilisateur connecté peut supprimer ce document (calculé par le serveur). */
  canDelete: boolean;
}

export interface DocumentQuery {
  search: string;
  mine: boolean;
  page: number;
  size: number;
  sortBy: string;
  direction: 'asc' | 'desc';
}

/** Taille lisible : 1,4 Mo, 230 Ko, 512 o. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toLocaleString('fr-FR', { maximumFractionDigits: 0 })} Ko`;
  return `${(bytes / (1024 * 1024)).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`;
}

/** Extension en majuscules d'un nom de fichier (« PDF »), ou chaîne vide. */
export function fileExtension(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot < 0 ? '' : fileName.substring(dot + 1).toUpperCase();
}
