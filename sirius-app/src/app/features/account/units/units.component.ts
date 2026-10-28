import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {UnitService} from '../../../core/services/unit.service';
import {UnitSystem} from '../../../api/antares/models/user.model';

/** "Units" tab of My account: the display system for loads, heights and distances. */
@Component({
  selector: 'app-units',
  standalone: true,
  imports: [TranslatePipe, NgIcon, HlmCardImports],
  templateUrl: './units.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnitsComponent {
  // --- INJECTIONS ---
  public readonly unitService = inject(UnitService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly systems: UnitSystem[] = ['METRIC', 'IMPERIAL'];

  // --- ACTIONS & EVENTS ---
  select(system: UnitSystem): void {
    if (system !== this.unitService.unitSystem()) this.unitService.setUnitSystem(system);
  }
}
