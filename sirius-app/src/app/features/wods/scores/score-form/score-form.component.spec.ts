import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {ScoreFormComponent} from './score-form.component';
import {WodScoresStore} from '../wod-scores.store';
import {UnitService} from '../../../../core/services/unit.service';
import {APP_ICONS} from '../../../../app.config';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';

describe('ScoreFormComponent', () => {
  let fixture: ComponentFixture<ScoreFormComponent>;
  let component: ScoreFormComponent;
  let wod: WritableSignal<any>;
  let editing: WritableSignal<ScoreResponse | 'new' | null>;
  let store: any;

  const init = (scoreType: string, edit: ScoreResponse | 'new' = 'new') => {
    wod.set({id: 7, scoreType, timeCapSeconds: 600});
    editing.set(edit);
    fixture = TestBed.createComponent(ScoreFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    wod = signal(null);
    editing = signal(null);
    store = {
      wod, editing,
      isSaving: signal(false),
      save: vi.fn().mockName('WodScoresStore.save'),
      close: vi.fn().mockName('WodScoresStore.close')
    };
    await TestBed.configureTestingModule({
      imports: [ScoreFormComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodScoresStore, useValue: store},
        {
          provide: UnitService,
          useValue: {
            massUnit: signal('KG'),
            convertScoreLoad: (v: number, u: string) => u === 'LBS' ? Math.round(v * 0.45359237 * 2) / 2 : v
          }
        }
      ]
    }).compileComponents();
  });

  it('should send a finishing time as minutes and seconds', () => {
    init('TIME');
    component.form.patchValue({minutes: 4, seconds: 32});
    component.onSubmit();

    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({
      wodId: 7, scaling: 'RX', timeCapped: false, timeMinutes: 4, timeSeconds: 32
    }));
  });

  it('should send the reps at the cap, without time, when the time cap is reached', () => {
    init('TIME');
    component.form.patchValue({timeCapped: true, minutes: 10});
    expect(component.isValueMissing()).toBe(true);

    component.form.patchValue({reps: 45});
    fixture.detectChanges();
    component.onSubmit();

    const sent = store.save.mock.calls[0][0];
    expect(sent).toMatchObject({timeCapped: true, reps: 45});
    expect(sent.timeMinutes).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('SCORE.FIELDS.REPS_AT_CAP');
  });

  it('should not submit while the value of the score type is missing', () => {
    init('REPS');
    component.onSubmit();
    expect(store.save).not.toHaveBeenCalled();
    expect((fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('should send a load in the user unit, and scaling notes only when not Rx', () => {
    init('WEIGHT');
    component.form.patchValue({load: 102.5, scaling: 'SCALED', scalingNotes: ' band ', userComment: ''});
    component.onSubmit();

    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({
      maxWeight: 102.5, weightUnit: 'KG', scaling: 'SCALED', scalingNotes: 'band', userComment: undefined
    }));
  });

  it('should prefill the form with the score being edited, loads in the user unit', () => {
    init('LOAD', {
      id: 3, userId: 1, date: '2026-10-20', wodSummary: {} as any, totalLoad: 225, weightUnit: 'LBS',
      scaling: 'ELITE', timeCapped: false, personalRecord: true, loggedAt: '', userComment: 'Heavy'
    });

    expect(component.isEdit()).toBe(true);
    expect(component.form.getRawValue()).toMatchObject({date: '2026-10-20', scaling: 'ELITE', load: 102, userComment: 'Heavy'});
  });

  it('should send rounds and reps for an AMRAP, and only the date for a WOD without result', () => {
    init('ROUNDS_REPS');
    component.form.patchValue({rounds: 12});
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({rounds: 12, reps: 0}));
  });

  it('should close without saving', () => {
    init('NONE');
    (fixture.nativeElement.querySelector('.modal-action .btn-ghost') as HTMLButtonElement).click();
    expect(store.close).toHaveBeenCalled();
    expect(component.isValueMissing()).toBe(false);
  });
});
