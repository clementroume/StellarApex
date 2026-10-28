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

const score = (id: number, gymId: number | null, personalRecord = false): ScoreResponse => ({
  id, userId: 1, date: '2026-10-27', gymId, reps: 100, scaling: 'RX', timeCapped: false, personalRecord,
  wodSummary: {id: 10 + id, title: 'WOD ' + id, wodType: 'AMRAP', scoreType: 'REPS'}
} as ScoreResponse);

describe('TodayComponent', () => {
  let fixture: ComponentFixture<TodayComponent>;
  const scoreService = {getMyScores: vi.fn().mockName('ScoreService.getMyScores')};
  const gymId = signal<number | null>(null);

  const create = () => {
    TestBed.configureTestingModule({
      imports: [TodayComponent],
      providers: [
        provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: AuthService, useValue: {currentUser: signal({firstName: 'Ana'})}},
        {provide: ScoreService, useValue: scoreService},
        {provide: UnitService, useValue: {}},
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
  });

  it('should list the latest scores and the records', () => {
    create();
    expect(titles()).toEqual(['WOD 1', 'WOD 2', 'WOD 3', 'WOD 1']);
    expect(fixture.nativeElement.textContent).toContain('TODAY.SESSION.COMING');
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
