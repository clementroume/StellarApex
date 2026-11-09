import {ChangeDetectionStrategy, Component, computed, inject, input, output} from '@angular/core';
import {ComboboxFilter, comboboxContainsFilter} from '@spartan-ng/brain/combobox';
import {HlmComboboxImports} from '@spartan-ng/helm/combobox';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {UsualNameOption} from '../../../api/aldebaran/models/movement.model';

let nextId = 0;

/** The most precise usual name a line contains; component techniques are written 1:POWER. */
export function usualNameOf(options: UsualNameOption[], movementId: number | null | undefined, techniques: string[] | null | undefined,
                            equipment: string[] | null | undefined): UsualNameOption | null {
  const fits = (o: UsualNameOption) => o.techniques.every(t => techniques?.includes(t))
    && o.equipment.every(e => equipment?.includes(e) || e === o.impliedEquipment);
  const size = (o: UsualNameOption) => o.techniques.length + o.equipment.length;
  return options.filter(o => o.movementId === movementId && fits(o)).sort((a, b) => size(b) - size(a))[0] ?? null;
}

/**
 * Searchable movement picker (spartan/ui combobox), driven by the usual names of the catalog: picking
 * "Power Clean" selects the Clean with its POWER technique. The parent loads the names once
 * (`MovementService.getUsualNames`) so several pickers on one form share them.
 */
@Component({
  selector: 'app-movement-combobox',
  standalone: true,
  imports: [HlmComboboxImports, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="sr-only" [for]="inputId">{{ label() || ('SHARED.MOVEMENT_COMBOBOX.LABEL' | translate) }}</label>
    <hlm-combobox [value]="selected()" (valueChange)="picked.emit($event ?? null)"
                  [itemToString]="toName" [filter]="filter" autoHighlight>
      <hlm-combobox-input class="w-full h-12 md:h-10" showClear [inputId]="inputId"
                          [placeholder]="'SHARED.MOVEMENT_COMBOBOX.PLACEHOLDER' | translate"
                          [triggerAriaLabel]="'SHARED.MOVEMENT_COMBOBOX.TOGGLE' | translate"
                          [clearAriaLabel]="'GLOBAL.CLEAR' | translate"/>
      <hlm-combobox-content *hlmComboboxPortal>
        <hlm-combobox-empty>{{ 'SHARED.MOVEMENT_COMBOBOX.NO_RESULT' | translate }}</hlm-combobox-empty>
        <div hlmComboboxList>
          @for (o of options(); track o.nameEn) {
            <hlm-combobox-item [value]="o" class="py-3 md:py-1.5">
              <span>{{ toName(o) }}</span>
              <span class="ms-auto text-xs text-muted-foreground">{{ 'MOVEMENT.CATEGORIES.' + o.category | translate }}</span>
            </hlm-combobox-item>
          }
        </div>
      </hlm-combobox-content>
    </hlm-combobox>
  `
})
export class MovementComboboxComponent {
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly options = input.required<UsualNameOption[]>();
  /** Accessible name (visually hidden); defaults to "Movement". */
  readonly label = input<string>('');
  /** Selected movement id and the line's variants (a component technique as 1:POWER), to show the matching name. */
  readonly value = input<number | null>(null);
  readonly techniques = input<string[] | null>(null);
  readonly equipment = input<string[] | null>(null);
  /** The entry picked, or null when cleared. */
  readonly picked = output<UsualNameOption | null>();

  protected readonly inputId = `movement-combobox-${nextId++}`;
  protected readonly toName = (o: UsualNameOption) =>
    (this.translate.currentLang() === 'fr' ? o.nameFr : null) || o.nameEn;
  /** Accent-insensitive (Intl.Collator) match on the names and the search terms. */
  protected readonly filter: ComboboxFilter<UsualNameOption> = (o, search, collator) =>
    [o.nameEn, o.nameFr, o.searchTerms, o.movementName].some(t => comboboxContainsFilter(t ?? '', search, collator));

  // --- COMPUTED STATE ---
  /** The most precise entry the line contains. */
  protected readonly selected = computed(() => usualNameOf(this.options(), this.value(), this.techniques(), this.equipment()));
}
