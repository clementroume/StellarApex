import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {TodayComponent} from './today.component';
import {APP_ICONS} from '../../app.config';
import {AuthService} from '../../api/antares/services/auth.service';
import {ScoreService} from '../../api/aldebaran/services/score.service';
import {ScoreResponse} from '../../api/aldebaran/models/score.model';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {UnitService} from '../../core/services/unit.service';
import {SessionService} from '../../api/aldebaran/services/session.service';
import {SessionSummaryResponse} from '../../api/aldebaran/models/session.model';
import {AccessService} from '../../core/services/access.service';
import {NotificationService} from '../../core/services/notification.service';

const session = (id: number, gymId: number | null, loggedByMe = false): SessionSummaryResponse => ({
  id, type: 'WOD', discipline: 'CROSSFIT', date: '2026-11-03', endDate: '2026-11-03', title: 'Session ' + id, gymId,
  authorId: 1, blocks: [{kind: 'SKILL', optional: false, themeEn: 'Heavy squat'}], contentVisible: false, loggedByMe
});

const score = (id: number, gymId: number | null, personalRecord = false): ScoreResponse => ({
  id, userId: 1, date: '2026-10-27', gymId, reps: 100, scaling: 'RX', timeCapped: false, personalRecord,
  wodSummary: {id: 10 + id, title: 'WOD ' + id, wodType: 'AMRAP', scoreType: 'REPS'}
} as ScoreResponse);

describe('TodayComponent', () => {
  let fixture: ComponentFixture<TodayComponent>;
  const scoreService = {getMyScores: vi.fn().mockName('ScoreService.getMyScores')};
  const sessionService = {
    getSessions: vi.fn().mockName('SessionService.getSessions'),
    logSession: vi.fn().mockName('SessionService.logSession')
  };
  const notificationService = {showSuccess: vi.fn(), showError: vi.fn()};
  const gymId = signal<number | null>(null);

  const create = () => {
    TestBed.configureTestingModule({
      imports: [TodayComponent],
      providers: [
        provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: AuthService, useValue: {currentUser: signal({firstName: 'Ana'})}},
        {provide: ScoreService, useValue: scoreService},
        {provide: UnitService, useValue: {}},
        {provide: SessionService, useValue: sessionService},
        {provide: NotificationService, useValue: notificationService},
        {provide: AccessService, useValue: {memberships: signal([])}},
        {
          provide: DisplayGymService,
          useValue: {gymId, membership: signal(null), matches: (id: number) => gymId() === null || gymId() === id}
        },
      ]
    });
    fixture = TestBed.createComponent(TodayComponent);
    fixture.detectChanges();
  };
  const titles = () => Array.from(fixture.nativeElement.querySelectorAll('app-score-row'))
    .map(r => (r as HTMLElement).textContent!.match(/WOD \d/)![0]);

  beforeEach(() => {
    gymId.set(null);
    scoreService.getMyScores.mockReturnValue(of({content: [score(1, 4, true), score(2, 7), score(3, null)], last: true}));
    sessionService.getSessions.mockReturnValue(of({content: [], last: true}));
    sessionService.logSession.mockReset();
  });

  it('should list the latest scores and the records', () => {
    create();
    expect(titles()).toEqual(['WOD 1', 'WOD 2', 'WOD 3', 'WOD 1']);
    expect(fixture.nativeElement.textContent).toContain('TODAY.SESSION.EMPTY');
  });

  it('should show today\'s sessions of the displayed box/prog and log one from home', () => {
    gymId.set(7);
    sessionService.getSessions.mockReturnValue(of({content: [session(1, 7), session(2, 4), session(3, null, true)], last: true}));
    sessionService.logSession.mockReturnValue(of({id: 9}));
    create();

    const cards = fixture.nativeElement.querySelectorAll('app-session-card');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Session 1');
    expect(cards[0].textContent).toContain('Heavy squat');
    expect(fixture.nativeElement.querySelectorAll('.today-log').length).toBe(1);

    (fixture.nativeElement.querySelector('.today-log') as HTMLButtonElement).click();
    expect(sessionService.logSession).toHaveBeenCalledWith(1, expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
    expect(notificationService.showSuccess).toHaveBeenCalledWith('SESSION.MESSAGES.LOGGED');
    expect(sessionService.getSessions).toHaveBeenCalledTimes(2);
  });

  it('should keep only the displayed box/prog and personal scores', () => {
    gymId.set(7);
    create();
    expect(titles()).toEqual(['WOD 2', 'WOD 3']);
    expect(fixture.nativeElement.textContent).toContain('TODAY.RECORDS.EMPTY');
  });

  it('should explain a loading failure', () => {
    scoreService.getMyScores.mockReturnValue(throwError(() => new Error('500')));
    create();
    expect(fixture.nativeElement.textContent).toContain('TODAY.SCORES.ERROR');
  });
});
