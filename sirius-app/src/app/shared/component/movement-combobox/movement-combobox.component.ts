import {ChangeDetectionStrategy, Component, computed, input, model} from '@angular/core';
import {ComboboxFilter, comboboxContainsFilter} from '@spartan-ng/brain/combobox';
import {HlmComboboxImports} from '@spartan-ng/helm/combobox';
import {TranslatePipe} from '@ngx-translate/core';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';

let nextId = 0;

/**
 * Searchable catalog movement picker (spartan/ui combobox). The parent loads the movements once
 * (`MovementService.getMovements`) so several pickers on one form share them.
 */
@Component({
  selector: 'app-movement-combobox',
  standalone: true,
  imports: [HlmComboboxImports, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="sr-only" [for]="inputId">{{ label() || ('SHARED.MOVEMENT_COMBOBOX.LABEL' | translate) }}</label>
    <hlm-combobox [value]="selected()" (valueChange)="value.set($event?.id ?? null)"
                  [itemToString]="toName" [filter]="filter" autoHighlight>
      <hlm-combobox-input class="w-full h-12 md:h-10" showClear [inputId]="inputId"
                          [placeholder]="'SHARED.MOVEMENT_COMBOBOX.PLACEHOLDER' | translate"
                          [triggerAriaLabel]="'SHARED.MOVEMENT_COMBOBOX.TOGGLE' | translate"
                          [clearAriaLabel]="'GLOBAL.CLEAR' | translate"/>
      <hlm-combobox-content *hlmComboboxPortal>
        <hlm-combobox-empty>{{ 'SHARED.MOVEMENT_COMBOBOX.NO_RESULT' | translate }}</hlm-combobox-empty>
        <div hlmComboboxList>
          @for (m of movements(); track m.id) {
            <hlm-combobox-item [value]="m" class="py-3 md:py-1.5">
              <span>{{ m.name }}
                @if (m.nameAbbreviation) {
                  <span class="text-muted-foreground">({{ m.nameAbbreviation }})</span>
                }
              </span>
              <span class="ms-auto text-xs text-muted-foreground">{{ 'MOVEMENT.CATEGORIES.' + m.category | translate }}</span>
            </hlm-combobox-item>
          }
        </div>
      </hlm-combobox-content>
    </hlm-combobox>
  `
})
export class MovementComboboxComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly movements = input.required<MovementSummaryResponse[]>();
  /** Accessible name (visually hidden); defaults to "Movement". */
  readonly label = input<string>('');
  /** Selected movement id. */
  readonly value = model<number | null>(null);

  protected readonly inputId = `movement-combobox-${nextId++}`;
  protected readonly toName = (m: MovementSummaryResponse) => m.name;
  /** Accent-insensitive (Intl.Collator) match on the name or the abbreviation. */
  protected readonly filter: ComboboxFilter<MovementSummaryResponse> = (m, search, collator) =>
    comboboxContainsFilter(m.name, search, collator)
    || comboboxContainsFilter(m.nameAbbreviation ?? '', search, collator);

  // --- COMPUTED STATE ---
  protected readonly selected = computed(() => this.movements().find(m => m.id === this.value()) ?? null);
}
