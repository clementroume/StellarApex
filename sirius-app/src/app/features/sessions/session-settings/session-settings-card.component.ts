import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInput} from '@spartan-ng/helm/input';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {TIMED_POLICIES, VisibilityPolicy} from '../../../api/aldebaran/models/session.model';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';

const ALL_POLICIES: VisibilityPolicy[] = ['ADVANCE', 'DAY_OF', 'AFTER', 'AFTER_PARTICIPANTS', 'PARTICIPANTS_ONLY'];

/**
 * Session settings of a box/prog (My box › Settings, MANAGE_SETTINGS): default visibility policy of
 * new sessions (a program: in advance or on the day only) and the time zone placing its days.
 */
@Component({
  selector: 'app-session-settings-card',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, HlmButton, HlmCardImports, HlmInput, NativeSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form hlmCard id="session-settings" [formGroup]="form" (ngSubmit)="save()">
      <div hlmCardHeader>
        <h2 hlmCardTitle class="text-lg">{{ 'SESSION.SETTINGS.TITLE' | translate }}</h2>
        <p hlmCardDescription>{{ 'SESSION.SETTINGS.DESC' | translate }}</p>
      </div>
      <div hlmCardContent class="flex flex-col gap-4">
        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">{{ 'SESSION.SETTINGS.DEFAULT' | translate }}</span>
          <select appNativeSelect formControlName="defaultVisibility">
            @for (p of policies(); track p) {
              <option [value]="p">{{ 'SESSION.POLICIES.' + p + '.LABEL' | translate }}</option>
            }
          </select>
          <span class="text-xs text-muted-foreground">{{ 'SESSION.POLICIES.' + form.controls.defaultVisibility.value + '.HINT' | translate }}</span>
        </label>
        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">{{ 'SESSION.SETTINGS.TIME_ZONE' | translate }}</span>
          <input hlmInput class="h-11 md:h-10" formControlName="timeZone" list="session-time-zones" autocomplete="off"/>
          <datalist id="session-time-zones">
            @for (zone of zones; track zone) {
              <option [value]="zone"></option>
            }
          </datalist>
          <span class="text-xs text-muted-foreground">{{ 'SESSION.SETTINGS.TIME_ZONE_HINT' | translate }}</span>
        </label>
        <button type="submit" hlmBtn class="h-11 md:h-10 sm:self-end" [disabled]="isSaving() || form.pristine || form.invalid">
          {{ 'GLOBAL.SAVE' | translate }}
        </button>
      </div>
    </form>
  `
})
export class SessionSettingsCardComponent {
  // --- INJECTIONS ---
  private readonly sessionService = inject(SessionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly gymId = input.required<number>();
  readonly programming = input(false);
  protected readonly isSaving = signal(false);
  protected readonly zones = supportedZones();
  readonly form = new FormGroup({
    defaultVisibility: new FormControl<VisibilityPolicy>('AFTER_PARTICIPANTS', {nonNullable: true}),
    timeZone: new FormControl<string>('Europe/Paris', {nonNullable: true, validators: [Validators.required, Validators.maxLength(50)]})
  });

  // --- COMPUTED STATE ---
  protected readonly policies = computed(() => this.programming() ? TIMED_POLICIES : ALL_POLICIES);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const gymId = this.gymId();
      untracked(() => this.load(gymId));
    });
  }

  // --- DATA FETCHING ---
  private load(gymId: number): void {
    this.sessionService.getSettings(gymId).subscribe({
      next: settings => this.form.reset(settings),
      error: () => this.form.reset()
    });
  }

  // --- ACTIONS & EVENTS ---
  protected save(): void {
    if (this.form.invalid || this.isSaving()) return;
    this.isSaving.set(true);
    this.sessionService.updateSettings(this.gymId(), this.form.getRawValue()).subscribe({
      next: settings => {
        this.isSaving.set(false);
        this.form.reset(settings);
        this.notificationService.showSuccess(this.translate.instant('SESSION.SETTINGS.SAVED'));
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'));
      }
    });
  }
}

/** IANA time zones known to the browser (Europe first), for the suggestions. */
function supportedZones(): string[] {
  try {
    const zones = (Intl as unknown as {supportedValuesOf?: (key: string) => string[]}).supportedValuesOf?.('timeZone') ?? [];
    return [...zones.filter(z => z.startsWith('Europe/')), ...zones.filter(z => !z.startsWith('Europe/'))];
  } catch {
    return ['Europe/Paris'];
  }
}
