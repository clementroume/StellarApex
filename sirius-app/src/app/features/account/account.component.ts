import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterModule} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon, provideIcons} from '@ng-icons/core';

import {heroBuildingOffice, heroCog8Tooth, heroScale, heroTrophy, heroUser} from '@ng-icons/heroicons/outline';
@Component({
  viewProviders: [provideIcons({heroBuildingOffice, heroCog8Tooth, heroScale, heroTrophy, heroUser})],
  selector: 'app-account',
  standalone: true,
  imports: [RouterModule, TranslatePipe, NgIcon],
  templateUrl: './account.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly menu = [
    {link: 'profile', icon: 'heroUser', label: 'ACCOUNT.MENU_INFO'},
    {link: 'athlete', icon: 'heroTrophy', label: 'ACCOUNT.MENU_ATHLETE'},
    {link: 'gyms', icon: 'heroBuildingOffice', label: 'ACCOUNT.MENU_GYMS'},
    {link: 'units', icon: 'heroScale', label: 'ACCOUNT.MENU_UNITS'},
    {link: 'settings', icon: 'heroCog8Tooth', label: 'ACCOUNT.MENU_SETTINGS'},
  ];
}
