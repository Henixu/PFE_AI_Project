import { Location } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Icon } from './icon/icon';
import { ThemeToggle } from './theme-toggle/theme-toggle';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ThemeToggle, Icon],
  templateUrl: './forbidden.html'
})
export class Forbidden {
  private readonly location = inject(Location);

  protected back(): void {
    this.location.back();
  }
}
