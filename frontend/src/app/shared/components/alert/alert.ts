import { Component, computed, input } from '@angular/core';

export type AlertType = 'error' | 'success' | 'warning' | 'info';

const ALERT_STYLES: Record<AlertType, string> = {
  error:
    'animate-shake border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-200',
  success:
    'animate-fade-in border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-200',
  warning:
    'animate-fade-in border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200',
  info: 'animate-fade-in border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-200'
};

/** Message en ligne (erreurs de formulaire, confirmations). Le texte est projeté via <ng-content>. */
@Component({
  selector: 'app-alert',
  templateUrl: './alert.html',
  host: { class: 'block' }
})
export class Alert {
  readonly type = input<AlertType>('error');
  readonly title = input<string>();

  protected readonly styles = computed(() => ALERT_STYLES[this.type()]);
}
