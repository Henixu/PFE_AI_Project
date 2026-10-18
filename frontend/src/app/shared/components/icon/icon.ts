import { Component, computed, input } from '@angular/core';

import { ICONS, IconName } from './icons';

/** Icône SVG décorative. La taille se règle via les classes de l'hôte (ex. class="h-5 w-5"). */
@Component({
  selector: 'app-icon',
  templateUrl: './icon.html',
  host: { class: 'inline-block shrink-0', 'aria-hidden': 'true' }
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly strokeWidth = input(1.8);

  protected readonly path = computed(() => ICONS[this.name()]);
}
