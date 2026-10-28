import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {ScoreFormComponent} from './score-form.component';
import {WodScoresStore} from '../wod-scores.store';
import {UnitService} from '../../../../core/services/unit.service';
import {AccessService} from '../../../../core/services/access.service';
import {APP_ICONS} from '../../../../app.config';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {GymService} from '../../../../api/antares/services/gym.service';
import {of} from 'rxjs';

describe('ScoreFormComponent', () => {
  let fixture: ComponentFixture<ScoreFormComponent>;
  let component: ScoreFormComponent;
  let wod: WritableSignal<any>;
  let editing: WritableSignal<ScoreResponse | 'new' | null>;
  let store: any;
  let gymService: {getAthletes: ReturnType<typeof vi.fn>};

  const init = (scoreType: string, edit: ScoreResponse | 'new' = 'new', extra: object = {}) => {
    wod.set({id: 7, scoreType, timeCapSeconds: 600, ...extra});
    editing.set(edit);
    fixture = TestBed.createComponent(ScoreFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    wod = signal(null);
    editing = signal(null);
    gymService = {getAthletes: vi.fn().mockName('GymService.getAthletes')};
    gymService.getAthletes.mockReturnValue(of([{id: 2, displayName: 'Me M.'}, {id: 9, displayName: 'Anne L.'}]));
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
        {provide: AccessService, useValue: {userId: signal(2), memberships: signal([{gymId: 5, gymName: 'Box'}, {gymId: 8, gymName: 'Zeus'}])}},
        {provide: GymService, useValue: gymService},
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

  it('should send rounds + reps of an AMRAP with a fixed round, converted by Aldebaran', () => {
    init('REPS', 'new', {repsPerRound: 30});
    component.form.patchValue({rounds: 10, reps: 12});
    fixture.detectChanges();
    expect(component.conversion()?.total).toBe(312);
    expect(fixture.nativeElement.querySelector('#reps-conversion').textContent).toContain('SCORE.DISPLAY.REPS');
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({rounds: 10, reps: 12}));
  });

  it('should switch between rounds + reps and total reps, keeping the typed score', () => {
    init('REPS', 'new', {repsPerRound: 30});
    component.form.patchValue({rounds: 10, reps: 12});
    component.setRepsEntry('TOTAL');
    expect(component.form.getRawValue()).toMatchObject({rounds: null, reps: 312});
    expect(component.conversion()).toMatchObject({rounds: 10, reps: 12});
    component.onSubmit();
    const sent = store.save.mock.lastCall[0];
    expect(sent.reps).toBe(312);
    expect(sent.rounds).toBeUndefined();

    component.setRepsEntry('ROUNDS');
    expect(component.form.getRawValue()).toMatchObject({rounds: 10, reps: 12});
  });

  it('should ask for total reps only when the WOD has no fixed round, and split them when editing', () => {
    init('REPS');
    expect(fixture.nativeElement.textContent).not.toContain('SCORE.ENTRY.ROUNDS');
    expect(fixture.nativeElement.textContent).toContain('SCORE.FIELDS.TOTAL_REPS');

    init('REPS', {
      id: 4, userId: 1, date: '2026-10-20', wodSummary: {} as any, reps: 312, scaling: 'RX',
      timeCapped: false, personalRecord: true, loggedAt: ''
    }, {repsPerRound: 30});
    expect(component.form.getRawValue()).toMatchObject({rounds: 10, reps: 12});
  });

  it('should close without saving', () => {
    init('NONE');
    (Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.trim() === 'GLOBAL.CANCEL')!).click();
    expect(store.close).toHaveBeenCalled();
    expect(component.isValueMissing()).toBe(false);
  });

  it('should send the rounds on the minute of an EMOM, at most its rounds', () => {
    init('ROUNDS', 'new', {emomRounds: 30});
    component.form.patchValue({rounds: 31});
    expect(component.isValueMissing()).toBe(true);

    component.form.patchValue({rounds: 24});
    component.onSubmit();
    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({rounds: 24}));
    expect(fixture.nativeElement.textContent).toContain('SCORE.FIELDS.ROUNDS_ON_MINUTE');
  });

  it('should offer the optional tiebreak only for a WOD that has one, and only when capped on a For Time', () => {
    init('TIME', 'new', {tiebreakEn: 'Last full set'});
    expect(component.showTiebreak()).toBe(false);

    component.form.patchValue({timeCapped: true, reps: 180, tiebreakMinutes: 18, tiebreakSeconds: 25});
    fixture.detectChanges();
    expect(component.showTiebreak()).toBe(true);
    expect(fixture.nativeElement.querySelector('#tiebreak').textContent).toContain('Last full set');
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({reps: 180, tiebreakSeconds: 1105}));
  });

  it('should never require nor send a tiebreak that is left empty, and none for a WOD without one', () => {
    init('REPS', 'new', {tiebreakFr: 'Dernière série'});
    expect(component.showTiebreak()).toBe(true);
    component.form.patchValue({reps: 200});
    component.onSubmit();
    expect(store.save.mock.lastCall[0].tiebreakSeconds).toBeUndefined();
  });

  it('should send the chosen gym of a public WOD, and no gym for a gym WOD', () => {
    init('REPS');
    expect(component.form.controls.gymId.value).toBe(5);
    expect(fixture.nativeElement.querySelector('#score-gym')).toBeTruthy();
    component.form.patchValue({reps: 100, gymId: 8});
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({gymId: 8}));

    init('REPS', 'new', {gymId: 42});
    expect(fixture.nativeElement.querySelector('#score-gym')).toBeNull();
    component.form.patchValue({reps: 100});
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({gymId: null}));
  });

  it('should let the judge of a competition WOD log a score for a gym member', () => {
    init('TIME', 'new', {gymId: 5, competition: true, capabilities: {canModify: false, locked: false, canJudge: true}});

    expect(gymService.getAthletes).toHaveBeenCalledWith(5);
    expect(component.athletes()).toEqual([{id: 9, displayName: 'Anne L.'}]);
    expect(fixture.nativeElement.querySelector('#score-athlete')).not.toBeNull();

    component.form.patchValue({athleteId: 9, minutes: 8, seconds: 0});
    component.onSubmit();
    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({userId: 9, wodId: 7}));
  });

  it('should not offer the athlete choice to a non-judge, nor when editing', () => {
    init('TIME', 'new', {gymId: 5, competition: true, capabilities: {canModify: false, locked: false, canJudge: false}});
    expect(fixture.nativeElement.querySelector('#score-athlete')).toBeNull();
    expect(gymService.getAthletes).not.toHaveBeenCalled();

    component.form.patchValue({minutes: 8});
    component.onSubmit();
    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({userId: undefined}));
  });

  it('should use the only gym of the user without asking', () => {
    (TestBed.inject(AccessService) as any).memberships.set([{gymId: 5, gymName: 'Box'}]);
    init('REPS');

    expect(fixture.nativeElement.querySelector('#score-gym')).toBeNull();
    component.form.patchValue({reps: 100});
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({gymId: 5}));
  });
});
