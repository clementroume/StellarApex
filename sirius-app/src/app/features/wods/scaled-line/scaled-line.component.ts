import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {TranslatePipe} from '@ngx-translate/core';
import {UsualNameOption} from '../../../api/aldebaran/models/movement.model';
import {WodScaledLineRequest, WodScaledLineResponse} from '../../../api/aldebaran/models/wod.model';
import {MovementComboboxComponent} from '../../../shared/component/movement-combobox/movement-combobox.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {HlmInput} from '@spartan-ng/helm/input';

/**
 * A line of the WOD's official Scaled version being written (lot S, D141, D154): changed or kept RX, the movement
 * (a usual name of the catalog), its reps and the men's / women's quantities, in the units of the RX line.
 */
export type ScaledLineForm = FormGroup<{
  changed: FormControl<boolean>;
  movementId: FormControl<number | null>;
  equipment: FormControl<string[]>;
  techniques: FormControl<string[]>;
  reps: FormControl<string>;
  weight: FormControl<number | null>;
  weightFemale: FormControl<number | null>;
  distance: FormControl<number | null>;
  calories: FormControl<number | null>;
  caloriesFemale: FormControl<number | null>;
  height: FormControl<number | null>;
  heightFemale: FormControl<number | null>;
  durationSeconds: FormControl<number | null>;
}>;

/** A Scaled line in the form, kept RX when there is none. */
export function scaledLineGroup(scaled?: WodScaledLineResponse | null): ScaledLineForm {
  const number = (v?: number | null) => new FormControl<number | null>(v ?? null);
  return new FormGroup({
    changed: new FormControl<boolean>(!!scaled, {nonNullable: true}),
    movementId: new FormControl<number | null>(scaled?.movement.id ?? null),
    equipment: new FormControl<string[]>([...(scaled?.equipment ?? [])], {nonNullable: true}),
    techniques: new FormControl<string[]>([...(scaled?.techniques ?? [])], {nonNullable: true}),
    reps: new FormControl<string>(scaled?.repsScheme ?? '', {nonNullable: true}),
    weight: number(scaled?.weight), weightFemale: number(scaled?.weightFemale),
    distance: number(scaled?.distance),
    calories: number(scaled?.calories), caloriesFemale: number(scaled?.caloriesFemale),
    height: number(scaled?.height), heightFemale: number(scaled?.heightFemale),
    durationSeconds: number(scaled?.durationSeconds)
  });
}

/**
 * The request of a Scaled line, or null when kept RX. Quantities are in the units of the RX line; a value equal to
 * the RX one is sent anyway (Aldebaran drops a version identical to the RX line).
 */
export function scaledLineRequest(group: ScaledLineForm, units: {weightUnit?: string | null; distanceUnit?: string | null;
  heightUnit?: string | null}): WodScaledLineRequest | null {
  const v = group.getRawValue();
  if (!v.changed || v.movementId == null) return null;
  const load = v.weight != null || v.weightFemale != null;
  const height = v.height != null || v.heightFemale != null;
  return {
    movementId: v.movementId,
    equipment: v.equipment,
    techniques: v.techniques,
    repsScheme: v.reps.trim() || null,
    weight: v.weight, weightFemale: v.weightFemale, weightUnit: load ? units.weightUnit ?? null : null,
    distance: v.distance, distanceUnit: v.distance != null ? units.distanceUnit ?? 'METERS' : null,
    calories: v.calories, caloriesFemale: v.caloriesFemale,
    height: v.height, heightFemale: v.heightFemale, heightUnit: height ? units.heightUnit ?? null : null,
    durationSeconds: v.durationSeconds
  };
}

/** Which quantities the RX line prescribes, so the Scaled line asks the same ones. */
export interface ScaledMeasures {
  load: boolean;
  distance: boolean;
  calories: boolean;
  height: boolean;
  duration: boolean;
}

/**
 * The block "Scaled" under a WOD line (D154, maquette version-scaled-wod-v1): "as RX" by default, "Change" opens the
 * movement picker, the reps and the men's / women's quantities the RX line has.
 */
