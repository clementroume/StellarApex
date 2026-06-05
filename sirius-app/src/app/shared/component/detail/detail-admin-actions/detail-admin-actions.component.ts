import {Component, EventEmitter, Input, Output, ChangeDetectionStrategy} from '@angular/core';
import {RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-detail-admin-actions',
  standalone: true,
  imports: [RouterModule, TranslateModule, NgIcon],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="pt-8 flex flex-col md:flex-row justify-end gap-4">
      <button class="btn btn-outline btn-error min-w-36" (click)="delete.emit()">
        <ng-icon name="heroTrash" size="1.5rem"></ng-icon>
        {{ 'GLOBAL.DELETE' | translate }}
      </button>
      <a [routerLink]="editLink" class="btn btn-secondary min-w-36">
        <ng-icon name="heroPencilSquare" size="1.5rem"></ng-icon>
        {{ 'GLOBAL.EDIT' | translate }}
      </a>
    </div>
  `
})
export class DetailAdminActionsComponent {
  @Input({required: true}) editLink!: any[] | string;
  @Output() delete = new EventEmitter<void>();
}
