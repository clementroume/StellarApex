import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {AGE_GROUPS, AgeGroup, BoardRecord, CanonicalLift} from '../../api/aldebaran/models/record.model';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {Division, DIVISIONS} from '../../api/antares/models/user.model';
import {UserService} from '../../api/antares/services/user.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {UnitService} from '../../core/services/unit.service';
import {NativeSelectDirective} from '../../shared/component/native-select/native-select.directive';
import {PageComponent} from '../../shared/component/page/page.component';

/** A movement offered on the board, with the N RM that have a canonical test. */
interface BoardLift {
  movementId: number;
  movementName: string;
  reps: number[];
}

/**
 * Records board of a box (D56): the best base-version load of each current member for a lift and a
 * number of reps, wherever it was set, filtered by division and age group. Aldebaran lists the members
 * (Antares) and refuses non-members; names come from Antares display names.
 */
@Component({
  selector: 'app-box-records',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmButton, HlmCardImports, HlmSpinner, NativeSelectDirective, PageComponent],
  templateUrl: './box-records.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoxRecordsComponent {
  // --- INJECTIONS ---
  private readonly recordService = inject(RecordService);
  private readonly userService = inject(UserService);
  private readonly accessService = inject(AccessService);
  private readonly displayGym = inject(DisplayGymService);
  protected readonly unitService = inject(UnitService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly divisions = DIVISIONS;
  readonly ageGroups = AGE_GROUPS;
  readonly lifts = signal<CanonicalLift[]>([]);
  /** Box picked on this page when the navbar shows every box/prog. */
  readonly chosenGymId = signal<number | null>(null);
  readonly movementId = signal<number | null>(null);
  readonly reps = signal<number>(1);
  readonly division = signal<Division | null>(null);
  readonly ageGroup = signal<AgeGroup | null>(null);
  readonly board = signal<BoardRecord[]>([]);
  readonly names = signal<Record<number, string>>({});
  readonly isLoading = signal<boolean>(false);
  readonly failed = signal<boolean>(false);

  // --- COMPUTED STATE ---
  readonly gyms = computed(() => this.accessService.memberships());
  readonly gymId = computed(() => this.displayGym.gymId() ?? this.chosenGymId() ?? this.gyms()[0]?.gymId ?? null);
  readonly myId = computed(() => this.accessService.userId());
  /** The navbar already picks one box/prog: no picker here. */
  readonly displayGymChosen = computed(() => this.displayGym.gymId() !== null);
  /** Movements with canonical tests, by name, each with its N RM. */
  readonly boardLifts = computed<BoardLift[]>(() => {
    const byMovement = new Map<number, BoardLift>();
    for (const lift of this.lifts()) {
      const row = byMovement.get(lift.movementId) ?? {movementId: lift.movementId, movementName: lift.movementName, reps: []};
      row.reps.push(lift.repMax);
      byMovement.set(lift.movementId, row);
    }
    return [...byMovement.values()];
  });
  readonly selectedLift = computed(() => this.boardLifts().find(l => l.movementId === this.movementId()) ?? null);
  /** N RM offered: the canonical ones, plus any N a member holds a record for. */
  readonly repsOptions = computed(() =>
    [...new Set([...(this.selectedLift()?.reps ?? []), ...this.board().map(r => r.reps)])].sort((a, b) => a - b));
  readonly rows = computed(() => this.board().filter(r =>
    r.reps === this.reps()
    && (this.division() === null || r.division === this.division())
    && (this.ageGroup() === null || r.ageGroup === this.ageGroup())));

  // --- LIFECYCLE ---
  constructor() {
    this.recordService.getCanonicalLifts(new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: lifts => {
        this.lifts.set(lifts);
        const first = this.boardLifts().find(l => l.movementName === 'Back Squat') ?? this.boardLifts()[0];
        if (first && this.movementId() === null) this.movementId.set(first.movementId);
      },
      error: () => this.failed.set(true)
    });
    effect(() => {
      const gymId = this.gymId();
      const movementId = this.movementId();
      if (gymId !== null && movementId !== null) untracked(() => this.fetchBoard(gymId, movementId));
    });
  }

  // --- DATA FETCHING ---
  private fetchBoard(gymId: number, movementId: number): void {
    this.isLoading.set(true);
    this.recordService.getGymBoard(gymId, movementId, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: board => {
        this.board.set(board);
        this.failed.set(false);
        this.isLoading.set(false);
        this.loadNames(board);
      },
      error: () => {
        this.board.set([]);
        this.failed.set(true);
        this.isLoading.set(false);
      }
    });
  }

  private loadNames(board: BoardRecord[]): void {
    const known = this.names();
    const missing = [...new Set(board.map(r => r.userId))].filter(id => !(id in known));
    if (missing.length === 0) return;
    this.userService.getDisplayNames(missing).subscribe({
      next: names => this.names.set({...this.names(), ...Object.fromEntries(names.map(n => [n.id, n.displayName]))}),
      error: () => undefined
    });
  }

  // --- ACTIONS & EVENTS ---
  onGymChange(value: string): void {
    this.chosenGymId.set(Number(value));
  }

  onMovementChange(value: string): void {
    this.movementId.set(Number(value));
    this.reps.set(1);
  }

  onRepsChange(reps: number): void {
    this.reps.set(reps);
  }

  onDivisionChange(division: Division | null): void {
    this.division.set(division);
  }

  onAgeGroupChange(value: string): void {
    this.ageGroup.set(value ? value as AgeGroup : null);
  }

  // --- HELPERS ---
  loadText(r: BoardRecord): string {
    return this.unitService.formatScoreLoad(r.weightKg, 'KG');
  }

  name(r: BoardRecord): string | null {
    return this.names()[r.userId] ?? null;
  }

  /** The canonical test of the selected lift and N, to log one's own. */
  testWodId(): number | null {
    return this.lifts().find(l => l.movementId === this.movementId() && l.repMax === this.reps())?.wodId ?? null;
  }
}
