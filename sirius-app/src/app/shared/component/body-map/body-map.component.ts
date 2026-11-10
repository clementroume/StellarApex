import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {BODY_BACK, BODY_BACK_VIEW_BOX, BODY_FRONT, BODY_FRONT_VIEW_BOX} from './body-map.data';

/** Region (`Muscle.region`) of each part of the drawing; the others are not muscles of the catalog. */
const REGION_OF_PART: Record<string, string> = {
  chest: 'THORAX', abs: 'ABDOMEN_PELVIS', obliques: 'ABDOMEN_PELVIS', 'lower-back': 'BACK', trapezius: 'BACK', 'upper-back': 'BACK',
  deltoids: 'SHOULDER', biceps: 'ARM_FOREARM', triceps: 'ARM_FOREARM', forearm: 'ARM_FOREARM', quadriceps: 'THIGH_LEG', adductors: 'THIGH_LEG',
  hamstring: 'THIGH_LEG', gluteal: 'HIP', calves: 'THIGH_LEG', tibialis: 'THIGH_LEG'
};

/**
 * Front and back of a body, each region clickable. A shortcut for the pointer: the page keeps
 * buttons for the regions, so the drawing is hidden from assistive technologies.
 */
@Component({
  selector: 'app-body-map',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid grid-cols-2 gap-2" data-slot="body-map">
      @for (view of views; track view.label) {
        <figure class="m-0 flex flex-col items-center gap-1">
          <svg [attr.viewBox]="view.viewBox" class="h-auto w-full max-h-96" aria-hidden="true">
            @for (part of view.parts; track part.slug) {
              @for (d of part.paths; track $index) {
                <path [attr.d]="d" [class]="classOf(part.slug)" (click)="pick(part.slug)"/>
              }
            }
          </svg>
          <figcaption class="text-xs text-muted-foreground">{{ view.label | translate }}</figcaption>
        </figure>
      }
    </div>
  `,
  styles: `
    .other { fill: var(--muted); }
    .region { fill: color-mix(in srgb, var(--muted-foreground) 35%, transparent); cursor: pointer; transition: fill 120ms; }
    .region:hover { fill: color-mix(in srgb, var(--sa-secondary) 55%, transparent); }
    .region.on { fill: var(--sa-secondary); }
  `
})
export class BodyMapComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  /** The region shown. */
  readonly selected = input<string | null>(null);
  readonly regionPicked = output<string>();

  protected readonly views = [
    {label: 'MUSCLE.LIST.FRONT', viewBox: BODY_FRONT_VIEW_BOX, parts: BODY_FRONT},
    {label: 'MUSCLE.LIST.BACK', viewBox: BODY_BACK_VIEW_BOX, parts: BODY_BACK}
  ];

  // --- ACTIONS & EVENTS ---
  protected pick(part: string): void {
    const region = REGION_OF_PART[part];
    if (region) this.regionPicked.emit(region);
  }

  // --- HELPERS ---
  protected classOf(part: string): string {
    const region = REGION_OF_PART[part];
    if (!region) return 'other';
    return region === this.selected() ? 'region on' : 'region';
  }
}
