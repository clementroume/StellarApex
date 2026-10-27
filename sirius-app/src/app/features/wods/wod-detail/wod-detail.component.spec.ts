import type {Mock, MockedObject} from 'vitest';
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {Location} from '@angular/common';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {WodDetailComponent} from './wod-detail.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AccessService} from '../../../core/services/access.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {UnitService} from '../../../core/services/unit.service';
import {APP_ICONS} from '../../../app.config';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';
import {ScoreService} from '../../../api/aldebaran/services/score.service';
import {UserService} from '../../../api/antares/services/user.service';
import {WodScoresStore} from '../scores/wod-scores.store';

describe('WodDetailComponent', () => {
  let component: WodDetailComponent;
  let fixture: ComponentFixture<WodDetailComponent>;
  let router: Router;

  let mockWodService: MockedObject<WodService>;
  let mockNotificationService: MockedObject<NotificationService>;
  let mockLocation: MockedObject<Location>;
  let mockScoreService: MockedObject<ScoreService>;

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
    capabilities: {canModify: true, locked: false, canJudge: false},
    movements: [
      {id: 12, movement: movement(2, 'Pull-up'), orderIndex: 2, repsScheme: '21-15-9'},
      {id: 11, movement: movement(1, 'Thruster'), orderIndex: 1, repsScheme: '21-15-9', weight: 43, weightUnit: 'KG'}
    ],
    createdAt: '',
    updatedAt: '',
    ...overrides
  });

  // Unit conversion is tested in UnitService: "43/30 KG"
  const pair = (v?: number | null, f?: number | null, u?: string | null) => v == null ? '' : `${v}${f ? '/' + f : ''} ${u}`;

  const init = (wod: WodResponse = buildWod()) => {
    mockWodService.getWod.mockReturnValue(of(wod));
    fixture = TestBed.createComponent(WodDetailComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
  };

  // Gyms where the user holds WOD_WRITE (copy targets)
  const writableGyms = vi.fn().mockName('AccessService.gymsWithPermission');

  beforeEach(async () => {
    writableGyms.mockReset();
    writableGyms.mockReturnValue([]);

    mockWodService = {
      getWod: vi.fn().mockName('WodService.getWod'),
      deleteWod: vi.fn().mockName('WodService.deleteWod'),
      copyWod: vi.fn().mockName('WodService.copyWod'),
      notifyRefresh: vi.fn().mockName('WodService.notifyRefresh')
    } as any;
    mockNotificationService = {
      showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
      showError: vi.fn().mockName('NotificationService.showError')
    } as any;
    mockLocation = {back: vi.fn().mockName('Location.back')} as any;
    mockScoreService = {
      getMyScores: vi.fn().mockName('ScoreService.getMyScores'),
      compareScore: vi.fn().mockName('ScoreService.compareScore'),
      getLeaderboard: vi.fn().mockName('ScoreService.getLeaderboard')
    } as any;
    mockScoreService.getLeaderboard.mockReturnValue(of({content: [], last: true} as any));
    mockScoreService.getMyScores.mockReturnValue(of({content: [], first: true, last: true, empty: true} as any));

    await TestBed.configureTestingModule({
      imports: [WodDetailComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {
          provide: AccessService,
          useValue: {
            memberships: signal([{gymId: 5, gymName: 'Box', gymRole: 'COACH', permissions: []}]),
            userId: signal(1),
            gymsWithPermission: writableGyms
          }
        },
        {provide: WodService, useValue: mockWodService},
        {
          provide: UnitService,
          useValue: {
            formatLoad: pair,
            formatDistance: pair
          }
        },
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation},
        {provide: ScoreService, useValue: mockScoreService},
        {provide: UserService, useValue: {getDisplayNames: vi.fn().mockReturnValue(of([]))}},
        {provide: GymService, useValue: {getAthletes: vi.fn().mockReturnValue(of([]))}}
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
  });

  it('should load the WOD and sort its movements by order', () => {
    init();
    expect(mockWodService.getWod).toHaveBeenCalledWith(1);
    expect(component.wod()?.title).toBe('Fran');
    expect(component.sortedMovements().map(m => m.movement.name)).toEqual(['Thruster', 'Pull-up']);
    expect(fixture.nativeElement.textContent).toContain('Thruster');
  });

  it('should notify and go back when the WOD cannot be loaded', () => {
    mockWodService.getWod.mockReturnValue(throwError(() => ({status: 403})));
    fixture = TestBed.createComponent(WodDetailComponent);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();

    expect(mockNotificationService.showError).toHaveBeenCalled();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  describe('visibility', () => {
    it('should be PUBLIC, GYM or PRIVATE', () => {
      init(buildWod({isPublic: true, competition: false}));
      expect(component.visibility()).toBe('PUBLIC');

      component.wod.set(buildWod({gymId: 5}));
      expect(component.visibility()).toBe('GYM');

      component.wod.set(buildWod());
      expect(component.visibility()).toBe('PRIVATE');
    });
  });

  describe('rights', () => {
    it('should show the actions when Aldebaran allows modification', () => {
      init();
      expect(component.canModify()).toBe(true);
      expect(fixture.nativeElement.querySelector('app-detail-admin-actions')).toBeTruthy();
    });

    it('should hide the actions when Aldebaran refuses modification', () => {
      init(buildWod({capabilities: {canModify: false, locked: false, canJudge: false}}));
      expect(component.canModify()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-detail-admin-actions')).toBeNull();
    });

    it('should hide the actions and show a badge when the WOD is locked', () => {
      init(buildWod({capabilities: {canModify: true, locked: true, canJudge: false}}));
      expect(component.canModify()).toBe(false);
      expect(component.isLocked()).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('WOD.MESSAGES.LOCKED_BADGE');
    });

    it('should hide the actions when capabilities are missing', () => {
      init(buildWod({capabilities: undefined}));
      expect(component.canModify()).toBe(false);
    });

    it('should display the gym name of a gym WOD', () => {
      init(buildWod({gymId: 5}));
      expect(component.gymName()).toBe('Box');
      expect(fixture.nativeElement.textContent).toContain('Box');
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      init();
      vi.spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as Mock).mockReturnValue(false);
      component.onDelete(component.wod()!);
      expect(mockWodService.deleteWod).not.toHaveBeenCalled();
    });

    it('should delete the WOD and navigate back to the list', () => {
      const navigateSpy = vi.spyOn(router, 'navigate');
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(of(undefined));

      component.onDelete(component.wod()!);

      expect(mockWodService.deleteWod).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockWodService.notifyRefresh).toHaveBeenCalled();
      expect(navigateSpy).toHaveBeenCalledWith(['/wods']);
    });

    it('should display the backend message when the WOD is locked (409)', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(throwError(() => ({status: 409, error: {detail: 'Locked!'}})));

      component.onDelete(component.wod()!);

      expect(mockNotificationService.showError).toHaveBeenCalledWith('Locked!');
    });

    it('should display a generic error otherwise', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(throwError(() => ({status: 500})));

      component.onDelete(component.wod()!);

      expect(mockNotificationService.showError).toHaveBeenCalledWith('GLOBAL.DELETE_ERROR');
    });
  });

  describe('whiteboard', () => {
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

    it('should show each information once: title alone, category and format in their own box', () => {
      init(buildWod({category: 'GIRLS', descriptionEn: 'Classic couplet'}));
      const text: string = fixture.nativeElement.textContent;
      const count = (key: string) => text.split(key).length - 1;

      expect(count('WOD.CATEGORIES.GIRLS')).toBe(1);
      expect(count('WOD.WOD_TYPES.FOR_TIME')).toBe(1);
      expect(count('WOD.FIELDS.TIME_CAP')).toBe(1);
      expect(text).not.toContain('WOD.SECTIONS.MOVEMENTS');
      expect(text).toContain('Classic couplet');
      expect(fixture.nativeElement.querySelector('#best-score')).toBeTruthy();
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

      expect(component.measures(thruster)).toEqual(['43/30 KG']);
      expect(component.measures({...thruster, weight: undefined, height: 24, heightFemale: 20, heightUnit: 'INCHES'}))
        .toEqual(['24/20 INCHES']);
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

      expect(component.variants(thruster)).toBe('MOVEMENT.EQUIPMENT.BARBELL, MOVEMENT.TECHNIQUES.TOUCH_AND_GO');
      // No language loaded in tests: English, with the French text as fallback when English is missing
      expect(component.movementNotes(thruster)).toBe('Squat below parallel');
      expect(component.movementNotes({...thruster, notesEn: undefined})).toBe('Squat sous la parallèle');
      expect(component.scaling(thruster)).toBe('Lighter barbell');
    });

    it('should show the official tiebreak only when the WOD has one', () => {
      init();
      expect(fixture.nativeElement.querySelector('#tiebreak')).toBeNull();

      init(buildWod({tiebreakEn: 'Time after the last full set'}));
      expect(fixture.nativeElement.querySelector('#tiebreak').textContent).toContain('Time after the last full set');
    });

    it('should not number the movements on the board', () => {
      init();
      const board: HTMLElement = fixture.nativeElement;
      expect(board.querySelector('.badge-secondary')).toBeNull();
    });
  });

  describe('scores', () => {
    const store = () => fixture.debugElement.injector.get(WodScoresStore);

    it('should load the user\'s scores on the WOD and invite to log one when there is none', () => {
      init();

      expect(mockScoreService.getMyScores).toHaveBeenCalledWith(1, 0, 50);
      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('SCORE.ACTIONS.LOG');
      expect(text).toContain('SCORE.MESSAGES.EMPTY');
      expect(fixture.nativeElement.querySelector('app-score-form')).toBeNull();
    });

    it('should open the score form from the history', () => {
      init();
      (fixture.nativeElement.querySelector('#log-score') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(store().editing()).toBe('new');
      expect(fixture.nativeElement.querySelector('app-score-form')).toBeTruthy();
    });

    it('should refresh the WOD (lock state) after a score change', () => {
      init();
      mockWodService.getWod.mockReturnValue(of(buildWod({capabilities: {canModify: true, locked: true, canJudge: false}})));

      store().changed$.next();
      fixture.detectChanges();

      expect(mockWodService.getWod).toHaveBeenCalledTimes(2);
      expect(component.isLocked()).toBe(true);
      expect(component.canModify()).toBe(false);
    });
  });

  describe('competition and copy', () => {
    it('should flag a competition copy: gym next to the title, link to the original, no copy button', () => {
      writableGyms.mockReturnValue([{gymId: 5, gymName: 'Box'}]);
      init(buildWod({gymId: 5, competition: true, sourceWodId: 3}));

      expect(fixture.nativeElement.querySelector('#competition-badge')).toBeTruthy();
      const header: HTMLElement = fixture.nativeElement.querySelector('app-detail-header');
      expect(header.querySelector('h1')?.textContent).toContain('(Box)');
      expect(header.querySelector('#source-wod')?.getAttribute('href')).toBe('/wods/3');
      expect(fixture.nativeElement.querySelector('#copy-wod')).toBeNull();
    });

    it('should offer the copy in the title bar, only to users programming a gym', () => {
      init();
      expect(fixture.nativeElement.querySelector('#copy-wod')).toBeNull();

      writableGyms.mockReturnValue([{gymId: 5, gymName: 'Box'}]);
      init();
      expect(fixture.nativeElement.querySelector('app-detail-header #copy-wod')).toBeTruthy();
    });

    it('should copy an Open WOD as a competition WOD of the gym, then open the copy', () => {
      writableGyms.mockReturnValue([{gymId: 5, gymName: 'Box'}]);
      mockWodService.copyWod.mockReturnValue(of(buildWod({id: 42, gymId: 5, competition: true})));
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      init(buildWod({isPublic: true, category: 'OPEN'}));

      (fixture.nativeElement.querySelector('#copy-wod') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(component.copyDraft()).toEqual({gymId: 5, competition: true});

      (fixture.nativeElement.querySelector('#copy-confirm') as HTMLButtonElement).click();

      expect(mockWodService.copyWod).toHaveBeenCalledWith(1, {gymId: 5, competition: true});
      expect(mockWodService.notifyRefresh).toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/wods', 42]);
    });

    it('should show the backend message when the copy fails', () => {
      writableGyms.mockReturnValue([{gymId: 5, gymName: 'Box'}]);
      mockWodService.copyWod.mockReturnValue(throwError(() => ({status: 400, error: {detail: 'No gym'}})));
      init();

      component.openCopy(component.wod()!);
      component.onCopy(component.wod()!);

      expect(mockNotificationService.showError).toHaveBeenCalledWith('No gym');
      expect(component.copyDraft()).not.toBeNull();
    });
  });
});
