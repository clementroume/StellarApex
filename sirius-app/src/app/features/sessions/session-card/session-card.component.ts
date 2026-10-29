import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {SessionSummaryResponse} from '../../../api/aldebaran/models/session.model';
import {AccessService} from '../../../core/services/access.service';
import {blockTheme, sessionDateLabel, sessionLink} from '../session.utils';

/**
 * Frame of a session as a link to it (week planning, Today page, journal): type or title, day or week,
 * the blocks with their themes, and the user's state (done, content still hidden). Actions such as
 * "Séance faite" are projected (`[cardActions]`).
 */
@Component({
  selector: 'app-session-card',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="flex flex-col gap-3 rounded-xl border bg-card p-4">
      <a [routerLink]="link()" class="flex flex-col gap-2 group">
        <div class="flex flex-wrap items-start justify-between gap-2">
          <div class="min-w-0">
            <h3 class="font-semibold group-hover:text-primary transition-colors">{{ heading() }}</h3>
            <p class="text-sm text-muted-foreground">
              @if (showDate()) {
                {{ dateLabel() }} ·
              }
              {{ 'SESSION.DISCIPLINES.' + session().discipline | translate }}
              @if (gymName(); as name) {
                · {{ name }}
              }
            </p>
          </div>
          <div class="flex flex-wrap gap-1">
            @if (session().loggedByMe) {
              <span hlmBadge class="bg-success/15 text-success gap-1">
                <ng-icon name="heroCheckCircle" aria-hidden="true"/>{{ 'SESSION.DONE' | translate }}
              </span>
            }
            @if (!session().contentVisible) {
              <span hlmBadge variant="outline" class="gap-1" [title]="'SESSION.HIDDEN_HINT' | translate">
                <ng-icon name="heroLockClosed" aria-hidden="true"/>{{ 'SESSION.HIDDEN' | translate }}
              </span>
            }
          </div>
        </div>
        <ul class="flex flex-col gap-1 text-sm">
          @for (block of blocks(); track $index) {
            <li class="flex gap-2" [class.text-muted-foreground]="block.optional">
              <span class="font-medium shrink-0">{{ 'SESSION.KINDS.' + block.kind | translate }}</span>
              @if (theme(block); as t) {
                <span class="truncate">{{ t }}</span>
              }
              @if (block.optional) {
                <span class="text-xs">({{ 'SESSION.OPTIONAL' | translate }})</span>
              }
            </li>
          }
        </ul>
      </a>
      <div class="flex flex-wrap gap-2 empty:hidden">
        <ng-content select="[cardActions]"/>
      </div>
    </article>
  `
})
export class SessionCardComponent {
  // --- INJECTIONS ---
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly session = input.required<SessionSummaryResponse>();
  /** False when the card sits under its day already (week planning). */
  readonly showDate = input(true);

  // --- COMPUTED STATE ---
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  protected readonly link = computed(() => sessionLink(this.session()));
  protected readonly blocks = computed(() => this.session().blocks);
  protected readonly heading = computed(() =>
    this.session().title || this.translate.instant('SESSION.TYPES.' + this.session().type));
  protected readonly dateLabel = computed(() =>
    sessionDateLabel(this.session(), this.lang(), (k, p) => this.translate.instant(k, p)));
  /** Gym name for members of several gyms (personal sessions have none). */
  protected readonly gymName = computed(() => {
    const memberships = this.accessService.memberships();
    if (memberships.length < 2) return null;
    return memberships.find(m => m.gymId === this.session().gymId)?.gymName ?? null;
  });

  // --- HELPERS ---
  protected theme(block: {themeEn?: string | null; themeFr?: string | null}): string {
    return blockTheme(this.lang(), block);
  }
}
