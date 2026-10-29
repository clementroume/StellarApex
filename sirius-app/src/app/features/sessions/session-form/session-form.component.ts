import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {CdkTrapFocus} from '@angular/cdk/a11y';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {
  BlockKind,
  Discipline,
  SessionBlockRequest,
  SessionRequest,
  SessionResponse,
  SessionType,
  TIMED_POLICIES,
  VisibilityPolicy
} from '../../../api/aldebaran/models/session.model';
import {WodRequest, WodResponse, WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {StaffGymPickerComponent, staffGymSelection} from '../../box/staff-gym';
import {WodQuickFormComponent} from '../../wods/wod-quick-form/wod-quick-form.component';
import {addDays, isTextKind, localDate, mondayOf, shortDay, toLocalInput} from '../session.utils';

const DISCIPLINES: Discipline[] = ['CROSSFIT', 'WEIGHTLIFTING', 'STRENGTH', 'GYMNASTICS', 'ENDURANCE', 'HYROX',
  'MOBILITY', 'BODYBUILDING', 'STRONGMAN', 'OTHER'];
const ALL_POLICIES: VisibilityPolicy[] = ['ADVANCE', 'DAY_OF', 'AFTER', 'AFTER_PARTICIPANTS', 'PARTICIPANTS_ONLY'];
/** Blocks offered on top of a session (Skill and WOD are there from the start). */
const EXTRA_KINDS: BlockKind[] = ['WARM_UP', 'SKILL', 'WOD', 'ACCESSORY', 'FINISHER', 'COOL_DOWN'];
/** Official competition WODs: programmed as competition blocks by default. */
const COMPETITION_CATEGORIES = ['OPEN', 'QUARTERFINALS', 'SEMIFINALS', 'GAMES'];
/** Blocks offered on top of the session: only they can be marked optional. */
const OPTIONAL_KINDS: BlockKind[] = ['ACCESSORY', 'FINISHER'];

/** WOD of a block: created for the session (new or kept), or borrowed from the standalone WODs. */
export type BlockWod =
  | {mode: 'own'; title: string; wodId: number | null; response: WodResponse | null; request: WodRequest | null}
  | {mode: 'borrowed'; title: string; wodId: number};

type BlockForm = FormGroup<{
  id: FormControl<number | null>;
  kind: FormControl<BlockKind>;
  themeFr: FormControl<string>;
  themeEn: FormControl<string>;
  optional: FormControl<boolean>;
  textFr: FormControl<string>;
  textEn: FormControl<string>;
  competition: FormControl<boolean>;
  wod: FormControl<BlockWod | null>;
}>;

/**
 * Staff session form (My box › Sessions) and personal session form (My training): type and day
 * (a specialty takes its week), visibility policy, then the blocks (Skill and WOD first, warm-up,
 * accessory, finisher, cool down on demand), each with its theme and its WOD written inline
 * (quick WOD editor) or borrowed. Aldebaran checks the composition, the rights and the policy.
 * Mobile first: blocks stack, reordered with up/down buttons.
 */
@Component({
  selector: 'app-session-form',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, TranslatePipe, NgIcon, HlmButton, HlmInput, HlmTextarea, HlmSpinner,
    CdkTrapFocus, PageComponent, NativeSelectDirective, StaffGymPickerComponent, WodQuickFormComponent],
  templateUrl: './session-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionFormComponent {
  // --- INJECTIONS ---
  private readonly sessionService = inject(SessionService);
  private readonly wodService = inject(WodService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly staffGym = staffGymSelection('WOD_WRITE');

  // --- STATE (SIGNALS & VARIABLES) ---
  /** Session to edit (route parameter), absent when creating. */
  readonly id = input<string>();
  /** Personal session (route data): no gym, no visibility policy. */
  readonly personal = input(false);
  protected readonly disciplines = DISCIPLINES;
  /** Title and coach's word, folded until asked for (or filled). */
  protected readonly showMore = signal(false);
  /** Visibility choice, folded into one line until asked for. */
  protected readonly showVisibility = signal(false);
  /** Default policy of the gym (its session settings), shown on the folded line. */
  private readonly defaultPolicy = signal<VisibilityPolicy | null>(null);
  protected readonly types: SessionType[] = ['WOD', 'SPECIALTY'];
  protected readonly original = signal<SessionResponse | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly isSaving = signal(false);
  /** Block whose WOD is being written (embedded WOD form), or null. */
  protected readonly editingWod = signal<number | null>(null);
  /** Block borrowing a WOD (search dialog), or null. */
  protected readonly borrowing = signal<number | null>(null);
  protected readonly borrowResults = signal<WodSummaryResponse[]>([]);
  private borrowTimer: ReturnType<typeof setTimeout> | null = null;

  readonly form = new FormGroup({
    type: new FormControl<SessionType>('WOD', {nonNullable: true}),
    date: new FormControl<string>(localDate(), {nonNullable: true, validators: Validators.required}),
    discipline: new FormControl<Discipline>('CROSSFIT', {nonNullable: true}),
    title: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(100)}),
    descriptionFr: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(4000)}),
    descriptionEn: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(4000)}),
    // '' = the gym's default policy
    visibilityPolicy: new FormControl<VisibilityPolicy | ''>('', {nonNullable: true}),
    contentVisibleAt: new FormControl<string>('', {nonNullable: true}),
    blocks: new FormArray<BlockForm>([])
  });
  protected readonly value = toSignal(this.form.valueChanges, {initialValue: this.form.getRawValue()});

  // --- COMPUTED STATE ---
  protected readonly isEdit = computed(() => !!this.id());
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  /** The user's language first: a coach writes in their language, the other one is optional. */
  protected readonly primaryFr = computed(() => this.lang() === 'fr');
  /** Gym of the session: the edited one's, none for a personal session, else the managed gym. */
  protected readonly gymId = computed(() => {
    if (this.personal()) return null;
    const original = this.original();
    return original ? original.gymId ?? null : this.staffGym.gymId();
  });
  private readonly gym = computed(() => this.accessService.memberships().find(m => m.gymId === this.gymId()) ?? null);
  /** Competitions exist in physical boxes only. */
  protected readonly canCompete = computed(() => this.gymId() != null && !this.gym()?.gymProgramming);
  /** A program only shows its sessions in advance or on the day. */
  protected readonly policies = computed(() => this.gym()?.gymProgramming ? TIMED_POLICIES : ALL_POLICIES);
  protected readonly isSpecialty = computed(() => this.value().type === 'SPECIALTY');
  protected readonly weekHint = computed(() => {
    const date = this.value().date;
    if (!this.isSpecialty() || !date) return '';
    const monday = mondayOf(date);
    return this.translate.instant('SESSION.WEEK_OF', {
      from: shortDay(monday, this.lang()), to: shortDay(addDays(monday, 6), this.lang())
    });
  });
  protected readonly needsInstant = computed(() => {
    const policy = this.value().visibilityPolicy;
    return policy !== '' && TIMED_POLICIES.includes(policy as VisibilityPolicy);
  });
  /** Policy the session gets: the chosen one, else the gym's default. */
  protected readonly effectivePolicy = computed(() => this.value().visibilityPolicy || this.defaultPolicy());
  protected readonly defaultPolicyLabel = computed(() => {
    const policy = this.defaultPolicy();
    return policy ? this.translate.instant('SESSION.POLICIES.' + policy + '.LABEL') : '';
  });
  protected readonly visibilityLabel = computed(() => {
    const policy = this.effectivePolicy();
    return policy ? this.translate.instant('SESSION.POLICIES.' + policy + '.LABEL') : '';
  });
  /** Warm-up and cool down exist once (first and last). */
  protected readonly addableKinds = computed(() => {
    const kinds = this.value().blocks?.map(b => b.kind) ?? [];
    return EXTRA_KINDS.filter(k => !isTextKind(k) || !kinds.includes(k));
  });
  protected readonly blockControls = computed(() => {
    this.value();
    return this.form.controls.blocks.controls;
  });
  protected readonly editingKind = computed<BlockKind>(() => {
    const index = this.editingWod();
    return index == null ? 'WOD' : this.form.controls.blocks.at(index)?.controls.kind.value ?? 'WOD';
  });
  /** Format of a new WOD by block: strength work for a Skill block, accessory work, else For Time. */
  protected readonly defaultFormat = computed(() =>
    ({SKILL: 'STRENGTH', ACCESSORY: 'ACCESSORY'} as Record<string, string>)[this.editingKind()] ?? 'FOR_TIME');
  /** Quick WOD editor input: the block's WOD as last written, or its saved WOD. */
  protected readonly wodToEdit = computed<WodResponse | null>(() => {
    const index = this.editingWod();
    const wod = index == null ? null : this.form.controls.blocks.at(index)?.controls.wod.value;
    if (!wod || wod.mode !== 'own') return null;
    return wod.request ? requestAsResponse(wod.request) : wod.response;
  });

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => id ? this.load(id) : this.prepareNew());
    });
    this.form.controls.type.valueChanges.subscribe(type => {
      if (type === 'WOD') this.form.controls.discipline.setValue('CROSSFIT');
      else if (this.form.controls.discipline.value === 'CROSSFIT') this.form.controls.discipline.setValue('WEIGHTLIFTING');
    });
    effect(() => {
      const gymId = this.gymId();
      untracked(() => this.loadDefaultPolicy(gymId));
    });
    this.destroyRef.onDestroy(() => this.borrowTimer && clearTimeout(this.borrowTimer));
  }

  // --- DATA FETCHING ---
  private prepareNew(): void {
    const date = this.route.snapshot.queryParamMap.get('date');
    if (date) this.form.controls.date.setValue(date);
    this.addBlock('SKILL');
    this.addBlock('WOD');
    const borrow = Number(this.route.snapshot.queryParamMap.get('borrow'));
    if (borrow) this.prefillBorrowed(borrow);
  }

  /** "Program in a session" from a WOD: the WOD block borrows it (competition for an official one). */
  private prefillBorrowed(wodId: number): void {
    this.wodService.getWod(wodId).subscribe({
      next: wod => {
        const block = this.form.controls.blocks.controls.find(b => b.controls.kind.value === 'WOD');
        block?.patchValue({
          wod: {mode: 'borrowed', title: wod.title, wodId: wod.id},
          competition: COMPETITION_CATEGORIES.includes(wod.category ?? '')
        });
      },
      error: () => this.notificationService.showError(this.translate.instant('WOD.MESSAGES.NOT_FOUND'))
    });
  }

  private loadDefaultPolicy(gymId: number | null): void {
    this.defaultPolicy.set(null);
    if (gymId == null) return;
    this.sessionService.getSettings(gymId).subscribe({
      next: settings => this.defaultPolicy.set(settings.defaultVisibility),
      error: () => this.defaultPolicy.set(null)
    });
  }

  private load(id: number): void {
    this.isLoading.set(true);
    this.sessionService.getSession(id).subscribe({
      next: s => {
        if (!s.capabilities.canModify || s.capabilities.locked) {
          this.notificationService.showError(this.translate.instant(
            s.capabilities.locked ? 'SESSION.MESSAGES.LOCKED' : 'SESSION.MESSAGES.FORBIDDEN'));
          void this.router.navigate([s.gymId == null ? '/training/sessions' : '/box/sessions', s.id]);
          return;
        }
        this.original.set(s);
        this.form.patchValue({
          type: s.type,
          date: s.date,
          discipline: s.discipline,
          title: s.title ?? '',
          descriptionFr: s.descriptionFr ?? '',
          descriptionEn: s.descriptionEn ?? '',
          visibilityPolicy: s.visibilityPolicy ?? '',
          contentVisibleAt: toLocalInput(s.contentVisibleFrom) ?? ''
        }, {emitEvent: false});
        this.form.controls.discipline.setValue(s.discipline);
        this.showMore.set(!!(s.title || s.descriptionFr || s.descriptionEn));
        this.form.controls.blocks.clear();
        for (const b of s.blocks) {
          const wod: BlockWod | null = !b.wod ? null : b.borrowed
            ? {mode: 'borrowed', title: b.wod.title, wodId: b.wod.id}
            : {mode: 'own', title: b.wod.title, wodId: b.wod.id, response: b.wod, request: null};
          this.form.controls.blocks.push(this.blockGroup(b.kind, {
            id: b.id, themeFr: b.themeFr ?? '', themeEn: b.themeEn ?? '', optional: b.optional, textFr: b.textFr ?? '',
            textEn: b.textEn ?? '', competition: b.competition, wod
          }));
        }
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.NOT_FOUND'));
        void this.router.navigate([this.personal() ? '/training/journal' : '/box/sessions']);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  /** Warm-up goes first, cool down last, any other block before the cool down. */
  protected addBlock(kind: BlockKind): void {
    const blocks = this.form.controls.blocks;
    const group = this.blockGroup(kind, {optional: OPTIONAL_KINDS.includes(kind)});
    if (kind === 'WARM_UP') {
      blocks.insert(0, group);
    } else if (kind !== 'COOL_DOWN' && this.indexOfKind('COOL_DOWN') >= 0) {
      blocks.insert(this.indexOfKind('COOL_DOWN'), group);
    } else {
      blocks.push(group);
    }
  }

  protected removeBlock(index: number): void {
    this.form.controls.blocks.removeAt(index);
  }

  /** Warm-up and cool down stay in place; the other blocks move between them. */
  protected canMove(index: number, delta: -1 | 1): boolean {
    const blocks = this.form.controls.blocks;
    const target = index + delta;
    const min = this.indexOfKind('WARM_UP') === 0 ? 1 : 0;
    const max = this.indexOfKind('COOL_DOWN') === blocks.length - 1 ? blocks.length - 2 : blocks.length - 1;
    return !isTextKind(blocks.at(index).controls.kind.value) && target >= min && target <= max;
  }

  /** A WOD session keeps at least one Skill and one WOD block (Aldebaran checks it too). */
  protected canRemove(index: number): boolean {
    const kind = this.form.controls.blocks.at(index).controls.kind.value;
    if (this.value().type !== 'WOD' || (kind !== 'SKILL' && kind !== 'WOD')) return true;
    return this.form.controls.blocks.controls.filter(b => b.controls.kind.value === kind).length > 1;
  }

  protected moveBlock(index: number, delta: -1 | 1): void {
    const blocks = this.form.controls.blocks;
    const target = index + delta;
    if (!this.canMove(index, delta)) return;
    const control = blocks.at(index);
    blocks.removeAt(index);
    blocks.insert(target, control);
  }

  protected setOwnWod(index: number, request: WodRequest): void {
    const control = this.form.controls.blocks.at(index).controls.wod;
    const current = control.value;
    control.setValue({
      mode: 'own',
      title: request.title,
      // Keep the block's own saved WOD: Aldebaran updates it in place
      wodId: current?.mode === 'own' ? current.wodId : null,
      response: current?.mode === 'own' ? current.response : null,
      request
    });
    this.editingWod.set(null);
  }

  /** Escape closes the WOD editor, unless it closes an open movement list first. */
  protected closeWodEditor(event: Event): void {
    if ((event.target as HTMLElement | null)?.closest('[role="combobox"], hlm-combobox')) return;
    this.editingWod.set(null);
  }

  protected openBorrow(index: number): void {
    this.borrowing.set(index);
    this.searchWods('');
  }

  protected onBorrowSearch(event: Event): void {
    const search = (event.target as HTMLInputElement).value;
    if (this.borrowTimer) clearTimeout(this.borrowTimer);
    this.borrowTimer = setTimeout(() => this.searchWods(search), 300);
  }

  protected borrow(wod: WodSummaryResponse): void {
    const index = this.borrowing();
    if (index == null) return;
    this.form.controls.blocks.at(index).controls.wod.setValue({mode: 'borrowed', title: wod.title, wodId: wod.id});
    this.borrowing.set(null);
  }

  protected clearWod(index: number): void {
    this.form.controls.blocks.at(index).controls.wod.setValue(null);
  }

  protected onSubmit(): void {
    if (this.form.invalid || this.isSaving()) {
      this.form.markAllAsTouched();
      return;
    }
    const missing = this.form.controls.blocks.controls.findIndex(b => !isTextKind(b.controls.kind.value) && !b.controls.wod.value);
    if (missing >= 0) {
      this.notificationService.showError(this.translate.instant('SESSION.MESSAGES.WOD_MISSING', {n: missing + 1}));
      return;
    }
    const request = this.buildRequest();
    const original = this.original();
    this.isSaving.set(true);
    const call = original ? this.sessionService.updateSession(original.id, request) : this.sessionService.createSession(request);
    call.subscribe({
      next: saved => {
        this.isSaving.set(false);
        this.notificationService.showSuccess(this.translate.instant(original ? 'SESSION.MESSAGES.UPDATED' : 'SESSION.MESSAGES.CREATED'));
        this.sessionService.notifyRefresh();
        void this.router.navigate([saved.gymId == null ? '/training/sessions' : '/box/sessions', saved.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving.set(false);
        const fieldError: string | undefined = err.error?.['errors']?.[0]?.message;
        this.notificationService.showError(fieldError ?? problemDetailOf(err) ?? this.translate.instant(
          err.status === 403 ? 'SESSION.MESSAGES.FORBIDDEN' : 'SESSION.MESSAGES.SAVE_ERROR'));
      }
    });
  }

  // --- HELPERS ---
  private indexOfKind(kind: BlockKind): number {
    return this.form.controls.blocks.controls.findIndex(b => b.controls.kind.value === kind);
  }

  isText(kind: BlockKind): boolean {
    return isTextKind(kind);
  }

  buildRequest(): SessionRequest {
    const v = this.form.getRawValue();
    const personal = this.gymId() == null;
    const policy = personal || v.visibilityPolicy === '' ? null : v.visibilityPolicy as VisibilityPolicy;
    return {
      type: v.type,
      discipline: v.type === 'WOD' ? 'CROSSFIT' : v.discipline,
      date: v.date,
      title: v.title.trim() || null,
      descriptionFr: v.descriptionFr.trim() || null,
      descriptionEn: v.descriptionEn.trim() || null,
      gymId: this.gymId(),
      visibilityPolicy: policy,
      contentVisibleAt: policy && TIMED_POLICIES.includes(policy) && v.contentVisibleAt ? v.contentVisibleAt : null,
      blocks: v.blocks.map(b => this.blockRequest(b))
    };
  }

  private blockRequest(b: ReturnType<BlockForm['getRawValue']>): SessionBlockRequest {
    const text = isTextKind(b.kind);
    const wod = text ? null : b.wod;
    return {
      id: b.id,
      kind: b.kind,
      // Letters (A, B...) come from the order; Aldebaran derives the stimulus
      label: null,
      themeFr: text ? null : b.themeFr.trim() || null,
      themeEn: text ? null : b.themeEn.trim() || null,
      optional: OPTIONAL_KINDS.includes(b.kind) && b.optional,
      stimulus: null,
      plannedMinutes: null,
      textFr: text ? b.textFr.trim() || null : null,
      textEn: text ? b.textEn.trim() || null : null,
      competition: b.kind === 'WOD' && this.canCompete() && b.competition,
      wodId: wod?.mode === 'borrowed' ? wod.wodId : wod?.mode === 'own' && !wod.request ? wod.wodId : null,
      wod: wod?.mode === 'own' && wod.request ? wod.request : null
    };
  }

  private blockGroup(kind: BlockKind, v: Partial<ReturnType<BlockForm['getRawValue']>> = {}): BlockForm {
    return new FormGroup({
      id: new FormControl<number | null>(v.id ?? null),
      kind: new FormControl<BlockKind>(kind, {nonNullable: true}),
      themeFr: new FormControl<string>(v.themeFr ?? '', {nonNullable: true, validators: Validators.maxLength(80)}),
      themeEn: new FormControl<string>(v.themeEn ?? '', {nonNullable: true, validators: Validators.maxLength(80)}),
      optional: new FormControl<boolean>(v.optional ?? false, {nonNullable: true}),
      textFr: new FormControl<string>(v.textFr ?? '', {nonNullable: true, validators: Validators.maxLength(4000)}),
      textEn: new FormControl<string>(v.textEn ?? '', {nonNullable: true, validators: Validators.maxLength(4000)}),
      competition: new FormControl<boolean>(v.competition ?? false, {nonNullable: true}),
      wod: new FormControl<BlockWod | null>(v.wod ?? null)
    });
  }

  private searchWods(search: string): void {
    this.wodService.getWods({search: search.trim() || undefined, scope: 'ALL'}, 0, 20).subscribe({
      next: slice => this.borrowResults.set(slice.content.filter(w => !w.competition)),
      error: () => this.borrowResults.set([])
    });
  }
}

/** A WOD written in the form, shaped as a response to fill the embedded WOD form again. */
function requestAsResponse(request: WodRequest): WodResponse {
  return {
    ...request,
    id: 0,
    modalities: [],
    createdAt: '',
    updatedAt: '',
    movements: request.movements.map(m => ({...m, id: 0, movement: {id: m.movementId, name: ''} as WodResponse['movements'][number]['movement']}))
  };
}
