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
import {AuthService} from '../../../../api/antares/services/auth.service';
import {MovementService} from '../../../../api/aldebaran/services/movement.service';
import {ScoreService} from '../../../../api/aldebaran/services/score.service';
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
      sessionLogId: signal(null),
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
        {provide: AuthService, useValue: {currentUser: signal({division: 'MEN'})}},
        {provide: MovementService, useValue: {getUsualNames: vi.fn().mockName('MovementService.getUsualNames').mockReturnValue(of([
          {movementId: 50, movementName: 'Rowing', category: 'CARDIO', nameEn: 'Row', techniques: [], equipment: []},
          {movementId: 60, movementName: 'Ring Row', category: 'PULLING', nameEn: 'Ring Row', techniques: [], equipment: []}]))}},
        {provide: ScoreService, useValue: {getReferenceData: vi.fn().mockName('ScoreService.getReferenceData').mockReturnValue(of({
          scalingLevels: ['RX', 'SCALED'],
          cardio: [{movementName: 'Running', meters: 1}, {movementName: 'Rowing', meters: 1.25, calories: 0.1}]}))}},
        {
          provide: UnitService,
          useValue: {
            massUnit: signal('KG'),
            unitSystem: signal('METRIC'),
            convertScoreLoad: (v: number, u: string) => u === 'LBS' ? Math.round(v * 0.45359237 * 2) / 2 : v,
            // Equivalents sent by Aldebaran, men's load for MEN
            lineLoadFor: (line: any) => line.equivalents?.weightKg ?? line.weight ?? null,
            lineLoadPair: (line: any) => line.equivalents?.weightKg != null
              ? {values: [String(line.equivalents.weightKg), String(line.equivalents.weightFemaleKg)], unit: 'kg', mine: 0} : null,
            lineHeightPair: () => null,
            formatDistance: (v: number | null) => v == null ? '' : `${v} m`
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

  it('should send a load in the user unit, a Scaled without any changed line', () => {
    init('WEIGHT');
    component.setLevel('SCALED');
    component.form.patchValue({load: 102.5, userComment: ''});
    component.onSubmit();

    const sent = store.save.mock.lastCall[0];
    expect(sent).toMatchObject({maxWeight: 102.5, weightUnit: 'KG', scaling: 'SCALED', userComment: undefined});
    expect(sent.lines).toBeUndefined();
  });

  it('should prefill the form with the score being edited, loads in the user unit', () => {
    init('LOAD', {
      id: 3, userId: 1, date: '2026-10-20', wodSummary: {} as any, totalLoad: 225, weightUnit: 'LBS',
      scaling: 'SCALED', timeCapped: false, personalRecord: true, loggedAt: '', userComment: 'Heavy'
    });

    expect(component.isEdit()).toBe(true);
    expect(component.form.getRawValue()).toMatchObject({date: '2026-10-20', level: 'SCALED', load: 102, userComment: 'Heavy'});
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

  it('should enter a strength score set by set, the best complete set as performance', () => {
    init('WEIGHT', 'new', {movements: [{id: 1, orderIndex: 1, repsScheme: '5', sets: 3, movement: {id: 1, name: 'Back Squat'}}]});
    (fixture.nativeElement.querySelector('.add-set') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(component.form.controls.sets.length).toBe(3);
    expect(component.form.controls.sets.at(0).getRawValue().reps).toBe(5);
    component.form.controls.sets.at(0).patchValue({weight: 100});
    component.form.controls.sets.at(1).patchValue({weight: 110});
    component.form.controls.sets.at(2).patchValue({weight: 120, completed: false});
    expect(component.setsValue()).toBe(110);
    expect(component.valueMissing()).toBe(false);

    component.onSubmit();
    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({
      sets: [{reps: 5, weight: 100, completed: true, wodMovementId: 1}, {reps: 5, weight: 110, completed: true, wodMovementId: 1},
        {reps: 5, weight: 120, completed: false, wodMovementId: 1}],
      weightUnit: 'KG'
    }));
    expect(store.save.mock.lastCall[0].maxWeight).toBeUndefined();
  });

  it('should let an athlete submit a competition score, never on another WOD', () => {
    init('TIME', 'new', {gymId: 5, competition: true, capabilities: {canModify: false, locked: false, canJudge: false}});
    const box = fixture.nativeElement.querySelector('#verification-requested input') as HTMLInputElement;
    box.click();
    component.form.patchValue({minutes: 8});
    component.onSubmit();
    expect(store.save).toHaveBeenLastCalledWith(expect.objectContaining({verificationRequested: true}));

    init('TIME');
    expect(fixture.nativeElement.querySelector('#verification-requested')).toBeNull();
  });

  it('should hide the date and the gym inside a session journal', () => {
    store.sessionLogId.set(12);
    init('REPS');
    expect(fixture.nativeElement.querySelector('input[type=date]')).toBeNull();
    expect(fixture.nativeElement.querySelector('#score-gym')).toBeNull();
    store.sessionLogId.set(null);
  });

  it('should let a member choose teammates of the box on a team WOD, at most the team size', () => {
    init('TIME', 'new', {gymId: 5, teamSize: 2});

    expect(gymService.getAthletes).toHaveBeenCalledWith(5);
    const chips = fixture.nativeElement.querySelectorAll('.teammate') as NodeListOf<HTMLButtonElement>;
    expect([...chips].map(c => c.textContent?.trim())).toEqual(['Anne L.']);
    component.toggleTeammate(9);
    component.toggleTeammate(11);
    component.toggleTeammate(12);
    expect(component.teammates()).toEqual([9, 11]);

    component.form.patchValue({minutes: 9, seconds: 0});
    component.onSubmit();
    expect(store.save).toHaveBeenCalledWith(expect.objectContaining({teammateIds: [9, 11]}));
  });

  it('should only offer boxes, never a program, for a team score on a public team WOD', () => {
    (TestBed.inject(AccessService) as any).memberships.set([
      {gymId: 8, gymName: 'Zeus', gymProgramming: true}, {gymId: 5, gymName: 'Box', gymProgramming: false}]);
    init('TIME', 'new', {teamSize: 2});

    expect(component.gymChoices().map(g => g.gymId)).toEqual([5]);
    expect(component.form.controls.gymId.value).toBe(5);
    expect(gymService.getAthletes).toHaveBeenCalledWith(5);
    expect(gymService.getAthletes).not.toHaveBeenCalledWith(8);
  });

  // --- Lot S: lines done, official Scaled version, sets per line ---

  const fran = {
    repsPerRound: null,
    movements: [
      {id: 10, orderIndex: 1, repsScheme: '21-15-9', weight: 95, weightFemale: 65, weightUnit: 'LBS',
        equipment: ['BARBELL'], techniques: [], movement: {id: 1, name: 'Thruster', equipment: ['BARBELL']},
        equivalents: {weightKg: 40, weightFemaleKg: 30},
        scaled: {movement: {id: 1, name: 'Thruster'}, equipment: ['BARBELL'], techniques: [], repsScheme: '21-15-9',
          weight: 65, weightFemale: 45, weightUnit: 'LBS', equivalents: {weightKg: 30, weightFemaleKg: 20}}},
      {id: 11, orderIndex: 2, repsScheme: '21-15-9', equipment: [], techniques: [], movement: {id: 2, name: 'Pull-up'},
        scaled: {movement: {id: 60, name: 'Ring Row'}, equipment: [], techniques: [], repsScheme: '21-15-9'}}
    ]
  };

  it('should offer three levels with an official Scaled version and prefill its lines', () => {
    init('TIME', 'new', fran);
    expect(fixture.nativeElement.querySelectorAll('#score-level button').length).toBe(3);

    component.setLevel('OFFICIAL');
    component.form.patchValue({minutes: 6, seconds: 0});
    component.onSubmit();

    const sent = store.save.mock.lastCall[0];
    expect(sent.scaling).toBe('SCALED');
    expect(sent.lines).toEqual([
      expect.objectContaining({wodMovementId: 10, movementId: 1, weight: 30, repsScheme: null}),
      expect.objectContaining({wodMovementId: 11, movementId: 60, weight: null})
    ]);
  });

  it('should change one line of a Scaled of one\'s own, the rest as prescribed; RX sends no line', () => {
    init('TIME', 'new', fran);
    expect(fixture.nativeElement.querySelectorAll('#score-level button').length).toBe(3);
    component.setLevel('SCALED');
    component.changeLine(1);
    component.onLinePicked(1, {movementId: 60, movementName: 'Ring Row', category: 'PULLING', nameEn: 'Ring Row', techniques: [], equipment: []});
    component.lineControls()[1].patchValue({reps: '15-12-9'});
    component.form.patchValue({minutes: 7, seconds: 0});
    component.onSubmit();
    expect(store.save.mock.lastCall[0].lines).toEqual([
      expect.objectContaining({wodMovementId: 11, movementId: 60, repsScheme: '15-12-9', weight: null})]);

    component.setLevel('RX');
    component.onSubmit();
    expect(store.save.mock.lastCall[0].lines).toBeUndefined();
    expect(store.save.mock.lastCall[0].scaling).toBe('RX');
  });

  it('should use the round as done when reps are reduced (Cindy at 3 pull-ups)', () => {
    init('REPS', 'new', {repsPerRound: 30, movements: [
      {id: 1, orderIndex: 1, repsScheme: '5', equipment: [], techniques: [], movement: {id: 2, name: 'Pull-up'}},
      {id: 2, orderIndex: 2, repsScheme: '25', equipment: [], techniques: [], movement: {id: 3, name: 'Squat'}}]});
    component.setLevel('SCALED');
    component.changeLine(0);
    component.lineControls()[0].patchValue({reps: '3'});
    component.form.patchValue({rounds: 10, reps: 5});
    expect(component.repsPerRound()).toBe(28);
    expect(component.conversion()?.total).toBe(285);
  });

  it('should count the reps of each max line and send their sum (Lynne)', () => {
    init('REPS', 'new', {movements: [
      {id: 1, orderIndex: 1, repsScheme: 'max', equipment: [], techniques: [], movement: {id: 4, name: 'Bench Press'}},
      {id: 2, orderIndex: 2, repsScheme: 'max', equipment: [], techniques: [], movement: {id: 2, name: 'Pull-up'}}]});
    component.lineControls()[0].patchValue({reps: '82'});
    component.lineControls()[1].patchValue({reps: '61'});
    fixture.detectChanges();
    expect(component.form.controls.reps.value).toBe(143);
    component.onSubmit();
    const sent = store.save.mock.lastCall[0];
    expect(sent).toMatchObject({scaling: 'RX', reps: 143});
    expect(sent.lines.map((l: any) => l.repsScheme)).toEqual(['82', '61']);
  });

  it('should enter the sets under each line, a LOAD score as the sum of each line\'s best (CrossFit Total)', () => {
    init('LOAD', 'new', {movements: [
      {id: 1, orderIndex: 1, repsScheme: '1', equipment: [], techniques: [], movement: {id: 5, name: 'Back Squat'}},
      {id: 2, orderIndex: 2, repsScheme: '1', equipment: [], techniques: [], movement: {id: 6, name: 'Shoulder Press'}}]});
    const lines = component.wodLines();
    component.addSet(lines[0]);
    component.addSet(lines[0]);
    component.addSet(lines[1]);
    const sets = component.form.controls.sets;
    sets.at(0).patchValue({weight: 120});
    sets.at(1).patchValue({weight: 130});
    sets.at(2).patchValue({weight: 65});
    expect(component.setsValue()).toBe(195);
    expect(component.setIndexes(lines[0])).toEqual([0, 1]);
    component.onSubmit();
    expect(store.save.mock.lastCall[0].sets.map((s: any) => s.wodMovementId)).toEqual([1, 1, 2]);
  });
});
