import { Component, inject } from '@angular/core';

import { ToastService, ToastType } from '../../../core/services/toast.service';
import { Icon } from '../icon/icon';
import { IconName } from '../icon/icons';

interface ToastStyle {
  icon: IconName;
  color: string;
  bar: string;
}

const TOAST_STYLES: Record<ToastType, ToastStyle> = {
  success: { icon: 'check-circle', color: 'text-emerald-600 dark:text-emerald-400', bar: 'bg-emerald-500' },
  error: { icon: 'x-circle', color: 'text-rose-600 dark:text-rose-400', bar: 'bg-rose-500' },
  warning: { icon: 'exclamation-triangle', color: 'text-amber-600 dark:text-amber-400', bar: 'bg-amber-500' },
  info: { icon: 'information-circle', color: 'text-sky-600 dark:text-sky-400', bar: 'bg-sky-500' }
};

@Component({
  selector: 'app-toast-container',
  imports: [Icon],
  templateUrl: './toast-container.html'
})
export class ToastContainer {
  protected readonly toastService = inject(ToastService);
  protected readonly styles = TOAST_STYLES;
}
