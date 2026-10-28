import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {RouterModule} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';

import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-detail-admin-actions',
  standalone: true,
  imports: [RouterModule, TranslatePipe, NgIcon, HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pt-8 flex flex-col md:flex-row justify-end gap-3">
      <button hlmBtn variant="destructive" size="lg" class="min-w-36" (click)="delete.emit()">
        <ng-icon name="heroTrash" aria-hidden="true"/>
        {{ 'GLOBAL.DELETE' | translate }}
      </button>
      <a hlmBtn size="lg" class="min-w-36" [routerLink]="editLink()">
        <ng-icon name="heroPencilSquare" aria-hidden="true"/>
        {{ 'GLOBAL.EDIT' | translate }}
      </a>
    </div>
  `
})
export class DetailAdminActionsComponent {
  editLink = input.required<any[] | string>();
  delete = output<void>();
}
