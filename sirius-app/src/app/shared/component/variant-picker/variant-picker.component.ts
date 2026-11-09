import {ChangeDetectionStrategy, Component, computed, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmButton} from '@spartan-ng/helm/button';

/** Next selection after a tap: one value at most from each exclusive set (the backend checks it again). */
export function toggleVariant(selected: string[], value: string, exclusiveSets: string[][] | null | undefined): string[] {
  if (selected.includes(value)) return selected.filter(v => v !== value);
  const rivals = (exclusiveSets ?? []).filter(set => set.includes(value)).flat();
  return [...selected.filter(v => !rivals.includes(v)), value];
}

/**
 * Chips of the equipment or techniques a movement allows. Techniques are grouped by type (reception,
 * start, position...), in the catalog's order.
 */
@Component({
  selector: 'app-variant-picker',
  standalone: true,
  imports: [TranslatePipe, HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-3">
      @for (group of shown(); track group.type) {
        <div role="group" [attr.aria-label]="group.title ? (group.label | translate) : null">
          @if (group.title) {
            <div class="text-xs text-muted-foreground mb-1">{{ group.label | translate }}</div>
          }
          <div class="flex flex-wrap gap-2">
            @for (value of group.values; track value) {
              <button type="button" hlmBtn variant="outline" size="xs" class="aria-pressed:border-secondary aria-pressed:bg-secondary/15 aria-pressed:text-secondary"
                      [attr.aria-pressed]="selected().includes(value)" (click)="toggled.emit(value)">
                {{ prefix() + value | translate }}
              </button>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class VariantPickerComponent {
  /** Values the movement allows. */
  readonly allowed = input.required<string[]>();
  readonly selected = input<string[]>([]);
  /** Type -> values, from the reference data; without it the chips are not grouped. */
  readonly groups = input<Record<string, string[]> | null>(null);
  /** Translation prefix of a value. */
  readonly prefix = input.required<string>();
  /** Translation prefix of a type; no heading without it. */
  readonly typePrefix = input<string>('');
  readonly toggled = output<string>();

  protected readonly shown = computed(() => {
    const allowed = this.allowed();
    const groups = this.groups();
    if (!groups) return [{type: '', label: this.prefix(), title: false, values: [...allowed].sort()}];
    return Object.entries(groups)
      .map(([type, values]) => ({
        type, label: this.typePrefix() + type, title: !!this.typePrefix(), values: values.filter(v => allowed.includes(v))
      }))
      .filter(g => g.values.length > 0);
  });
}
