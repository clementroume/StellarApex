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
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserResponse} from '../../../api/antares/models/user.model';
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

  // Unit conversion is tested in UnitService: "43/30 KG", the user's division value marked
  const pair = (v?: number | null, f?: number | null, u?: string | null, division?: string | null) =>
    v == null && f == null ? null : {
      values: [v, f].filter(x => x != null).map(String),
      unit: u ?? '',
      mine: v != null && f != null && division ? (division === 'MEN' ? 0 : 1) : null
    };
  const currentUser = signal<Partial<UserResponse> | null>({division: null});

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
            formatDistance: (v?: number | null, f?: number | null, u?: string | null) =>
              v == null ? '' : `${v}${f ? '/' + f : ''} ${u}`,
            loadPair: pair,
            distancePair: pair
          }
        },
        {provide: AuthService, useValue: {currentUser}},
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
    const text: string = fixture.nativeElement.textContent;
    expect(text.indexOf('Thruster')).toBeLessThan(text.indexOf('Pull-up'));
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

    it('should highlight the load of the user\'s division and mute the other one', () => {
      currentUser.set({division: 'WOMEN'});
      init(buildWod({
        movements: [{id: 11, movement: movement(1, 'Thruster'), orderIndex: 1, repsScheme: '21-15-9',
          weight: 43, weightFemale: 30, weightUnit: 'KG'}]
      }));

      const measure: HTMLElement = fixture.nativeElement.querySelector('[data-slot="measure"]');
      expect(measure.textContent?.replace(/\s+/g, ' ').trim()).toBe('43/30 KG');
      const [men, women] = Array.from(measure.querySelectorAll('span')) as HTMLElement[];
      expect(women.className).toContain('underline');
      expect(men.className).toContain('text-muted-foreground');
      currentUser.set({division: null});
    });

    it('should show the official tiebreak only when the WOD has one', () => {
      init();
      expect(fixture.nativeElement.querySelector('#tiebreak')).toBeNull();

      init(buildWod({tiebreakEn: 'Time after the last full set'}));
      expect(fixture.nativeElement.querySelector('#tiebreak').textContent).toContain('Time after the last full set');
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

  describe('competition and programming', () => {
    it('should flag a competition copy: gym next to the title, link to the original, nothing to program', () => {
      writableGyms.mockReturnValue([{gymId: 5, gymName: 'Box', gymProgramming: false}]);
      init(buildWod({gymId: 5, competition: true, sourceWodId: 77}));
      expect(fixture.nativeElement.querySelector('#source-wod').getAttribute('href')).toBe('/wods/77');
      expect(fixture.nativeElement.querySelector('#program-wod')).toBeNull();
      expect(fixture.nativeElement.querySelector('#competition-badge')).toBeTruthy();
    });

    it('should offer to program a WOD in a session to users programming a box or a program', () => {
      init(buildWod({isPublic: true}));
      expect(fixture.nativeElement.querySelector('#program-wod')).toBeNull();

      writableGyms.mockReturnValue([{gymId: 6, gymName: 'Zeus', gymProgramming: true}]);
      init(buildWod({isPublic: true}));
      expect(fixture.nativeElement.querySelector('#program-wod').getAttribute('href')).toBe('/box/sessions/new?borrow=1');
    });

    it('should edit a personal WOD with the quick editor of My training', () => {
      init(buildWod({gymId: undefined, isPublic: false}));
      expect(fixture.nativeElement.querySelector('app-detail-admin-actions a').getAttribute('href')).toBe('/training/wods/1/edit');
    });
  });
});
