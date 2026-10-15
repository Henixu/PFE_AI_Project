import { Component, inject } from '@angular/core';

import { ToastService, ToastType } from '../../../core/services/toast.service';

interface ToastStyle {
  card: string;
  icon: string;
  bar: string;
}

const TOAST_STYLES: Record<ToastType, ToastStyle> = {
  success: {
    card: 'border-emerald-200 shadow-emerald-500/10 dark:border-emerald-500/30',
    icon: 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400',
    bar: 'bg-emerald-500'
  },
  error: {
    card: 'border-rose-200 shadow-rose-500/10 dark:border-rose-500/30',
    icon: 'bg-rose-100 text-rose-600 dark:bg-rose-500/15 dark:text-rose-400',
    bar: 'bg-rose-500'
  },
  warning: {
    card: 'border-amber-200 shadow-amber-500/10 dark:border-amber-500/30',
    icon: 'bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-400',
    bar: 'bg-amber-500'
  },
  info: {
    card: 'border-sky-200 shadow-sky-500/10 dark:border-sky-500/30',
    icon: 'bg-sky-100 text-sky-600 dark:bg-sky-500/15 dark:text-sky-400',
    bar: 'bg-sky-500'
  }
};

@Component({
  selector: 'app-toast-container',
  templateUrl: './toast-container.html'
})
export class ToastContainer {
  protected readonly toastService = inject(ToastService);
  protected readonly styles = TOAST_STYLES;
}
