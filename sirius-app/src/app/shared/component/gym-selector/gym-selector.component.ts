import {ChangeDetectionStrategy, Component, inject, model} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {AccessService} from '../../../core/services/access.service';
import {NativeSelectDirective} from '../native-select/native-select.directive';

/** Select value standing for "all my boxes/progs" (gym ids are positive). */
const ALL_GYMS = 0;
let nextId = 0;

/**
 * Display selector over the user's usable memberships (one box/prog or all). Purely a UI filter:
 * never send it as an "active gym", rights stay evaluated per resource by the backends.
 * A native select on purpose: short closed list, native picker on mobile, nothing added to the
 * initial bundle although it sits in the top bar.
 */
@Component({
  selector: 'app-gym-selector',
  standalone: true,
  imports: [TranslatePipe, NativeSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="sr-only" [for]="selectId">{{ 'SHARED.GYM_SELECTOR.LABEL' | translate }}</label>
    <select appNativeSelect [id]="selectId" class="h-9 md:h-9 max-w-44 md:max-w-56 truncate" (change)="onChange($event)">
      <option [value]="ALL" [selected]="value() === null">{{ 'SHARED.GYM_SELECTOR.ALL' | translate }}</option>
      @for (m of memberships(); track m.gymId) {
        <option [value]="m.gymId" [selected]="value() === m.gymId">
          {{ m.gymName }} · {{ (m.gymProgramming ? 'SHARED.GYM_SELECTOR.PROG' : 'SHARED.GYM_SELECTOR.BOX') | translate }}
        </option>
      }
    </select>
  `
})
export class GymSelectorComponent {
  // --- INJECTIONS ---
  private readonly accessService = inject(AccessService);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** Displayed gym id, null = all of the user's boxes/progs. */
  readonly value = model<number | null>(null);
  protected readonly ALL = ALL_GYMS;
  protected readonly selectId = `gym-selector-${nextId++}`;

  // --- COMPUTED STATE ---
  protected readonly memberships = this.accessService.memberships;

  // --- ACTIONS & EVENTS ---
  protected onChange(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value);
    this.value.set(id === ALL_GYMS ? null : id);
  }
}
