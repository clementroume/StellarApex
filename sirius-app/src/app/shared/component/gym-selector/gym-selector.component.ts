import {ChangeDetectionStrategy, Component, computed, inject, model} from '@angular/core';
import {HlmSelectImports} from '@spartan-ng/helm/select';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {AccessService} from '../../../core/services/access.service';

/** Select value standing for "all my boxes/progs" (gym ids are positive). */
const ALL_GYMS = 0;
let nextId = 0;

/**
 * Display selector over the user's usable memberships (one box/prog or all). Purely a UI filter:
 * never send it as an "active gym", rights stay evaluated per resource by the backends.
 */
@Component({
  selector: 'app-gym-selector',
  standalone: true,
  imports: [HlmSelectImports, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="sr-only" [for]="triggerId">{{ 'SHARED.GYM_SELECTOR.LABEL' | translate }}</label>
    <hlm-select [value]="selected()" (valueChange)="onChange($event)" [itemToString]="toLabel">
      <hlm-select-trigger class="h-11 md:h-8 max-w-56" [buttonId]="triggerId">
        <hlm-select-value/>
      </hlm-select-trigger>
      <hlm-select-content *hlmSelectPortal>
        <hlm-select-group>
          <hlm-select-item [value]="ALL" class="py-3 md:py-1.5">{{ 'SHARED.GYM_SELECTOR.ALL' | translate }}</hlm-select-item>
          @for (m of memberships(); track m.gymId) {
            <hlm-select-item [value]="m.gymId" class="py-3 md:py-1.5">
              {{ m.gymName }}
              <span class="ms-auto text-xs text-muted-foreground">
                {{ (m.gymProgramming ? 'SHARED.GYM_SELECTOR.PROG' : 'SHARED.GYM_SELECTOR.BOX') | translate }}
              </span>
            </hlm-select-item>
          }
        </hlm-select-group>
      </hlm-select-content>
    </hlm-select>
  `
})
export class GymSelectorComponent {
  // --- INJECTIONS ---
  private readonly accessService = inject(AccessService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** Displayed gym id, null = all of the user's boxes/progs. */
  readonly value = model<number | null>(null);
  protected readonly ALL = ALL_GYMS;
  protected readonly triggerId = `gym-selector-${nextId++}`;

  // --- COMPUTED STATE ---
  protected readonly memberships = this.accessService.memberships;
  protected readonly selected = computed(() => this.value() ?? ALL_GYMS);

  // --- ACTIONS & EVENTS ---
  protected onChange(id: number | null | undefined): void {
    this.value.set(id == null || id === ALL_GYMS ? null : id);
  }

  // --- HELPERS ---
  protected readonly toLabel = (id: number) => id === ALL_GYMS
    ? this.translate.instant('SHARED.GYM_SELECTOR.ALL')
    : this.memberships().find(m => m.gymId === id)?.gymName ?? '';
}
