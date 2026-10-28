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
});
