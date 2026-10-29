import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {EMPTY, of} from 'rxjs';
import {vi} from 'vitest';
import {SessionWeekComponent} from './session-week.component';
import {APP_ICONS} from '../../../app.config';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {AccessService} from '../../../core/services/access.service';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {addDays, localDate, mondayOf} from '../session.utils';

describe('SessionWeekComponent', () => {
  let fixture: ComponentFixture<SessionWeekComponent>;
  const monday = mondayOf(localDate());
  const sessionService = {getSessions: vi.fn().mockName('SessionService.getSessions'), refreshNeeded$: EMPTY};
  const writable = vi.fn();
  const displayed = signal<number | null>(null);

  const s = (id: number, type: 'WOD' | 'SPECIALTY', date: string, gymId: number | null) => ({
    id, type, discipline: type === 'WOD' ? 'CROSSFIT' : 'WEIGHTLIFTING', date, endDate: type === 'WOD' ? date : addDays(date, 6),
    title: 'S' + id, gymId, authorId: 1, blocks: [], contentVisible: true, loggedByMe: false
  });

  const init = () => {
    fixture = TestBed.createComponent(SessionWeekComponent);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    sessionService.getSessions.mockReset();
    sessionService.getSessions.mockReturnValue(of({content: [
      s(1, 'WOD', addDays(monday, 1), 4), s(2, 'SPECIALTY', monday, 4), s(3, 'WOD', addDays(monday, 1), 7), s(4, 'WOD', monday, null)
    ]}));
    writable.mockReturnValue([]);
    displayed.set(4);
    await TestBed.configureTestingModule({
      imports: [SessionWeekComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: SessionService, useValue: sessionService},
        {provide: AccessService, useValue: {gymsWithPermission: writable, memberships: signal([])}},
        {provide: DisplayGymService, useValue: {gymId: displayed, matches: (id: number) => displayed() === null || displayed() === id}}]
    }).compileComponents();
  });

  it('should load the current week of the displayed box/prog, specialties apart', () => {
    init();
    expect(sessionService.getSessions).toHaveBeenCalledWith(
      {scope: 'GYM', from: monday, to: addDays(monday, 6)}, 0, 100, expect.anything());
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('SESSION.WEEK.SPECIALTIES');
    expect(text).toContain('S1');
    expect(text).toContain('S2');
    expect(text).not.toContain('S3'); // another box
    expect(text).not.toContain('S4'); // personal session
    expect(fixture.nativeElement.querySelectorAll('ol > li').length).toBe(7);
    expect(text).not.toContain('SESSION.ACTIONS.NEW');
  });

  it('should move from week to week and offer the create button to staff', () => {
    writable.mockReturnValue([{gymId: 4}]);
    init();
    expect(fixture.nativeElement.textContent).toContain('SESSION.ACTIONS.NEW');

    (fixture.nativeElement.querySelector('[aria-label="SESSION.WEEK.NEXT"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(sessionService.getSessions).toHaveBeenLastCalledWith(
      {scope: 'GYM', from: addDays(monday, 7), to: addDays(monday, 13)}, 0, 100, expect.anything());
    expect(fixture.nativeElement.textContent).toContain('SESSION.WEEK.THIS_WEEK');
  });
});
