import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {SessionBlockComponent} from './session-block.component';
import {APP_ICONS} from '../../../app.config';
import {SessionBlockResponse} from '../../../api/aldebaran/models/session.model';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {ScoreService} from '../../../api/aldebaran/services/score.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UnitService} from '../../../core/services/unit.service';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {WodScoresStore} from '../../wods/scores/wod-scores.store';

describe('SessionBlockComponent', () => {
  let fixture: ComponentFixture<SessionBlockComponent>;
  const wod = (overrides: Partial<WodResponse> = {}): WodResponse => ({
    id: 30, title: 'Back Squat 5x5', wodType: 'STRENGTH', scoreType: 'WEIGHT', isPublic: false, competition: false,
    standalone: false, modalities: [], movements: [], createdAt: '', updatedAt: '', ...overrides
  });
  const wodService = {getWod: vi.fn().mockName('WodService.getWod')};
  const scoreService = {
    getMyScores: vi.fn().mockReturnValue(of({content: []})),
    compareScore: vi.fn(),
    getVerificationQueue: vi.fn().mockName('ScoreService.getVerificationQueue'),
    getLeaderboard: vi.fn().mockReturnValue(of({content: [], last: true}))
  };
  const userService = {getDisplayNames: vi.fn().mockReturnValue(of([{id: 8, displayName: 'Anne L.'}]))};

  const init = (block: SessionBlockResponse, log: any = null) => {
    fixture = TestBed.createComponent(SessionBlockComponent);
    fixture.componentRef.setInput('block', block);
    fixture.componentRef.setInput('sessionId', 4);
    fixture.componentRef.setInput('log', log);
    fixture.detectChanges();
  };
  const block = (overrides: Partial<SessionBlockResponse> = {}): SessionBlockResponse => ({
    id: 11, orderIndex: 1, kind: 'SKILL', optional: false, label: 'A', themeEn: 'Heavy squat', stimulus: 'STRENGTH',
    plannedMinutes: 20, wod: wod(), borrowed: false, competition: false, ...overrides
  });

  beforeEach(async () => {
    vi.clearAllMocks();
    wodService.getWod.mockReturnValue(of(wod({capabilities: {canModify: false, locked: false, canJudge: false}})));
    await TestBed.configureTestingModule({
      imports: [SessionBlockComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: WodService, useValue: wodService},
        {provide: ScoreService, useValue: scoreService},
        {provide: UserService, useValue: userService},
        {provide: GymService, useValue: {getAthletes: vi.fn().mockReturnValue(of([]))}},
        {provide: AuthService, useValue: {currentUser: signal({division: null})}},
        {provide: UnitService, useValue: {loadPair: () => null, distancePair: () => null, formatDistance: () => '', massUnit: signal('KG'),
          formatScoreLoad: (v: number) => `${v} kg`, convertScoreLoad: (v: number) => v}},
        {provide: AccessService, useValue: {memberships: signal([]), userId: signal(2)}},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}}]
    }).compileComponents();
  });

  it('should show a text block without WOD nor score', () => {
    init(block({kind: 'WARM_UP', wod: null, textEn: '2 rounds: air squats', stimulus: null}));
    expect(fixture.nativeElement.textContent).toContain('2 rounds: air squats');
    expect(fixture.nativeElement.querySelector('app-wod-whiteboard')).toBeNull();
    expect(wodService.getWod).not.toHaveBeenCalled();
  });

  it('should show the WOD, link new scores to the journal and let the athlete tick the block', () => {
    const log = {id: 9, blocks: [{blockId: 11, kind: 'SKILL', done: true}], scores: []};
    init(block(), log);
    const store = fixture.debugElement.injector.get(WodScoresStore);

    expect(wodService.getWod).toHaveBeenCalledWith(30);
    expect(fixture.nativeElement.querySelector('app-wod-whiteboard')).toBeTruthy();
    expect(store.sessionLogId()).toBe(9);
    expect(store.sessionId()).toBe(4);
    expect(scoreService.getMyScores).toHaveBeenCalledWith(30, 0, 50, undefined, 4);

    const done = vi.fn();
    fixture.componentInstance.doneChange.subscribe(done);
    (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    expect(done).toHaveBeenCalledWith(false);

    (fixture.nativeElement.querySelector('.log-score') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-score-form')).toBeTruthy();
  });

  it('should fold the session leaderboard and load only the session scores', () => {
    init(block({borrowed: true, wod: wod({id: 31, title: 'Fran', standalone: true})}), {id: 9, blocks: [], scores: []});
    expect(fixture.nativeElement.querySelector('app-score-leaderboard')).toBeNull();

    (fixture.nativeElement.querySelector('.session-leaderboard-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-score-leaderboard')).toBeTruthy();
    expect(scoreService.getLeaderboard).toHaveBeenCalledWith(30, 'RX', 0, expect.any(Number), [], false, null, 4);
    expect(fixture.nativeElement.textContent).toContain('SCORE.LEADERBOARD.SESSION_TITLE');
  });

  it('should ask for "Séance faite" before scoring a borrowed WOD', () => {
    init(block({borrowed: true, wod: wod({id: 31, title: 'Fran', standalone: true})}));
    expect(fixture.nativeElement.querySelector('.log-score')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('SESSION.MESSAGES.LOG_FIRST');
  });

  it('should show the judge the submitted scores to verify', () => {
    wodService.getWod.mockReturnValue(of(wod({competition: true, capabilities: {canModify: false, locked: false, canJudge: true}})));
    scoreService.getVerificationQueue.mockReturnValue(of([{
      id: 50, userId: 8, date: '2026-11-03', scaling: 'RX', timeCapped: false, personalRecord: false, maxWeight: 100,
      weightUnit: 'KG', loggedAt: '', wodSummary: {} as any, capabilities: {canModify: true, canVerify: true}
    }]));
    init(block({competition: true}));

    expect(scoreService.getVerificationQueue).toHaveBeenCalledWith(30);
    const queue: HTMLElement = fixture.nativeElement.querySelector('.verification-queue');
    expect(queue.textContent).toContain('Anne L.');
    expect(queue.textContent).toContain('SCORE.ACTIONS.VERIFY');
  });
});
