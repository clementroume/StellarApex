import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../app.config';
import {ProgrammingService} from '../../api/aldebaran/services/programming.service';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {localDate, mondayOf} from '../sessions/session.utils';
import {ProgrammingBalanceComponent} from './programming-balance.component';
import {balanceOf} from './programming.fixture';
import {DayOfWeek} from '../../api/aldebaran/models/programming.model';

describe('ProgrammingBalanceComponent', () => {
  let fixture: ComponentFixture<ProgrammingBalanceComponent>;
  let service: {getBalance: ReturnType<typeof vi.fn>};

  async function setup(response: (days: DayOfWeek[]) => unknown, staff = [{gymId: 4, gymName: 'Box', gymRole: 'COACH'}]) {
    service = {
      getBalance: vi.fn().mockName('ProgrammingService.getBalance')
        .mockImplementation((_: number, q: {days: DayOfWeek[]}) => response(q.days))
    };
    await TestBed.configureTestingModule({
      imports: [ProgrammingBalanceComponent],
      providers: [
        provideRouter([]), provideIcons(APP_ICONS), provideTranslateService(),
        {provide: ProgrammingService, useValue: service},
        {provide: AccessService, useValue: {staffGyms: () => staff, gymsWithPermission: () => []}},
        {provide: DisplayGymService, useValue: {gymId: signal(null)}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ProgrammingBalanceComponent);
    fixture.detectChanges();
  }

  const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;
  const all = (selector: string) => Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];
  const lastQuery = () => service.getBalance.mock.lastCall![1];

  it('asks the current week for 4 weeks, RX, without optional blocks, and shows the full week', async () => {
    await setup(() => of(balanceOf()));
    expect(service.getBalance).toHaveBeenCalledWith(4,
      {start: mondayOf(localDate()), weeks: 4, scaled: false, optional: false, days: []}, expect.anything());
    expect(el('[data-slot="full-week"]')).not.toBeNull();
    expect(el('app-pattern-radar')).not.toBeNull();
    expect(el('[data-slot="unusual"]')).not.toBeNull();
    expect(el('[data-slot="concentrated"]')).not.toBeNull();
    expect(el('[data-slot="uncounted"]')).not.toBeNull();
    expect(all('[data-slot="profile-row"]')).toHaveLength(1);
    expect(el('app-review-metcons')).not.toBeNull();
  });

  it('switches to Scaled, counts the optional blocks and moves the cycle', async () => {
    await setup(() => of(balanceOf()));
    (all('button').find(b => b.textContent!.trim() === 'Scaled') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(lastQuery().scaled).toBe(true);
    (el('input[role="switch"]') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(lastQuery().optional).toBe(true);
    (el('button[aria-label="PROGRAMMING.NEXT_WEEK"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(lastQuery().start > mondayOf(localDate())).toBe(true);
  });

  it('opens a profile in alert, shows its alert and goes back to all profiles', async () => {
    await setup(days => of(balanceOf(days)));
    (el('[data-slot="profile-row"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(lastQuery().days).toEqual(['THURSDAY', 'FRIDAY', 'SATURDAY']);
    expect(el('[data-slot="profile"]')).not.toBeNull();
    expect(el('[data-slot="alert"]')!.textContent).toContain('PROGRAMMING.ALERT.GAP_TITLE');
    (all('button').find(b => b.textContent!.includes('PROGRAMMING.PROFILES.BACK')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(lastQuery().days).toEqual([]);
    expect(el('[data-slot="full-week"]')).not.toBeNull();
  });

  it('keeps the metcons and says where minutes come from when the prescription gives none', async () => {
    const noMinutes = balanceOf();
    noMinutes.fullWeek.minutesPerWeek = {VERY_SHORT: 0, SHORT: 0, MEDIUM: 0, LONG: 0, INTERVALS: 0};
    await setup(() => of(noMinutes));
    expect(el('app-review-metcons')!.textContent).toContain('PROGRAMMING.METCONS_EMPTY');
    expect(el('app-review-modalities')).not.toBeNull();
  });

  it('warns about a partial cycle, and disables Scaled when nothing is programmed in Scaled', async () => {
    const partial = balanceOf();
    partial.programmedWeeks = 1;
    partial.scaledLines = 0;
    await setup(() => of(partial));
    expect(el('[data-slot="partial"]')!.textContent).toContain('PROGRAMMING.PARTIAL');
    expect(el('[data-slot="no-scaled"]')).not.toBeNull();
    const scaled = all('button').find(b => b.textContent!.trim() === 'Scaled') as HTMLButtonElement;
    expect(scaled.disabled).toBe(true);
  });

  it('explains the profile sizes left out and draws one bar per region', async () => {
    await setup(() => of(balanceOf()));
    // Sizes 3 and 4 are listed; 5 comes with no profile (the backend decides, the page explains)
    expect(el('[data-slot="sizes-note"]')!.textContent).toContain('PROGRAMMING.PROFILES.SIZES_NOTE');
    expect(all('[data-slot="regions"] .rounded-full.bg-muted')).toHaveLength(2);
    expect(el('[data-slot="partial"]')).toBeNull();
  });

  it('never shows the previous profile under the days now asked', async () => {
    // The answer always carries Thursday to Saturday, whatever was asked
    await setup(days => of(balanceOf(days.length ? ['THURSDAY', 'FRIDAY', 'SATURDAY'] : [])));
    (el('[data-slot="profile-row"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el('[data-slot="profile"]')).not.toBeNull();
    const chips = all('[aria-labelledby="programming-compose"] button');
    chips[0].click();
    fixture.detectChanges();
    expect(lastQuery().days).toEqual(['MONDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']);
    expect(el('[data-slot="profile"]')).toBeNull();
  });

    it('composes a profile with the day chips', async () => {
    await setup(days => of(balanceOf(days)));
    const chips = all('[aria-labelledby="programming-compose"] button');
    expect(chips).toHaveLength(6);
    chips[2].click();
    chips[0].click();
    fixture.detectChanges();
    expect(lastQuery().days).toEqual(['MONDAY', 'WEDNESDAY']);
  });

  it('says when nothing could be loaded, and when the user is staff nowhere', async () => {
    await setup(() => throwError(() => new Error('down')));
    expect(fixture.nativeElement.textContent).toContain('PROGRAMMING.LOAD_ERROR');
    TestBed.resetTestingModule();
    await setup(() => of(balanceOf()), []);
    expect(service.getBalance).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('PROGRAMMING.NO_GYM');
  });
});
