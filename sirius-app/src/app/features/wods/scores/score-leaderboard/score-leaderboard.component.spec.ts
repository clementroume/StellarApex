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
  const setVerified = vi.fn().mockName('WodScoresStore.setVerified');

  const score = (id: number, userId: number, seconds: number, tb?: number): ScoreResponse => ({
    id, userId, date: '2026-10-27', wodSummary: {} as any, timeSeconds: seconds, scaling: 'RX', timeCapped: false,
    personalRecord: true, loggedAt: '', tiebreakSeconds: tb
  });

  const init = async (gymId: number | null = null, competition = false) => {
    wod.set({id: 7, scoreType: 'TIME', gymId, competition});
    await TestBed.configureTestingModule({
      imports: [ScoreLeaderboardComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: {wod, changed$, setVerified, openEdit: vi.fn(), delete: vi.fn()}},
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

    expect(scoreService.getLeaderboard).toHaveBeenCalledWith(7, 'RX', 0, 50, false, false);
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
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'SCALED', 0, 50, false, false);

    component.onMyGymsChange(true);
    fixture.detectChanges();
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'SCALED', 0, 50, true, false);

    const calls = scoreService.getLeaderboard.mock.calls.length;
    changed$.next();
    expect(scoreService.getLeaderboard.mock.calls.length).toBe(calls + 1);
  });

  it('should offer the my-gym filter on a public WOD only', async () => {
    await init();
    expect(fixture.nativeElement.querySelector('#leaderboard-my-gyms')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('SCORE.LEADERBOARD.MY_GYM');
    TestBed.resetTestingModule();
    await init(42);
    expect(fixture.nativeElement.querySelector('#leaderboard-my-gyms')).toBeNull();
  });

  it('should load the next page on demand', async () => {
    scoreService.getLeaderboard.mockReturnValueOnce(of({content: [score(1, 1, 250)], last: false} as any));
    await init();
    expect(fixture.nativeElement.textContent).toContain('SCORE.LEADERBOARD.MORE');
    component.load(false);
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'RX', 1, 50, false, false);
  });

  it('should rank verified scores only by default on a competition WOD, and let judges verify', async () => {
    const judged = {...score(1, 1, 250), capabilities: {canModify: true, canVerify: true}};
    const verified = {...score(3, 3, 260), verifiedAt: '2026-10-27T10:00:00', capabilities: {canModify: true, canVerify: true}};
    scoreService.getLeaderboard.mockReturnValue(of({content: [judged, verified], last: true} as any));
    await init(5, true);

    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'RX', 0, 50, false, true);
    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    buttons.find(b => b.textContent?.includes('SCORE.ACTIONS.VERIFY'))!.click();
    expect(setVerified).toHaveBeenCalledWith(judged, true);
    expect(buttons.some(b => b.getAttribute('aria-label') === 'SCORE.ACTIONS.UNVERIFY')).toBe(true);

    const filter: HTMLInputElement = fixture.nativeElement.querySelector('#leaderboard-verified');
    filter.checked = false;
    filter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'RX', 0, 50, false, false);
  });

  it('should show every score to a judge by default, so they see the scores to verify', async () => {
    wod.set({id: 7, scoreType: 'TIME', gymId: 5, competition: true, capabilities: {canJudge: true}});
    await TestBed.configureTestingModule({
      imports: [ScoreLeaderboardComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: {wod, changed$, setVerified, openEdit: vi.fn(), delete: vi.fn()}},
        {provide: ScoreService, useValue: scoreService},
        {provide: UserService, useValue: userService},
        {provide: AccessService, useValue: {userId: signal(2), memberships: signal([])}},
        {provide: UnitService, useValue: {}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ScoreLeaderboardComponent);
    fixture.detectChanges();

    expect(scoreService.getLeaderboard).toHaveBeenLastCalledWith(7, 'RX', 0, 50, false, false);
    expect((fixture.nativeElement.querySelector('#leaderboard-verified') as HTMLInputElement).checked).toBe(false);
  });

  it('should not offer the verified filter outside competitions', async () => {
    await init();
    expect(fixture.nativeElement.querySelector('#leaderboard-verified')).toBeNull();
  });
});
