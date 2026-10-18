import { Component, inject } from '@angular/core';

import { ThemeService } from '../../../core/services/theme.service';
import { Icon } from '../icon/icon';

@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  templateUrl: './theme-toggle.html'
})
export class ThemeToggle {
  protected readonly themeService = inject(ThemeService);
}
