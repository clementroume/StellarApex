import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../app.config';
import {WeeklyBalance} from '../../api/aldebaran/models/balance.model';
import {BalanceService} from '../../api/aldebaran/services/balance.service';
import {localDate, mondayOf} from '../sessions/session.utils';
import {WeeklyReviewComponent} from './weekly-review.component';

const balance = (sets: number): WeeklyBalance => ({
  weekStart: mondayOf(localDate()),
  windowStart: mondayOf(localDate()),
  muscles: sets > 0 ? [{muscleId: 1, code: 'quadriceps-femoris', region: 'THIGH_LEG', medicalName: 'Quadriceps', movingSets: sets, holdingSets: 0}] : [],
  volume: Array.from({length: 8}, (_, i) => ({weekStart: `2026-01-0${i + 1}`, sets: i === 7 ? sets : 0})),
  minutes: {VERY_SHORT: 0, SHORT: sets > 0 ? 9 : 0, MEDIUM: 0, LONG: 0, INTERVALS: 0},
  lifts: [],
  modalities: {GYMNASTICS: sets, WEIGHTLIFTING: 0, MONOSTRUCTURAL: 0}
});

describe('WeeklyReviewComponent', () => {
  let fixture: ComponentFixture<WeeklyReviewComponent>;
  let service: {getMyBalance: ReturnType<typeof vi.fn>};

  async function setup(response: unknown) {
    service = {getMyBalance: vi.fn().mockName('BalanceService.getMyBalance').mockReturnValue(response)};
    await TestBed.configureTestingModule({
      imports: [WeeklyReviewComponent],
      providers: [provideRouter([]), provideIcons(APP_ICONS), provideTranslateService(), {provide: BalanceService, useValue: service}]
    }).compileComponents();
    fixture = TestBed.createComponent(WeeklyReviewComponent);
    fixture.detectChanges();
  }

  const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;

  it('asks the current week and shows the sections, the next week disabled', async () => {
    await setup(of(balance(4)));
    expect(service.getMyBalance).toHaveBeenCalledWith(mondayOf(localDate()), expect.anything());
    expect(el('app-review-muscles')).not.toBeNull();
    expect(el('app-review-volume')).not.toBeNull();
    const next = el('button[aria-label="REVIEW.NEXT_WEEK"]') as HTMLButtonElement;
    expect(next.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('REVIEW.CURRENT');
  });

  it('goes back one week and loads it', async () => {
    await setup(of(balance(4)));
    (el('button[aria-label="REVIEW.PREVIOUS_WEEK"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const monday = mondayOf(localDate());
    const previous = new Date(`${monday}T12:00:00`);
    previous.setDate(previous.getDate() - 7);
    expect(service.getMyBalance).toHaveBeenLastCalledWith(localDate(previous), expect.anything());
    expect(fixture.nativeElement.textContent).not.toContain('REVIEW.CURRENT');
  });

  it('shows one sentence for the whole screen when the window is empty', async () => {
    await setup(of(balance(0)));
    expect(el('[data-slot="review-empty"]')).not.toBeNull();
    expect(el('app-review-muscles')).toBeNull();
  });

  it('explains every section in one dialog opened next to the title', async () => {
    await setup(of(balance(4)));
    expect(el('details')).toBeNull();
    (el('[data-slot="review-help"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const help = document.querySelector('[data-slot="review-help-text"]') as HTMLElement;
    expect(help.querySelectorAll('dt')).toHaveLength(5);
    expect(help.textContent).toContain('REVIEW.HELP.FORCE');
  });

  it('reads the device day again when the page comes back, for a page left open past Sunday', async () => {
    vi.useFakeTimers({toFake: ['Date']});
    try {
      vi.setSystemTime(new Date('2026-11-15T23:50:00'));
      await setup(of(balance(4)));
      expect(fixture.componentInstance.currentMonday()).toBe('2026-11-09');
      vi.setSystemTime(new Date('2026-11-16T08:00:00'));
      window.dispatchEvent(new Event('focus'));
      expect(fixture.componentInstance.currentMonday()).toBe('2026-11-16');
      expect(fixture.componentInstance.isCurrent()).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('says so when the balance cannot be loaded', async () => {
    await setup(throwError(() => new Error('down')));
    expect(fixture.nativeElement.textContent).toContain('REVIEW.LOAD_ERROR');
  });
});
