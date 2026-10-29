import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {TrainingJournalComponent} from './training-journal.component';
import {ScoreService} from '../../api/aldebaran/services/score.service';
import {ScoreResponse} from '../../api/aldebaran/models/score.model';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {UnitService} from '../../core/services/unit.service';
import {SessionService} from '../../api/aldebaran/services/session.service';
import {AccessService} from '../../core/services/access.service';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../app.config';
import {EMPTY} from 'rxjs';

const score = (id: number): ScoreResponse => ({
  id, userId: 1, date: '2026-10-27', gymId: null, reps: 50, scaling: 'RX', timeCapped: false, personalRecord: false,
  wodSummary: {id, title: 'WOD ' + id, wodType: 'AMRAP', scoreType: 'REPS'}
} as ScoreResponse);

describe('TrainingJournalComponent', () => {
  let fixture: ComponentFixture<TrainingJournalComponent>;
  const scoreService = {getMyScores: vi.fn().mockName('ScoreService.getMyScores')};

  beforeEach(async () => {
    scoreService.getMyScores
      .mockReturnValueOnce(of({content: [score(1)], last: false}))
      .mockReturnValueOnce(of({content: [score(2)], last: true}));
    await TestBed.configureTestingModule({
      imports: [TrainingJournalComponent],
      providers: [
        provideRouter([]), provideTranslateService(),
        {provide: ScoreService, useValue: scoreService},
        {provide: UnitService, useValue: {}},
        {provide: DisplayGymService, useValue: {matches: () => true}},
        provideIcons(APP_ICONS),
        {provide: AccessService, useValue: {memberships: signal([])}},
        {
          provide: SessionService,
          useValue: {
            refreshNeeded$: EMPTY,
            getMyLogs: vi.fn().mockReturnValue(of({content: [{
              id: 3, userId: 1, sessionId: 8, sessionType: 'WOD', discipline: 'CROSSFIT', sessionDate: '2026-10-27',
              sessionTitle: 'Murph day', gymId: 4, date: '2026-10-27', source: 'MANUAL',
              blocks: [{blockId: 1, kind: 'SKILL', done: true}, {blockId: 2, kind: 'WOD', done: false}], scores: []
            }], last: true})),
            getSessions: vi.fn().mockReturnValue(of({content: [], last: true}))
          }
        },
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingJournalComponent);
    fixture.detectChanges();
  });

  it('should load the next page on demand until the last one', () => {
    expect(fixture.nativeElement.querySelectorAll('app-score-row').length).toBe(1);
    const more = () => Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.includes('TRAINING.JOURNAL.MORE'));

    more()!.click();
    fixture.detectChanges();

    expect(scoreService.getMyScores).toHaveBeenLastCalledWith(undefined, 1, 20, expect.anything());
    expect(fixture.nativeElement.querySelectorAll('app-score-row').length).toBe(2);
    expect(more()).toBeUndefined();
  });

  it('should list the sessions done with their blocks done', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Murph day');
    expect(text).toContain('SESSION.JOURNAL.BLOCKS');
    expect(fixture.nativeElement.querySelector('#new-personal-session')).toBeTruthy();
  });
});
