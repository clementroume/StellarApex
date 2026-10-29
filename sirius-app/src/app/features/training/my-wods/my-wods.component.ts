import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {PageComponent} from '../../../shared/component/page/page.component';
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {wodTypeLabel} from '../../wods/wod.utils';

const PAGE_SIZE = 20;

/** My training › My WODs: the user's personal WODs (author only), written with the quick editor. */
@Component({
  selector: 'app-my-wods',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmButton, HlmCardImports, HlmSpinner, PageComponent, ListItemComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page [title]="'TRAINING.WODS.TITLE' | translate" [subtitle]="'TRAINING.WODS.SUBTITLE' | translate">
      <a pageActions hlmBtn size="lg" id="new-wod" routerLink="/training/wods/new">
        <ng-icon name="heroPlus" aria-hidden="true"/>{{ 'TRAINING.WODS.NEW' | translate }}
      </a>
      <section hlmCard>
        <div hlmCardContent>
          <ul class="divide-y">
            @for (wod of wods(); track wod.id) {
              <app-list-item [title]="wod.title" [badge]="typeLabel(wod)" [subtitle]="wod.repScheme ?? ''" [link]="['/wods', wod.id]"/>
            } @empty {
              @if (!isLoading()) {
                <li class="py-8 text-center text-sm text-muted-foreground">{{ 'TRAINING.WODS.EMPTY' | translate }}</li>
              }
            }
          </ul>
          @if (isLoading()) {
            <div class="flex justify-center py-6"><hlm-spinner class="text-2xl text-primary" [aria-label]="'GLOBAL.LOADING' | translate"/></div>
          } @else if (hasMore()) {
            <div class="flex justify-center pt-4">
              <button hlmBtn variant="outline" size="lg" (click)="load()">{{ 'TRAINING.JOURNAL.MORE' | translate }}</button>
            </div>
          }
        </div>
      </section>
    </app-page>
  `
})
export class MyWodsComponent {
  // --- INJECTIONS ---
  private readonly wodService = inject(WodService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly wods = signal<WodSummaryResponse[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly hasMore = signal(false);
  private page = 0;

  // --- LIFECYCLE ---
  constructor() {
    this.load();
  }

  // --- DATA FETCHING ---
  protected load(): void {
    this.isLoading.set(true);
    this.wodService.getWods({scope: 'MINE'}, this.page, PAGE_SIZE, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: slice => {
        this.wods.update(list => [...list, ...slice.content]);
        this.hasMore.set(!slice.last);
        this.page++;
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  // --- HELPERS ---
  protected typeLabel(wod: WodSummaryResponse): string {
    return wodTypeLabel(key => this.translate.instant(key), wod);
  }
}
