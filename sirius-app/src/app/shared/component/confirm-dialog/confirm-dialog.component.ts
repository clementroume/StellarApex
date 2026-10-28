import {ChangeDetectionStrategy, Component, computed, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {BrnDialogState} from '@spartan-ng/brain/dialog';
import {HlmAlertDialogImports} from '@spartan-ng/helm/alert-dialog';

/**
 * Confirmation of a sensitive action (spartan alert dialog: focus trapped, Escape closes, focus
 * given back). Extra fields (e.g. typing a name to confirm) are projected between the description
 * and the buttons; `confirmDisabled` keeps the action off until they are valid.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [TranslatePipe, NgIcon, HlmAlertDialogImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <hlm-alert-dialog [state]="state()" (closed)="closed.emit()">
      <hlm-alert-dialog-content *hlmAlertDialogPortal>
        <hlm-alert-dialog-header>
          <h2 hlmAlertDialogTitle class="flex items-center gap-2" [class.text-destructive]="destructive()">
            @if (destructive()) {
              <ng-icon name="heroExclamationTriangle" size="1.25rem" aria-hidden="true"/>
            }
            {{ title() }}
          </h2>
          @if (description()) {
            <p hlmAlertDialogDescription>{{ description() }}</p>
          }
        </hlm-alert-dialog-header>
        <ng-content/>
        <hlm-alert-dialog-footer>
          <button hlmAlertDialogCancel>{{ 'GLOBAL.CANCEL' | translate }}</button>
          <button hlmAlertDialogAction [variant]="destructive() ? 'destructive' : 'default'"
                  [disabled]="confirmDisabled()" (click)="confirmed.emit()">
            {{ confirmLabel() || ('GLOBAL.CONFIRM' | translate) }}
          </button>
        </hlm-alert-dialog-footer>
      </hlm-alert-dialog-content>
    </hlm-alert-dialog>
  `
})
export class ConfirmDialogComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly open = input<boolean>(false);
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly confirmLabel = input<string>();
  readonly destructive = input<boolean>(false);
  readonly confirmDisabled = input<boolean>(false);
  readonly confirmed = output<void>();
  readonly closed = output<void>();

  // --- COMPUTED STATE ---
  protected readonly state = computed<BrnDialogState>(() => (this.open() ? 'open' : 'closed'));
}
