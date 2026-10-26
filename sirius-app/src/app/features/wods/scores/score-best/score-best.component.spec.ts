import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {ScoreBestComponent} from './score-best.component';
import {WodScoresStore} from '../wod-scores.store';
import {UnitService} from '../../../../core/services/unit.service';
import {APP_ICONS} from '../../../../app.config';
import {ScoreComparisonResponse, ScoreResponse} from '../../../../api/aldebaran/models/score.model';

describe('ScoreBestComponent', () => {
  let fixture: ComponentFixture<ScoreBestComponent>;
  const best = signal<ScoreResponse | null>(null);
  const comparison = signal<ScoreComparisonResponse | null>(null);
  const store = {
    wod: signal({id: 1, scoreType: 'TIME'}),
    best,
    comparison,
    isLoading: signal(false),
    openNew: vi.fn().mockName('WodScoresStore.openNew')
  };

  beforeEach(async () => {
    best.set(null);
    comparison.set(null);
    await TestBed.configureTestingModule({
      imports: [ScoreBestComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: store},
        {provide: UnitService, useValue: {unitSystem: signal('METRIC')}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ScoreBestComponent);
    fixture.detectChanges();
  });

  it('should invite to log a score when there is none', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.textContent).toContain('SCORE.ACTIONS.LOG');

    button.click();
    expect(store.openNew).toHaveBeenCalled();
  });

  it('should show the personal record with its level and rank', () => {
    best.set({
      id: 2, userId: 1, date: '2026-10-26', wodSummary: {} as any, timeSeconds: 272, scaling: 'RX',
      timeCapped: false, personalRecord: true, loggedAt: ''
    });
    comparison.set({rank: 2, totalScores: 8, percentile: 85});
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(fixture.nativeElement.querySelector('#best-score-value').textContent).toBe('4:32');
    expect(text).toContain('SCORE.SCALING.RX');
    expect(text).toContain('SCORE.RANK');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
