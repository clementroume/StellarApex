import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {WodQuickFormComponent} from './wod-quick-form.component';
import {APP_ICONS} from '../../../app.config';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {UnitService} from '../../../core/services/unit.service';
import {WodRequest, WodResponse} from '../../../api/aldebaran/models/wod.model';

describe('WodQuickFormComponent', () => {
  let fixture: ComponentFixture<WodQuickFormComponent>;
  let component: WodQuickFormComponent;
  /** Picks a movement the way the search does: its usual name is the movement's name here. */
  const pick = (index: number, id: number) => {
    let list: {id: number; name: string; category: string}[] = [];
    movementService.getMovements().subscribe((l: typeof list) => list = l);
    const m = list.find(x => x.id === id)!;
    component.onPicked(index, {movementId: id, movementName: m.name, category: m.category, nameEn: m.name === 'Running' ? 'Run' : m.name, techniques: [], equipment: []});
  };
  const movementService = {
    getUsualNames: vi.fn().mockReturnValue(of([])),
    getReferenceData: vi.fn().mockReturnValue(of({categoryGroups: {}, equipmentGroups: {}, techniqueGroups: {SWING_STYLE: ['KIPPING', 'BUTTERFLY', 'STRICT']}, exclusiveTechniqueSets: []})),
    getMovements: vi.fn().mockReturnValue(of([
      {id: 1, name: 'Thruster', category: 'COMPLEXES'},
      {id: 2, name: 'Pull-up', category: 'PULLING'},
      {id: 3, name: 'Back Squat', category: 'SQUAT'},
      {id: 4, name: 'Rowing', category: 'CARDIO_MACHINES'},
      {id: 5, name: 'Wall-ball Shot', category: 'THROWS_AND_SLAMS'},
      {id: 6, name: 'Running', category: 'CARDIO'},
      {id: 7, name: 'Burpee', category: 'BURPEES'},
      {id: 8, name: 'American Kettlebell Swing', category: 'SWING'},
      {id: 9, name: 'Box Jump', category: 'JUMPING'},
      {id: 10, name: 'Snatch', category: 'SNATCH'}
    ])),
    getMovement: vi.fn().mockImplementation((id: number) =>
      of({id, name: 'M' + id, equipment: id === 2 ? ['PULL_UP_BAR', 'RINGS'] : id === 8 ? ['KETTLEBELL'] : id === 9 ? ['BOX'] : [], techniques: id === 2 ? ['KIPPING', 'STRICT'] : []}))
  };

  const init = (inputs: Record<string, unknown> = {}) => {
    fixture = TestBed.createComponent(WodQuickFormComponent);
    component = fixture.componentInstance;
    for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WodQuickFormComponent],
      providers: [provideTranslateService(), provideIcons(APP_ICONS),
        {provide: MovementService, useValue: movementService},
        {provide: UnitService, useValue: {unitSystem: signal('METRIC'), massUnit: signal('KG')}}]
    }).compileComponents();
  });

  it('should write Fran as a descending ladder: no reps per movement, only the load, title generated', () => {
    init();
    component.setCategory('FOR_TIME');
    component.setStructure('SCHEME');
    component.form.patchValue({minutes: 10});
    pick(0, 1);
    component.addRow();
    pick(1, 2);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('input[formControlName="reps"]').length).toBe(0);
    expect(component.extrasOf(0)).toEqual(['LOAD']);
    expect(component.extrasOf(1)).toEqual([]);
    component.form.controls.rows.at(0).patchValue({weight: 43, weightFemale: 29});

    const request = component.buildRequest();
    expect(request).toMatchObject({title: '21-15-9 Thruster / Pull-up', wodType: 'FOR_TIME', structure: 'SCHEME', scoreType: 'TIME',
      timeCapSeconds: 600, repScheme: '21-15-9', ladderStart: null});
    expect(request.movements[0]).toMatchObject({movementId: 1, repsScheme: '21-15-9', weight: 43, weightFemale: 29,
      weightUnit: 'KG', segment: 'MAIN'});
    expect(request.movements[1]).toMatchObject({movementId: 2, repsScheme: '21-15-9', weight: undefined});
  });

  it('should take sets and reps of a strength WOD from its sets field', () => {
    init({defaultFormat: 'FOR_LOAD'});
    expect(component.form.controls.scoreType.value).toBe('WEIGHT');
    component.setSets('5x5');
    pick(0, 3);
    component.form.controls.rows.at(0).patchValue({weight: 100});
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    component.onSubmit();

    const request: WodRequest = submitted.mock.calls[0][0];
    expect(request).toMatchObject({title: 'Back Squat 5x5', wodType: 'FOR_LOAD', structure: 'SETS', repScheme: '5x5', vestWeight: null});
    expect(request.movements[0]).toMatchObject({sets: 5, repsScheme: '5', weight: 100});
  });

  it('should write a For Load line as a percentage of the 1RM, and keep it when editing', () => {
    init({defaultFormat: 'FOR_LOAD'});
    component.setSets('5x3');
    pick(0, 3);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('.percent-input') as HTMLInputElement;
    expect(input).not.toBeNull();
    component.form.controls.rows.at(0).patchValue({percentOfRm: 80});
    expect(component.buildRequest().movements[0]).toMatchObject({sets: 5, repsScheme: '3', percentOfRm: 80, rmReps: 1});

    component.setCategory('AMRAP');
    expect(component.buildRequest().movements[0]).toMatchObject({percentOfRm: null, rmReps: null});

    const saved = {
      id: 31, title: 'Squat', wodType: 'FOR_LOAD', structure: 'SETS', scoreType: 'WEIGHT', isPublic: false, competition: false,
      repScheme: '5x3', modalities: [], createdAt: '', updatedAt: '',
      movements: [{id: 1, orderIndex: 1, movement: {id: 3, name: 'Back Squat'}, repsScheme: '3', sets: 5, percentOfRm: 85, rmReps: 3}]
    } as unknown as WodResponse;
    init({initial: saved});
    expect(component.buildRequest().movements[0]).toMatchObject({percentOfRm: 85, rmReps: 3});
  });

  it('should pick the measure of each movement, ask reps only where needed, and count the reps of a round', () => {
    init();
    component.setCategory('AMRAP');
    component.form.patchValue({minutes: 12});
    pick(0, 4);
    component.addRow();
    pick(1, 5);
    component.form.controls.rows.at(0).patchValue({calories: 15});
    component.form.controls.rows.at(1).patchValue({reps: '20', weight: 9, weightFemale: 6, height: 3, heightFemale: 2.7});
    fixture.detectChanges();

    expect(component.measureOfRow(0)).toBe('CALORIES');
    expect(fixture.nativeElement.querySelectorAll('input[formControlName="reps"]').length).toBe(1);
    expect(component.extrasOf(1)).toEqual(['LOAD', 'HEIGHT']);
    const request = component.buildRequest();
    expect(request).toMatchObject({wodType: 'AMRAP', structure: 'ROUNDS', scoreType: 'REPS', timeCapSeconds: 720, repsPerRound: 35});
    expect(request.movements[0]).toMatchObject({calories: 15, repsScheme: undefined});
    expect(request.movements[1]).toMatchObject({repsScheme: '20', heightUnit: 'CENTIMETERS'});
  });

  it('should write Murph: rounds, buy-in and cash-out runs, weight vest', () => {
    init();
    component.setCategory('FOR_TIME');
    component.form.patchValue({rounds: 20, minutes: 60});
    pick(0, 2);
    component.form.controls.rows.at(0).patchValue({reps: '5'});
    // The cash-out goes last, the buy-in first, a new line of the rounds before the cash-out
    component.addRow('BUY_OUT');
    component.addRow('BUY_IN');
    component.addRow();
    expect(component.form.controls.rows.controls.map(r => r.controls.segment.value))
      .toEqual(['BUY_IN', 'MAIN', 'MAIN', 'BUY_OUT']);
    component.removeRow(2);
    pick(0, 6);
    pick(2, 6);
    component.form.controls.rows.at(0).patchValue({distance: 1600});
    component.form.controls.rows.at(2).patchValue({distance: 1600});
    component.form.patchValue({vestWeight: 9, vestWeightFemale: 6});
    fixture.detectChanges();

    expect(component.measureOfRow(0)).toBe('DISTANCE');
    const headings = [...fixture.nativeElement.querySelectorAll('.segment-heading')] as HTMLElement[];
    expect(headings.map(h => h.dataset['segment'])).toEqual(['BUY_IN', 'MAIN', 'BUY_OUT']);
    const request = component.buildRequest();
    expect(request).toMatchObject({wodType: 'FOR_TIME', structure: 'ROUNDS', scoreType: 'TIME', rounds: 20, timeCapSeconds: 3600,
      vestWeight: 9, vestWeightFemale: 6, vestWeightUnit: 'KG', repScheme: undefined});
    expect(request.movements.map(m => m.segment)).toEqual(['BUY_IN', 'MAIN', 'BUY_OUT']);
    expect(request.movements[0]).toMatchObject({distance: 1600, distanceUnit: 'METERS', repsScheme: undefined});
    expect(request.title).toBe('WOD.QUICK.N_ROUNDS Running / Pull-up / Running');
  });

  it('should write an ascending ladder in reps and a Death by in rounds', () => {
    init();
    component.setCategory('AMRAP');
    component.setStructure('LADDER');
    component.form.patchValue({ladderStart: 3, ladderStep: 3, minutes: 12});
    pick(0, 7);
    expect(component.buildRequest()).toMatchObject({wodType: 'AMRAP', structure: 'LADDER', scoreType: 'REPS', ladderStart: 3,
      ladderStep: 3, repScheme: undefined, title: 'Burpee 3-6-9…'});

    component.setCategory('EMOM');
    component.setStructure('DEATH_BY');
    const request = component.buildRequest();
    expect(request).toMatchObject({wodType: 'EMOM', structure: 'DEATH_BY', scoreType: 'ROUNDS', ladderStart: 1, ladderStep: 1,
      emomInterval: 60, timeCapSeconds: undefined});
    expect(request.movements[0].repsScheme).toBeUndefined();
  });

  it('should preset a Tabata and turn an EMOM with work / rest into intervals', () => {
    init();
    component.setCategory('TABATA');
    pick(0, 7);
    expect(component.buildRequest()).toMatchObject({wodType: 'TABATA', scoreType: 'REPS', emomRounds: 8,
      workSeconds: 20, restSeconds: 10});

    component.setCategory('EMOM');
    expect(component.buildRequest()).toMatchObject({wodType: 'EMOM', scoreType: 'ROUNDS', emomRounds: 10,
      emomInterval: 60, workSeconds: null});
    component.setStructure('INTERVALS');
    component.form.patchValue({rounds: 5, workSeconds: 180, restSeconds: 60});
    expect(component.buildRequest()).toMatchObject({wodType: 'EMOM', structure: 'INTERVALS', scoreType: 'REPS', emomRounds: 5,
      emomInterval: undefined, workSeconds: 180, restSeconds: 60});
  });

  it('should fold equipment, technique and the other measures until asked', () => {
    init();
    pick(0, 2);
    fixture.detectChanges();
    const toggles = [...fixture.nativeElement.querySelectorAll('.variant-toggle')] as HTMLButtonElement[];
    expect(toggles.map(t => t.textContent)).toEqual([expect.stringContaining('WOD.QUICK.EQUIPMENT'),
      expect.stringContaining('WOD.QUICK.TECHNIQUE')]);
    toggles[1].click();
    fixture.detectChanges();
    expect(component.isVariantsOpen(0, 'techniques')).toBe(true);
    expect(component.isVariantsOpen(0, 'equipment')).toBe(false);
    const chips = [...fixture.nativeElement.querySelectorAll('[aria-pressed]')] as HTMLButtonElement[];
    chips.find(c => c.textContent?.includes('MOVEMENT.TECHNIQUES.KIPPING'))!.click();
    fixture.detectChanges();
    expect(component.buildRequest().movements[0].techniques).toEqual(['KIPPING']);
    expect(component.variantLabel(0, 'techniques')).toBe('MOVEMENT.TECHNIQUES.KIPPING');

    component.toggleExpanded(0);
    expect(component.extrasOf(0)).toEqual(['LOAD', 'HEIGHT']);
    // Buy-in / cash-out only on Rounds (the default For Time sub-category) and AMRAP
    expect(fixture.nativeElement.querySelector('#add-buy-in')).toBeTruthy();
    component.setStructure('SINGLE');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#add-buy-in')).toBeNull();
    expect(fixture.nativeElement.querySelector('#add-cash-out')).toBeNull();
  });

  it('should title a WOD with the usual names of its lines', () => {
    movementService.getUsualNames.mockReturnValueOnce(of([
      {movementId: 10, movementName: 'Snatch', category: 'SNATCH', nameEn: 'Snatch', techniques: [], equipment: []},
      {movementId: 10, movementName: 'Snatch', category: 'SNATCH', nameEn: 'Power Snatch', techniques: ['POWER'], equipment: []}
    ]));
    init();
    component.setCategory('FOR_TIME');
    component.setStructure('SCHEME');
    component.onPicked(0, {movementId: 10, movementName: 'Snatch', category: 'SNATCH', nameEn: 'Power Snatch', techniques: ['POWER'], equipment: []});
    expect(component.buildRequest().title).toBe('Power Snatch 21-15-9');
    expect(component.lineTechniques({techniques: ['HANG'], componentTechniques: [{componentOrder: 1, technique: 'POWER'}]}))
      .toEqual(['HANG', '1:POWER']);
  });

  it('should refuse a movement line without movement, and fill a saved WOD back', () => {
    init();
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    component.onSubmit();
    expect(submitted).not.toHaveBeenCalled();

    const saved = {
      id: 30, title: 'Back Squat 5x5', wodType: 'FOR_LOAD', structure: 'SETS', scoreType: 'WEIGHT', isPublic: false, competition: false,
      repScheme: '5x5', modalities: [], createdAt: '', updatedAt: '', notesFr: 'Tempo 3-1-1',
      movements: [{id: 1, orderIndex: 1, movement: {id: 3, name: 'Back Squat'}, repsScheme: '5', sets: 5, weight: 100, weightUnit: 'KG'}]
    } as unknown as WodResponse;
    init({initial: saved});
    expect(component.form.getRawValue()).toMatchObject({category: 'FOR_LOAD', structure: 'SETS', sets: '5x5', title: 'Back Squat 5x5'});
    expect(component.form.controls.rows.at(0).getRawValue()).toMatchObject({movementId: 3, weight: 100, reps: ''});
  });

  it('should read a former "5 rounds" For Time as Rounds, with its measures, parts and vest', () => {
    const saved = {
      id: 31, title: 'Murph', wodType: 'FOR_TIME', scoreType: 'TIME', isPublic: false, competition: false,
      repScheme: '20 rounds', vestWeight: 20, vestWeightFemale: 14, vestWeightUnit: 'LBS',
      modalities: [], createdAt: '', updatedAt: '',
      movements: [
        {id: 1, orderIndex: 1, movement: {id: 6, name: 'Running'}, distance: 1600, distanceUnit: 'METERS', segment: 'BUY_IN'},
        {id: 2, orderIndex: 2, movement: {id: 2, name: 'Pull-up'}, repsScheme: 'max'}
      ]
    } as unknown as WodResponse;
    init({initial: saved});
    expect(component.form.getRawValue()).toMatchObject({category: 'FOR_TIME', structure: 'ROUNDS', rounds: 20, vestWeight: 20});
    expect(component.form.controls.rows.at(0).getRawValue()).toMatchObject({measure: 'DISTANCE', segment: 'BUY_IN'});
    expect(component.measureOfRow(1)).toBe('MAX');
    expect(component.buildRequest().movements[1].repsScheme).toBe('max');
  });

  it('should group the lines of a complex and offer the sub-categories of the category', () => {
    init();
    component.setCategory('FOR_LOAD');
    fixture.detectChanges();
    const chips = [...fixture.nativeElement.querySelectorAll('[id^="structure-"]')].map((b: Element) => b.id);
    expect(chips).toEqual(['structure-SETS', 'structure-SCHEME', 'structure-MAX', 'structure-COMPLEX']);

    component.setStructure('COMPLEX');
    component.setSets('5x1');
    pick(0, 3);
    component.addRow();
    pick(1, 1);
    const request = component.buildRequest();
    expect(request).toMatchObject({wodType: 'FOR_LOAD', structure: 'COMPLEX', scoreType: 'WEIGHT', repScheme: '5x1'});
    expect(request.movements.map(m => m.complexGroup)).toEqual([1, 1]);
    expect(request.movements[0]).toMatchObject({sets: 5, repsScheme: '1'});
  });

  it('should keep Fran a For Time scheme and read a saved pyramid as a Ladder', () => {
    const pyramid = {
      id: 40, title: 'Pyramid', wodType: 'FOR_TIME', scoreType: 'TIME', isPublic: false, competition: false,
      repScheme: '1-2-3-2-1', modalities: [], createdAt: '', updatedAt: '',
      movements: [{id: 1, orderIndex: 1, movement: {id: 7, name: 'Burpee'}, repsScheme: '1-2-3-2-1'}]
    } as unknown as WodResponse;
    init({initial: pyramid});
    expect(component.form.getRawValue()).toMatchObject({category: 'FOR_TIME', structure: 'LADDER', ladder: '1-2-3-2-1'});
    expect(component.buildRequest()).toMatchObject({wodType: 'FOR_TIME', structure: 'LADDER', repScheme: '1-2-3-2-1'});
  });

  it('should write a team WOD: size, mode and each line in total, by each or together', () => {
    init();
    fixture.nativeElement.querySelector('#show-team').click();
    fixture.detectChanges();
    component.form.patchValue({teamSize: 3, teamMode: 'SPLIT'});
    pick(0, 2);
    component.form.controls.rows.at(0).patchValue({reps: '50', teamSplit: 'EACH'});

    const request = component.buildRequest();
    expect(request).toMatchObject({teamSize: 3, teamMode: 'SPLIT'});
    expect(request.movements[0].teamSplit).toBe('EACH');
    expect(fixture.nativeElement.querySelectorAll('.team-split-select').length).toBe(1);
  });
  it('should offer standard kettlebell and dumbbell weights and the three box heights', () => {
    init();
    pick(0, 8);
    component.addRow();
    pick(1, 9);
    component.addRow();
    pick(2, 10);
    fixture.detectChanges();

    // The implied equipment is picked at once
    expect(component.form.controls.rows.at(0).controls.equipment.value).toEqual(['KETTLEBELL']);
    expect(component.form.controls.rows.at(1).controls.equipment.value).toEqual(['BOX']);
    expect(component.loadChoices(0)).toEqual([4, 6, 8, 12, 16, 20, 24, 28, 32, 36, 40, 48]);
    expect(component.heightChoices(1)).toEqual([51, 61, 76]);
    // A barbell snatch keeps a free load, a dumbbell one gets the 2.5 kg steps
    expect(component.loadChoices(2)).toBeNull();
    component.toggleVariant(2, 'equipment', 'DUMBBELL');
    expect(component.loadChoices(2)?.slice(0, 3)).toEqual([2.5, 5, 7.5]);
    // A weight already written stays offered
    component.form.controls.rows.at(0).patchValue({weight: 22});
    expect(component.loadChoices(0)).toContain(22);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('select.load-preset').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('select.height-preset').length).toBe(2);
  });
});
