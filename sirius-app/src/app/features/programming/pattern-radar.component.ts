import {ChangeDetectionStrategy, Component, computed, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {AxisShare, DetailShare, PatternAxis} from '../../api/aldebaran/models/programming.model';
import {AXES, detailsOf, percent, radarPoint, radarScale} from './programming.utils';

const CENTRE = 150;
const RADIUS = 88;

/**
 * The radar of movement patterns (lot D', D174-D179): the full week filled, a profile dashed when
 * compared, axes in alert marked; under it one button per axis opens its details (shares of the
 * full week and of the profile). The drawing itself is a picture.
 */
@Component({
  selector: 'app-pattern-radar',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './pattern-radar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PatternRadarComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly axes = input.required<AxisShare[]>();
  readonly details = input<DetailShare[]>([]);
  /** Draw the profile against the full week. */
  readonly compare = input(false);
  /** Axes of the profile in alert (pattern gap). */
  readonly alerted = input<string[]>([]);
  readonly lang = input('en');
  /** Axis whose details are open, or null. */
  readonly selected = input<PatternAxis | null>(null);
  readonly selectedChange = output<PatternAxis | null>();

  protected readonly centre = CENTRE;

  // --- COMPUTED STATE ---
  private readonly ordered = computed(() => AXES.map(axis =>
    this.axes().find(a => a.axis === axis) ?? {axis, share: 0, fullShare: 0, judged: false}));

  private readonly scale = computed(() =>
    radarScale(this.ordered().flatMap(a => this.compare() ? [a.share, a.fullShare] : [a.fullShare])));

  readonly rings = computed(() => {
    const max = this.scale();
    return [10, 20, 30, 40, 50, 60].filter(k => k <= max).map(k => ({
      label: `${k} %`,
      points: this.polygon(AXES.map(() => k)),
      y: radarPoint(0, AXES.length, k, max, CENTRE, RADIUS)[1]
    }));
  });

  readonly spokes = computed(() => AXES.map((axis, i) => {
    const [x, y] = radarPoint(i, AXES.length, this.scale(), this.scale(), CENTRE, RADIUS);
    const [lx, ly] = radarPoint(i, AXES.length, this.scale() * 1.3, this.scale() * 1.3, CENTRE, RADIUS * 1.3);
    const a = this.ordered()[i];
    return {
      axis, x, y, lx, ly,
      anchor: Math.abs(lx - CENTRE) < 4 ? 'middle' : lx > CENTRE ? 'start' : 'end',
      alert: this.alerted().includes(axis),
      value: this.compare() ? `${percent(a.fullShare, this.lang())} · ${percent(a.share, this.lang())}` : percent(a.fullShare, this.lang())
    };
  }));

  readonly fullPolygon = computed(() => this.polygon(this.ordered().map(a => a.fullShare)));
  readonly profilePolygon = computed(() => this.compare() ? this.polygon(this.ordered().map(a => a.share)) : null);
  readonly alertDots = computed(() => this.ordered()
    .map((a, i) => ({a, i}))
    .filter(({a}) => this.compare() && this.alerted().includes(a.axis))
    .map(({a, i}) => radarPoint(i, AXES.length, a.share, this.scale(), CENTRE, RADIUS)));

  /** The open axis: its shares and details. */
  readonly panel = computed(() => {
    const axis = this.selected();
    if (!axis) return null;
    const share = this.ordered().find(a => a.axis === axis)!;
    const rows = detailsOf(axis).map(pattern => this.details().find(d => d.pattern === pattern) ?? {pattern, share: 0, fullShare: 0});
    const max = Math.max(1, ...rows.flatMap(r => [r.fullShare, this.compare() ? r.share : 0]));
    return {
      axis, share,
      rows: rows.map(r => ({
        ...r, fullWidth: r.fullShare / max * 100, width: r.share / max * 100,
        fullText: percent(r.fullShare, this.lang()), text: percent(r.share, this.lang())
      })),
      fullText: percent(share.fullShare, this.lang()),
      text: percent(share.share, this.lang())
    };
  });

  // --- ACTIONS & EVENTS ---
  toggle(axis: PatternAxis): void {
    this.selectedChange.emit(this.selected() === axis ? null : axis);
  }

  // --- HELPERS ---
  private polygon(values: number[]): string {
    return values.map((v, i) => radarPoint(i, values.length, v, this.scale(), CENTRE, RADIUS).join(',')).join(' ');
  }
}
