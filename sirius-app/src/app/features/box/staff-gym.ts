import {ChangeDetectionStrategy, Component, computed, inject, input, output, Signal, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {MembershipSummary, Permission} from '../../api/antares/models/membership.model';
import {NativeSelectDirective} from '../../shared/component/native-select/native-select.directive';

export interface StaffGymSelection {
  /** Usable memberships holding the permission. */
  candidates: Signal<MembershipSummary[]>;
  /** Managed gym: the one picked here, else the displayed box/prog (D7) when allowed, else the first. */
  gymId: Signal<number | null>;
  membership: Signal<MembershipSummary | null>;
  select(gymId: number): void;
}

/**
 * Which box/prog a staff page manages. Follows the display selector when it shows one the user may
 * manage; never sent to the API as an "active gym" (the gym id is part of each request).
 * Call in an injection context.
 */
export function staffGymSelection(permission: Permission): StaffGymSelection {
  const accessService = inject(AccessService);
  const displayGymService = inject(DisplayGymService);
  const picked = signal<number | null>(null);
  const candidates = computed(() => accessService.gymsWithPermission(permission));
  const gymId = computed(() => {
    const ids = candidates().map(m => m.gymId);
    const choice = picked();
    if (choice !== null && ids.includes(choice)) return choice;
    const displayed = displayGymService.gymId();
    if (displayed !== null && ids.includes(displayed)) return displayed;
    return ids[0] ?? null;
  });
  return {
    candidates,
    gymId,
    membership: computed(() => candidates().find(m => m.gymId === gymId()) ?? null),
    select: (id: number) => picked.set(id)
  };
}

let nextId = 0;

/** Native select of the managed box/prog, shown only when the user manages several. */
@Component({
  selector: 'app-staff-gym-picker',
  standalone: true,
  imports: [TranslatePipe, NativeSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (candidates().length > 1) {
      <label class="flex flex-col gap-1.5 md:w-72">
        <span class="text-sm font-medium">{{ 'BOX.STAFF_GYM' | translate }}</span>
        <select appNativeSelect [id]="selectId" (change)="onChange($event)">
          @for (m of candidates(); track m.gymId) {
            <option [value]="m.gymId" [selected]="m.gymId === value()">
              {{ m.gymName }} · {{ (m.gymProgramming ? 'GYM.TYPE.PROGRAM' : 'GYM.TYPE.BOX') | translate }}
            </option>
          }
        </select>
      </label>
    }
  `
})
export class StaffGymPickerComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly candidates = input.required<MembershipSummary[]>();
  readonly value = input<number | null>(null);
  readonly valueChange = output<number>();
  protected readonly selectId = `staff-gym-${nextId++}`;

  // --- ACTIONS & EVENTS ---
  protected onChange(event: Event): void {
    this.valueChange.emit(Number((event.target as HTMLSelectElement).value));
  }
}
