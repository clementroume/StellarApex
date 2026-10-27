import type {MockedObject} from 'vitest';
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, Subject} from 'rxjs';
import {ScoreLeaderboardComponent} from './score-leaderboard.component';
import {WodScoresStore} from '../wod-scores.store';
import {ScoreService} from '../../../../api/aldebaran/services/score.service';
import {UserService} from '../../../../api/antares/services/user.service';
import {AccessService} from '../../../../core/services/access.service';
import {UnitService} from '../../../../core/services/unit.service';
import {APP_ICONS} from '../../../../app.config';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';

describe('ScoreLeaderboardComponent', () => {
  let fixture: ComponentFixture<ScoreLeaderboardComponent>;
  let component: ScoreLeaderboardComponent;
  let scoreService: MockedObject<ScoreService>;
  let userService: MockedObject<UserService>;
  const changed$ = new Subject<void>();
  const wod = signal<any>({id: 7, scoreType: 'TIME', gymId: null});

  const score = (id: number, userId: number, seconds: number, tb?: number): ScoreResponse => ({
    id, userId, date: '2026-10-27', wodSummary: {} as any, timeSeconds: seconds, scaling: 'RX', timeCapped: false,
    personalRecord: true, loggedAt: '', tiebreakSeconds: tb
  });

  const init = async (gymId: number | null = null) => {
    wod.set({id: 7, scoreType: 'TIME', gymId});
    await TestBed.configureTestingModule({
      imports: [ScoreLeaderboardComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: {wod, changed$}},
        {provide: ScoreService, useValue: scoreService},
        {provide: UserService, useValue: userService},
        {provide: AccessService, useValue: {userId: signal(2), memberships: signal([{gymId: 5, gymName: 'Box'}])}},
        {provide: UnitService, useValue: {}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ScoreLeaderboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    scoreService = {getLeaderboard: vi.fn().mockName('ScoreService.getLeaderboard')} as any;
    userService = {getDisplayNames: vi.fn().mockName('UserService.getDisplayNames')} as any;
    scoreService.getLeaderboard.mockReturnValue(of({content: [score(1, 1, 250), score(2, 2, 300, 290)], last: true} as any));
    userService.getDisplayNames.mockReturnValue(of([{id: 2, displayName: 'Clément R.'}]));
  });

  it('should rank the PRs, name the visible athletes and highlight the user', async () => {
    await init();

    expect(scoreService.getLeaderboard).toHaveBeenCalledWith(7, 'RX', 0, 50, null);
    expect(userService.getDisplayNames).toHaveBeenCalledWith([1, 2]);
    const rows = fixture.nativeElement.querySelectorAll('li') as NodeListOf<HTMLElement>;
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('SCORE.LEADERBOARD.ANONYMOUS');
    expect(rows[0].textContent).toContain('4:10');
    expect(rows[1].textContent).toContain('Clément R.');
    expect(rows[1].getAttribute('aria-current')).toBe('true');
    expect(rows[1].textContent).toContain('SCORE.TIEBREAK_SHORT');
  });

  it('should reload on a level or gym change and after a score change', async () => {
    await init();
    component.onScalingChange('SCALED');
    fixture.detectChanges();
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'SCALED', 0, 50, null);

    component.onGymChange('5');
    fixture.detectChanges();
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'SCALED', 0, 50, 5);

    const calls = scoreService.getLeaderboard.mock.calls.length;
    changed$.next();
    expect(scoreService.getLeaderboard.mock.calls.length).toBe(calls + 1);
  });

  it('should offer the gym filter on a public WOD only', async () => {
    await init();
    expect(fixture.nativeElement.querySelector('#leaderboard-gym')).toBeTruthy();
    TestBed.resetTestingModule();
    await init(42);
    expect(fixture.nativeElement.querySelector('#leaderboard-gym')).toBeNull();
  });

  it('should load the next page on demand', async () => {
    scoreService.getLeaderboard.mockReturnValueOnce(of({content: [score(1, 1, 250)], last: false} as any));
    await init();
    expect(fixture.nativeElement.textContent).toContain('SCORE.LEADERBOARD.MORE');
    component.load(false);
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'RX', 1, 50, null);
  });
});
