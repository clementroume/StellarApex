import {vi} from 'vitest';
import {of} from 'rxjs';
import {UserService} from '../../../../api/antares/services/user.service';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {ScoreHistoryComponent} from './score-history.component';
import {WodScoresStore} from '../wod-scores.store';
import {UnitService} from '../../../../core/services/unit.service';
import {APP_ICONS} from '../../../../app.config';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';

describe('ScoreHistoryComponent', () => {
  let fixture: ComponentFixture<ScoreHistoryComponent>;
  const scores = signal<ScoreResponse[]>([]);
  const store = {
    wod: signal({id: 1, scoreType: 'REPS', repsPerRound: 30}),
    scores,
    openNew: vi.fn().mockName('WodScoresStore.openNew'),
    openEdit: vi.fn().mockName('WodScoresStore.openEdit'),
    delete: vi.fn().mockName('WodScoresStore.delete')
  };
  const score = (id: number, canModify: boolean, personalRecord = false): ScoreResponse => ({
    id, userId: 1, date: '2026-10-26', wodSummary: {} as any, reps: 360, scaling: 'SCALED',
    timeCapped: false, personalRecord, loggedAt: '', userComment: 'Tough one', capabilities: {canModify, canVerify: false}
  });

  const userService = {
    getDisplayNames: vi.fn().mockName('UserService.getDisplayNames')
  };

  beforeEach(async () => {
    scores.set([]);
    userService.getDisplayNames.mockReset();
    userService.getDisplayNames.mockReturnValue(of([{id: 2, displayName: 'Coach C.'}]));
    await TestBed.configureTestingModule({
      imports: [ScoreHistoryComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: store},
        {provide: UnitService, useValue: {}},
        {provide: UserService, useValue: userService}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ScoreHistoryComponent);
    fixture.detectChanges();
  });

  it('should say when no score is logged yet', () => {
    expect(fixture.nativeElement.textContent).toContain('SCORE.MESSAGES.EMPTY');
  });

  it('should list the scores with their level, PR badge and comment', () => {
    scores.set([score(1, true, true)]);
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('SCORE.DISPLAY.ROUNDS');
    expect(text).toContain('SCORE.SCALING.SCALED');
    expect(text).toContain('SCORE.PR');
    expect(text).toContain('Tough one');
  });

  it('should show the optional tiebreak time', () => {
    scores.set([{...score(1, true), tiebreakSeconds: 1105}]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SCORE.TIEBREAK_SHORT');
  });

  it('should offer edit and delete only when Aldebaran allows it', () => {
    scores.set([score(1, false)]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('li button')).toBeNull();

    scores.set([score(2, true)]);
    fixture.detectChanges();
    const [edit, remove] = fixture.nativeElement.querySelectorAll('li button') as NodeListOf<HTMLButtonElement>;
    edit.click();
    remove.click();
    expect(store.openEdit).toHaveBeenCalledWith(scores()[0]);
    expect(store.delete).toHaveBeenCalledWith(scores()[0]);
  });

  it('should name the judge who verified or logged a competition score', () => {
    scores.set([{...score(1, false), loggedBy: 2, verifiedBy: 2, verifiedAt: '2026-10-27T10:00:00'}]);
    fixture.detectChanges();

    expect(userService.getDisplayNames).toHaveBeenCalledWith([2]);
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('SCORE.VERIFIED_BY');
    expect(text).toContain('SCORE.LOGGED_BY');
    // Verified: locked for the athlete, no edit button
    expect(fixture.nativeElement.querySelector('ng-icon[name="heroPencilSquare"]')).toBeNull();
  });
});
