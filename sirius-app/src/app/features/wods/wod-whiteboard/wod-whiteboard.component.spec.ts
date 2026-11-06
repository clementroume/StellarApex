import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {WodWhiteboardComponent} from './wod-whiteboard.component';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';
import {UnitService} from '../../../core/services/unit.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserResponse} from '../../../api/antares/models/user.model';
import {RecordService} from '../../../api/aldebaran/services/record.service';
import {of} from 'rxjs';

describe('WodWhiteboardComponent', () => {
  let component: WodWhiteboardComponent;
  let fixture: ComponentFixture<WodWhiteboardComponent>;

  const movement = (id: number, name: string): any => ({id, name, category: 'SQUAT', equipment: [], techniques: [], targetedMuscles: []});

  const buildWod = (overrides: Partial<WodResponse> = {}): WodResponse => ({
    id: 1,
    title: 'Fran',
    wodType: 'FOR_TIME',
    scoreType: 'TIME',
    authorId: 10,
    isPublic: false, competition: false,
    timeCapSeconds: 600,
    modalities: ['WEIGHTLIFTING'],
    movements: [
      {id: 12, movement: movement(2, 'Pull-up'), orderIndex: 2, repsScheme: '21-15-9'},
      {id: 11, movement: movement(1, 'Thruster'), orderIndex: 1, repsScheme: '21-15-9', weight: 43, weightUnit: 'KG'}
    ],
    createdAt: '',
    updatedAt: '',
    ...overrides
  });

  // Unit conversion is tested in UnitService: "43/30 KG", the user's division value marked
  const pair = (v?: number | null, f?: number | null, u?: string | null, division?: string | null) =>
    v == null && f == null ? null : {
      values: [v, f].filter(x => x != null).map(String),
      unit: u ?? '',
      mine: v != null && f != null && division ? (division === 'MEN' ? 0 : 1) : null
    };
  const currentUser = signal<Partial<UserResponse> | null>({division: null});
  const recordService = {getMyRecords: vi.fn().mockName('RecordService.getMyRecords'), version: signal(0)};

  const init = (wod: WodResponse = buildWod()) => {
    fixture = TestBed.createComponent(WodWhiteboardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('wod', wod);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WodWhiteboardComponent],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        {
          provide: UnitService,
          useValue: {
            formatDistance: (v?: number | null, f?: number | null, u?: string | null) =>
              v == null ? '' : `${v}${f ? '/' + f : ''} ${u}`,
            loadPair: pair,
            distancePair: pair,
            formatPercentLoad: (kg: number) => `${Math.round(kg)} kg`
          }
        },
        {provide: AuthService, useValue: {currentUser}},
        {provide: RecordService, useValue: recordService}
      ]
    }).compileComponents();
  });

  it('should load my records only for percentage loads, and write my load from the base version', () => {
    recordService.getMyRecords.mockReset();
    init();
    expect(recordService.getMyRecords).not.toHaveBeenCalled();

    recordService.getMyRecords.mockReturnValue(of([{movementId: 1, movementName: 'Thruster', variants: [
      {variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: 110,
        records: [{reps: 3, weightKg: 100, date: '2026-11-01', scoreId: 1, wodId: 1, wodTitle: 'W'}], estimates: []}
    ]}]));
    const wod = buildWod({wodType: 'FOR_LOAD', movements: [
      {id: 11, movement: movement(1, 'Thruster'), orderIndex: 1, repsScheme: '3', sets: 5, percentOfRm: 80, rmReps: 1},
      {id: 12, movement: movement(1, 'Thruster'), orderIndex: 2, repsScheme: '3', percentOfRm: 90, rmReps: 3},
      {id: 13, movement: movement(2, 'Pull-up'), orderIndex: 3, repsScheme: '5', percentOfRm: 50, rmReps: 1}
    ]});
    init(wod);

    expect(recordService.getMyRecords).toHaveBeenCalledTimes(1);
    expect(component.percentLoad(wod.movements[0])).toMatchObject({text: '88 kg', estimated: true, source: 'RECORDS.PERCENT.SOURCE_ESTIMATED'});
    expect(component.percentLoad(wod.movements[1])).toMatchObject({text: '90 kg', estimated: false, source: 'RECORDS.PERCENT.SOURCE'});
    expect(component.percentLoad(wod.movements[2])).toBeNull();
    // A variant line refers to the record of the base version, and says so
    expect(component.percentLoad({...wod.movements[1], techniques: ['PAUSED']})?.source).toBe('RECORDS.PERCENT.SOURCE_BASE');
    const percents = fixture.nativeElement.querySelectorAll('[data-slot="percent"]');
    expect(percents.length).toBe(2);
    expect(percents[0].textContent).toContain('≈ 88 kg');
    expect(fixture.nativeElement.querySelectorAll('[data-slot="percent-source"]').length).toBe(3);
    expect(fixture.nativeElement.querySelector('[data-slot="percent-no-record"]')).not.toBeNull();

    // A new score: the records are asked again
    recordService.version.set(1);
    fixture.detectChanges();
    expect(recordService.getMyRecords).toHaveBeenCalledTimes(2);
  });

  it('should list the movements by order', () => {
    init();
    expect(component.sortedMovements().map(m => m.movement.name)).toEqual(['Thruster', 'Pull-up']);
  });

  it('should write the prescribed sets before the reps', () => {
    const wod = buildWod({wodType: 'FOR_LOAD', structure: 'SETS', repScheme: '5x5'});
    init(wod);

    expect(component.quantity({...wod.movements[0], repsScheme: '5', sets: 5}, wod)).toBe('5 x 5');
    expect(component.quantity({...wod.movements[0], repsScheme: undefined, sets: 3}, wod)).toBe('3 x');
  });

  it('should write the format with its duration, and the time cap only when it is not the duration', () => {
    init();
    const amrap = buildWod({wodType: 'AMRAP', timeCapSeconds: 1200});

    expect(component.formatLine(amrap)).toBe('WOD.WOD_TYPES.AMRAP WOD.FIELDS.DURATION_MINUTES');
    expect(component.timeCapLabel(amrap)).toBe('');
    expect(component.formatLine({...amrap, timeCapSeconds: 425})).toBe("WOD.WOD_TYPES.AMRAP 7'05");
    expect(component.formatLine(buildWod())).toBe('WOD.WOD_TYPES.FOR_TIME');
    expect(component.timeCapLabel(buildWod())).toBe("WOD.FIELDS.TIME_CAP 10'");
    expect(component.timeCapLabel(buildWod({timeCapSeconds: undefined}))).toBe('');
    expect(component.formatLine(buildWod({wodType: 'EMOM', emomInterval: 60, emomRounds: 10})))
      .toBe('WOD.WOD_TYPES.EMOM WOD.FIELDS.DURATION_MINUTES');
    expect(component.formatLine(buildWod({wodType: 'EMOM', emomInterval: 120, emomRounds: 5})))
      .toBe('WOD.WOD_TYPES.EMOM 5 x 2:00');
  });

  it('should write the rep scheme only when neither the format nor the movements already say it', () => {
    init();
    const fran = buildWod({repScheme: '21-15-9'});

    expect(component.repSchemeLine(fran)).toBe('21-15-9');
    expect(component.repSchemeLine(buildWod({repScheme: '5 rounds'}))).toBe('5 rounds');
    expect(component.repSchemeLine(buildWod({wodType: 'AMRAP', repScheme: 'AMRAP 20 min'}))).toBe('');
    expect(component.repSchemeLine(buildWod({repScheme: 'For time'}))).toBe('');
    expect(component.repSchemeLine(buildWod({repScheme: '30 reps'}))).toBe('');
    expect(component.repSchemeLine(buildWod({repScheme: '1 mile / 100 / 200 / 300 / 1 mile'}))).toBe('');
    expect(component.formatLine(buildWod({wodType: 'TABATA', repScheme: 'Tabata x 4'}))).toBe('Tabata x 4');
    expect(component.formatLine(buildWod({repScheme: 'For time'}))).toBe('WOD.WOD_TYPES.FOR_TIME');
  });

  it('should omit the reps that repeat the rep scheme and write the other quantities first', () => {
    const wod = buildWod({repScheme: '21-15-9'});
    init(wod);

    expect(component.quantity({...wod.movements[0]}, wod)).toBe('');
    expect(component.quantity({...wod.movements[0], repsScheme: '50'}, wod)).toBe('50');
    expect(component.quantity({...wod.movements[0], repsScheme: 'max'}, wod)).toBe('Max');
    expect(component.quantity({...wod.movements[0], repsScheme: undefined, calories: 60}, wod)).toBe('60 cal');
    expect(component.quantity({...wod.movements[0], repsScheme: undefined, calories: 14, caloriesFemale: 12}, wod))
      .toBe('14/12 cal');
    expect(component.quantity({...wod.movements[0], repsScheme: undefined, distance: 400, distanceUnit: 'METERS'}, wod))
      .toBe('400 METERS');
    expect(component.quantity({...wod.movements[0], repsScheme: undefined, durationSeconds: 60}, wod)).toBe('1:00');
  });

  it('should write the men/women load and height after the movement, without "@"', () => {
    init();
    const thruster = {...component.sortedMovements()[0], weightFemale: 30};

    expect(component.measures(thruster)).toEqual([{values: ['43', '30'], unit: 'KG', mine: null}]);
    expect(component.measures({...thruster, weight: undefined, weightFemale: undefined, height: 24, heightFemale: 20, heightUnit: 'INCHES'}))
      .toEqual([{values: ['24', '20'], unit: 'INCHES', mine: null}]);
    expect(fixture.nativeElement.textContent).toContain('43 KG');
    expect(fixture.nativeElement.textContent).not.toContain('@');
  });

  it('should list the variants and the texts in the user language', () => {
    init();
    const thruster = {
      ...component.sortedMovements()[0],
      equipment: ['BARBELL'],
      techniques: ['TOUCH_AND_GO'],
      notesEn: 'Squat below parallel',
      notesFr: 'Squat sous la parallèle',
      scalingOptionsEn: 'Lighter barbell'
    };

    // Variants built into the name in CrossFit English, plural for 21-15-9
    expect(component.movementName(thruster, component.wod())).toBe('Touch-and-Go Barbell Thrusters');
    expect(fixture.nativeElement.querySelector('.movement-name').textContent.trim()).toBe('Thrusters');
    // No language loaded in tests: English, with the French text as fallback when English is missing
    expect(component.movementNotes(thruster)).toBe('Squat below parallel');
    expect(component.movementNotes({...thruster, notesEn: undefined})).toBe('Squat sous la parallèle');
    expect(component.scaling(thruster)).toBe('Lighter barbell');
  });

  it('should not number the movements on the board', () => {
    init();
    const board: HTMLElement = fixture.nativeElement;
    expect(board.querySelector('.badge-secondary')).toBeNull();
  });
  it('should write rounds, rest, vest and the buy-in / cash-out parts', () => {
    init(buildWod({
      title: 'Murph', rounds: 20, restSeconds: 120, vestWeight: 20, vestWeightFemale: 14, vestWeightUnit: 'LBS',
      movements: [
        {id: 1, movement: movement(6, 'Running'), orderIndex: 1, distance: 1600, distanceUnit: 'METERS', segment: 'BUY_IN'},
        {id: 2, movement: movement(2, 'Pull-up'), orderIndex: 2, repsScheme: '5'},
        {id: 3, movement: movement(6, 'Running'), orderIndex: 3, distance: 1600, distanceUnit: 'METERS', segment: 'BUY_OUT'}
      ]
    }));
    const board: HTMLElement = fixture.nativeElement;
    expect(board.querySelector('#format-line')?.textContent).toContain('WOD.BOARD.ROUNDS_FOR_TIME');
    expect(board.querySelector('#rest-badge')?.textContent).toContain('WOD.BOARD.REST');
    expect(board.querySelector('#vest-badge')?.textContent).toContain('20/14 LBS');
    expect([...board.querySelectorAll('[data-segment]')].map(e => e.getAttribute('data-segment'))).toEqual(['BUY_IN', 'BUY_OUT']);
    expect(board.querySelectorAll('ul').length).toBe(3);
    // Buy-in, then the format line and the rounds, then the cash-out
    const order = [...board.querySelectorAll('[data-segment], #format-line')].map(e => e.getAttribute('data-segment') ?? 'FORMAT');
    expect(order).toEqual(['BUY_IN', 'FORMAT', 'BUY_OUT']);
  });

  it('should write intervals, an ascending ladder and a Death by', () => {
    init(buildWod({wodType: 'TABATA', emomRounds: 8, workSeconds: 20, restSeconds: 10, timeCapSeconds: undefined}));
    expect(component.formatLine(component.wod())).toBe('WOD.WOD_TYPES.TABATA 8 x 20s / 10s');
    expect(component.restLabel(component.wod())).toBe('');
    expect(component.structureLine(buildWod({wodType: 'FOR_TIME', structure: 'LADDER', ladderStart: 3, ladderStep: 3}))).toBe('3-6-9-12…');
    expect(component.structureLine(buildWod({wodType: 'EMOM', structure: 'DEATH_BY', ladderStart: 1, ladderStep: 1})))
      .toBe('WOD.BOARD.DEATH_BY');
  });

  it('should show the team and how much each line asks from it', () => {
    init(buildWod({
      teamSize: 2, teamMode: 'YOU_GO_I_GO',
      movements: [{id: 1, movement: movement(2, 'Pull-up'), orderIndex: 1, repsScheme: '50', teamSplit: 'EACH'}]
    }));
    expect(fixture.nativeElement.querySelector('#team-badge').textContent).toContain('WOD.TEAM.OF');
    const [pullUp] = component.wod().movements;
    expect(component.quantity(pullUp, component.wod())).toBe('50');
    expect(component.teamSplit(pullUp, component.wod())).toBe('(WOD.BOARD.SPLITS.EACH)');
    expect(fixture.nativeElement.querySelector('.team-split').textContent.trim()).toBe('(WOD.BOARD.SPLITS.EACH)');
  });

  it('should write a carry done together as a partner carry, without a split', () => {
    const carry = {id: 2, movement: {...movement(3, 'Carry'), category: 'CARRY'}, orderIndex: 2, distance: 17,
      distanceUnit: 'METERS', equipment: ['BARBELL'], teamSplit: 'TOGETHER' as const};
    init(buildWod({teamSize: 2, movements: [carry]}));
    expect(component.movementName(carry, component.wod())).toBe('WOD.BOARD.SPLITS.PARTNER Barbell Carry');
    expect(component.teamSplit(carry, component.wod())).toBe('');
  });

  it('should keep the singular for one rep, a distance or calories', () => {
    init();
    const wod = buildWod({repScheme: undefined});
    const line = (repsScheme?: string, extra: object = {}) =>
      component.movementName({id: 1, movement: movement(1, 'Power Clean'), orderIndex: 1, repsScheme,
        techniques: ['HANG'], ...extra}, wod);
    expect(line('9')).toBe('Hang Power Cleans');
    expect(line('1')).toBe('Hang Power Clean');
    expect(line('1-1-1')).toBe('Hang Power Cleans');
    expect(line(undefined, {calories: 20})).toBe('Hang Power Clean');
  });
});