@Component({
  selector: 'app-scaled-line',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, MovementComboboxComponent, NativeSelectDirective, HlmInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [formGroup]="group()" class="scaled-line flex flex-col gap-2 border-l-4 border-secondary bg-secondary/10 rounded-r-xl px-3 py-2">
      <div class="flex items-center justify-between gap-2 text-sm">
        <span><span class="font-semibold text-secondary">Scaled</span>
          @if (!group().controls.changed.value) { · {{ 'WOD.SCALED.AS_RX' | translate }} }</span>
        <button type="button" class="text-sm text-primary hover:underline" (click)="toggle()">
          {{ (group().controls.changed.value ? 'WOD.SCALED.AS_RX' : 'WOD.SCALED.CHANGE') | translate }}</button>
      </div>
      @if (group().controls.changed.value) {
        <app-movement-combobox [options]="options()" [label]="'WOD.FIELDS.MOVEMENT' | translate"
                               [value]="group().controls.movementId.value" [techniques]="group().controls.techniques.value"
                               [equipment]="group().controls.equipment.value" (picked)="onPicked($event)"/>
        <div class="flex flex-wrap items-end gap-2">
          <label class="flex flex-col gap-1 w-24">
            <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.REPS' | translate }}</span>
            <input type="text" formControlName="reps" hlmInput class="h-10" [placeholder]="rxReps()"/>
          </label>
          @if (measures().load) {
            <div class="flex flex-col gap-1">
              <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.LOAD' | translate }} ({{ massLabel() }})</span>
              <div class="flex items-center gap-1">
                @if (loadChoices(); as weights) {
                  <select appNativeSelect formControlName="weight" class="h-10 w-20" [attr.aria-label]="'WOD.QUICK.MEN' | translate">
                    <option [ngValue]="null">–</option>
                    @for (w of weights; track w) { <option [ngValue]="w">{{ w }}</option> }
                  </select>
                  <span aria-hidden="true">/</span>
                  <select appNativeSelect formControlName="weightFemale" class="h-10 w-20" [attr.aria-label]="'WOD.QUICK.WOMEN' | translate">
                    <option [ngValue]="null">–</option>
                    @for (w of weights; track w) { <option [ngValue]="w">{{ w }}</option> }
                  </select>
                } @else {
                  <input type="number" min="0" step="0.5" inputmode="decimal" formControlName="weight" hlmInput class="h-10 w-20"
                         [attr.aria-label]="'WOD.QUICK.MEN' | translate"/>
                  <span aria-hidden="true">/</span>
                  <input type="number" min="0" step="0.5" inputmode="decimal" formControlName="weightFemale" hlmInput class="h-10 w-20"
                         [attr.aria-label]="'WOD.QUICK.WOMEN' | translate"/>
                }
              </div>
            </div>
          }
          @if (measures().distance) {
            <label class="flex flex-col gap-1">
              <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.DISTANCE' | translate }} (m)</span>
              <input type="number" min="0" inputmode="numeric" formControlName="distance" hlmInput class="h-10 w-24"/>
            </label>
          }
          @if (measures().calories) {
            <div class="flex flex-col gap-1">
              <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.CALORIES' | translate }}</span>
              <div class="flex items-center gap-1">
                <input type="number" min="0" inputmode="numeric" formControlName="calories" hlmInput class="h-10 w-20"
                       [attr.aria-label]="'WOD.QUICK.MEN' | translate"/>
                <span aria-hidden="true">/</span>
                <input type="number" min="0" inputmode="numeric" formControlName="caloriesFemale" hlmInput class="h-10 w-20"
                       [attr.aria-label]="'WOD.QUICK.WOMEN' | translate"/>
              </div>
            </div>
          }
          @if (measures().height) {
            <div class="flex flex-col gap-1">
              <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.HEIGHT' | translate }} ({{ heightLabel() }})</span>
              <div class="flex items-center gap-1">
                <input type="number" min="0" inputmode="decimal" formControlName="height" hlmInput class="h-10 w-20"
                       [attr.aria-label]="'WOD.QUICK.MEN' | translate"/>
                <span aria-hidden="true">/</span>
                <input type="number" min="0" inputmode="decimal" formControlName="heightFemale" hlmInput class="h-10 w-20"
                       [attr.aria-label]="'WOD.QUICK.WOMEN' | translate"/>
              </div>
            </div>
          }
          @if (measures().duration) {
            <label class="flex flex-col gap-1">
              <span class="text-xs text-muted-foreground">{{ 'WOD.QUICK.TIME_S' | translate }}</span>
              <input type="number" min="0" inputmode="numeric" formControlName="durationSeconds" hlmInput class="h-10 w-24"/>
            </label>
          }
        </div>
      }
    </div>
  `
})
export class ScaledLineComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly group = input.required<ScaledLineForm>();
  readonly options = input.required<UsualNameOption[]>();
  /** The RX line: its movement and variant, prefilled when the line is changed. */
  readonly rxMovementId = input<number | null>(null);
  readonly rxEquipment = input<string[]>([]);
  readonly rxTechniques = input<string[]>([]);
  readonly rxReps = input<string>('');
  readonly measures = input<ScaledMeasures>({load: false, distance: false, calories: false, height: false, duration: false});
  readonly massLabel = input<string>('kg');
  readonly heightLabel = input<string>('cm');
  /** Real sizes of the line's implement (kettlebells, dumbbells, medballs), or null for a free load. */
  readonly loadChoices = input<number[] | null>(null);

  // --- ACTIONS & EVENTS ---
  /** "Change" starts from the RX movement; "As RX" keeps the line RX. */
  toggle(): void {
    const group = this.group();
    if (group.controls.changed.value) {
      group.controls.changed.setValue(false);
      return;
    }
    if (group.controls.movementId.value == null) {
      group.patchValue({movementId: this.rxMovementId(), equipment: [...this.rxEquipment()], techniques: [...this.rxTechniques()]});
    }
    group.controls.changed.setValue(true);
  }

  onPicked(option: UsualNameOption | null): void {
    if (!option) return;
    this.group().patchValue({movementId: option.movementId, equipment: [...option.equipment], techniques: [...option.techniques]});
  }
}
