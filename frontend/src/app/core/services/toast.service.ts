import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  title: string;
  message?: string;
  /** Durée d'affichage en ms (0 = jusqu'à fermeture manuelle). */
  duration: number;
}

const MAX_TOASTS = 4;

/** Notifications éphémères affichées par ToastContainer (en haut à droite). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  readonly toasts = signal<Toast[]>([]);

  success(title: string, message?: string, duration = 4000): void {
    this.show('success', title, message, duration);
  }

  error(title: string, message?: string, duration = 6000): void {
    this.show('error', title, message, duration);
  }

  warning(title: string, message?: string, duration = 5000): void {
    this.show('warning', title, message, duration);
  }

  info(title: string, message?: string, duration = 4000): void {
    this.show('info', title, message, duration);
  }

  dismiss(id: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private show(type: ToastType, title: string, message: string | undefined, duration: number): void {
    const toast: Toast = { id: this.nextId++, type, title, message, duration };
    this.toasts.update((list) => {
      const next = [...list, toast];
      next.slice(0, Math.max(0, next.length - MAX_TOASTS)).forEach((old) => this.clearTimer(old.id));
      return next.slice(-MAX_TOASTS);
    });
    if (duration > 0) {
      this.timers.set(toast.id, setTimeout(() => this.dismiss(toast.id), duration));
    }
  }

  private clearTimer(id: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
  }
}
