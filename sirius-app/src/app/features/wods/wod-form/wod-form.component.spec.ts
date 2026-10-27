import type {MockedObject} from 'vitest';
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {WodFormComponent} from './wod-form.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AccessService} from '../../../core/services/access.service';
import {UnitService} from '../../../core/services/unit.service';
import {APP_ICONS} from '../../../app.config';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';

describe('WodFormComponent', () => {
  let component: WodFormComponent;
  let fixture: ComponentFixture<WodFormComponent>;
  let router: Router;

  let mockWodService: MockedObject<WodService>;
  let mockMovementService: MockedObject<MovementService>;
  let mockNotificationService: MockedObject<NotificationService>;
  let writableGyms: WritableSignal<any[]>;

  const existingWod: WodResponse = {
    id: 8,
    title: 'Gym Fran',
    wodType: 'FOR_TIME',
    scoreType: 'TIME',
    authorId: 3,
    gymId: 5,
    isPublic: false, competition: false,
    timeCapSeconds: 425,
    repScheme: '21-15-9',
    modalities: [],
    capabilities: {canModify: true, locked: false, canJudge: false},
    movements: [
      {id: 2, movement: {id: 20, name: 'Pull-up'} as any, orderIndex: 2, repsScheme: '21-15-9'},
      {id: 1, movement: {id: 10, name: 'Thruster'} as any, orderIndex: 1, weight: 43, weightUnit: 'KG', equipment: ['BARBELL']}
    ],
    createdAt: '',
    updatedAt: ''
  };

  const setup = async (routeParamId: string | null, queryParams: Record<string, string> = {}) => {
    await TestBed.configureTestingModule({
      imports: [WodFormComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodService, useValue: mockWodService},
        {provide: MovementService, useValue: mockMovementService},
        {provide: UnitService, useValue: {massUnit: signal('KG')}},
        {
          provide: AccessService,
          useValue: {
            userId: signal(3),
            memberships: signal([{gymId: 5, gymName: 'Box'}, {gymId: 7, gymName: 'Zeus'}]),
            gymsWithPermission: () => writableGyms()
          }
        },
        {provide: NotificationService, useValue: mockNotificationService},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: {get: () => routeParamId}, queryParamMap: {get: (key: string) => queryParams[key] ?? null}}}
        }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(WodFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const fillValidForm = () => {
    component.wodForm.patchValue({title: '  Cindy ', wodType: 'AMRAP', scoreType: 'REPS'});
    component.addMovement();
    component.movementsFormArray.at(0).patchValue({movementId: 20, repsScheme: '5'});
  };

  beforeEach(() => {
    writableGyms = signal<any[]>([]);

    mockWodService = {
      getWod: vi.fn().mockName('WodService.getWod'),
      createWod: vi.fn().mockName('WodService.createWod'),
      updateWod: vi.fn().mockName('WodService.updateWod'),
      getReferenceData: vi.fn().mockName('WodService.getReferenceData'),
      notifyRefresh: vi.fn().mockName('WodService.notifyRefresh')
    } as any;
    mockWodService.getReferenceData.mockReturnValue(of({
      wodTypes: ['AMRAP', 'EMOM', 'FOR_TIME'],
      categories: ['GIRLS', 'HERO'],
      scoreTypes: ['TIME', 'REPS'],
      unitGroups: {MASS: ['KG', 'LBS'], DISTANCE: ['METERS'], TIME: ['SECONDS']}
    }));
    mockWodService.createWod.mockReturnValue(of({...existingWod, id: 42}));
    mockWodService.updateWod.mockReturnValue(of(existingWod));
    mockWodService.getWod.mockReturnValue(of(existingWod));

    mockMovementService = {
      getMovements: vi.fn().mockName('MovementService.getMovements'),
      getMovement: vi.fn().mockName('MovementService.getMovement')
    } as any;
    mockMovementService.getMovements.mockReturnValue(of([
      {id: 20, name: 'Pull-up', nameAbbreviation: undefined, category: 'PULL'},
      {id: 10, name: 'Air Squat', category: 'SQUAT'}
    ]));
    const details: Record<number, any> = {
      20: {id: 20, name: 'Pull-up', equipment: ['RINGS', 'PULL_UP_BAR'], techniques: ['STRICT', 'KIPPING'], targetedMuscles: []},
      10: {id: 10, name: 'Thruster', equipment: ['BARBELL', 'DUMBBELL'], techniques: [], targetedMuscles: []}
    };
    mockMovementService.getMovement.mockImplementation((id: number) => of(details[id]));

    mockNotificationService = {
      showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
      showError: vi.fn().mockName('NotificationService.showError')
    } as any;
  });

  describe('create mode', () => {
    beforeEach(async () => setup(null));

    it('should propose the usual score type of the format until the user picks one', () => {
      component.wodForm.controls.wodType.setValue('EMOM');
      expect(component.wodForm.controls.scoreType.value).toBe('ROUNDS');
      component.wodForm.controls.wodType.setValue('FOR_TIME');
      expect(component.wodForm.controls.scoreType.value).toBe('TIME');

      component.wodForm.controls.wodType.setValue('AMRAP');
      expect(component.wodForm.controls.scoreType.value).toBe('REPS');

      component.wodForm.controls.scoreType.setValue('CALORIES');
      component.wodForm.controls.scoreType.markAsDirty();
      component.wodForm.controls.wodType.setValue('FOR_TIME');
      expect(component.wodForm.controls.scoreType.value).toBe('CALORIES');
    });

    it('should suggest the reps per round from the movements and send it for a REPS WOD only', () => {
      component.wodForm.patchValue({wodType: 'AMRAP', scoreType: 'REPS'});
      component.addMovement();
      component.addMovement();
      component.movementsFormArray.at(0).patchValue({movementId: 1, repsScheme: '5', calories: null, distance: null});
      component.movementsFormArray.at(1).patchValue({movementId: 10, repsScheme: '', calories: 12});
      expect(component.suggestedRepsPerRound()).toBe(17);

      component.movementsFormArray.at(1).patchValue({repsScheme: '3-6-9...'});
      expect(component.suggestedRepsPerRound()).toBeNull();

      component.wodForm.patchValue({repsPerRound: 30});
      expect(component.buildPayload().repsPerRound).toBe(30);
      component.wodForm.patchValue({scoreType: 'TIME'});
      expect(component.buildPayload().repsPerRound).toBeUndefined();
    });

    it('should send the optional tiebreak texts', () => {
      component.wodForm.patchValue({tiebreakFr: ' Dernière série complète ', tiebreakEn: ''});
      expect(component.buildPayload()).toMatchObject({tiebreakFr: 'Dernière série complète', tiebreakEn: undefined});
    });

    it('should load reference data and sort the movements alphabetically', () => {
      expect(component.isEditMode()).toBe(false);
      expect(component.wodTypes()).toEqual(['AMRAP', 'EMOM', 'FOR_TIME']);
      expect(component.availableMovements().map(m => m.name)).toEqual(['Air Squat', 'Pull-up']);
      expect(component.massUnits()).toEqual(['KG', 'LBS']);
    });

    it('should not offer the gym visibility without WOD_WRITE anywhere', () => {
      expect(component.canTargetGym()).toBe(false);
      expect(component.wodForm.controls.visibility.value).toBe('PRIVATE');
    });

    it('should let anyone make a WOD public', () => {
      expect(fixture.nativeElement.querySelector('input[formcontrolname="isPublic"]')).toBeTruthy();
      fillValidForm();
      component.wodForm.patchValue({isPublic: true, competition: false});
      expect(component.buildPayload()).toMatchObject({isPublic: true, competition: false, authorId: 3});
    });

    it('should be invalid without any movement', () => {
      component.wodForm.patchValue({title: 'Cindy', wodType: 'AMRAP', scoreType: 'REPS'});
      expect(component.wodForm.valid).toBe(false);
      expect(component.movementsFormArray.errors).toEqual({minLengthArray: {required: 1}});

      component.onSubmit();
      expect(mockWodService.createWod).not.toHaveBeenCalled();
    });

    it('should create a personal WOD authored by the current user', () => {
      fillValidForm();
      component.wodForm.patchValue({timeCapMinutes: 20, timeCapSeconds: null});

      component.onSubmit();

      const payload = mockWodService.createWod.mock.lastCall![0];
      expect(payload).toMatchObject({
        title: 'Cindy',
        wodType: 'AMRAP',
        scoreType: 'REPS',
        authorId: 3,
        gymId: undefined,
        isPublic: false, competition: false,
        timeCapSeconds: 1200,
        emomInterval: undefined
      });
      expect(payload.movements).toEqual([expect.objectContaining({movementId: 20, orderIndex: 1, repsScheme: '5', weightUnit: undefined})]);
      expect(mockNotificationService.showSuccess).toHaveBeenCalledWith('WOD.MESSAGES.SUCCESS_CREATE');
      expect(mockWodService.notifyRefresh).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/wods', 42]);
    });

    it('should send a competition WOD for a gym only, never public', () => {
      fillValidForm();
      component.wodForm.patchValue({visibility: 'GYM', gymId: 5, isPublic: true, competition: true});
      expect(component.isCompetition()).toBe(true);
      expect(component.buildPayload()).toMatchObject({gymId: 5, competition: true, isPublic: false});

      // A personal WOD is never a competition WOD
      component.wodForm.patchValue({visibility: 'PRIVATE', gymId: null});
      expect(component.isCompetition()).toBe(false);
      expect(component.buildPayload()).toMatchObject({competition: false, isPublic: true});
    });

    it('should only send EMOM fields for an EMOM WOD', () => {
      fillValidForm();
      component.wodForm.patchValue({wodType: 'EMOM', emomInterval: 60, emomRounds: 10});
      expect(component.buildPayload()).toMatchObject({emomInterval: 60, emomRounds: 10});

      component.wodForm.patchValue({wodType: 'AMRAP'});
      expect(component.buildPayload()).toMatchObject({emomInterval: undefined, emomRounds: undefined});
    });

    it('should recompute orderIndex after reordering and removal', () => {
      fillValidForm();
      component.addMovement();
      component.movementsFormArray.at(1).patchValue({movementId: 10, weight: 60});
      component.addMovement();
      component.movementsFormArray.at(2).patchValue({movementId: 20});

      component.moveMovement(1, -1);
      expect(component.buildPayload().movements.map(m => [m.movementId, m.orderIndex])).toEqual([[10, 1], [20, 2], [20, 3]]);
      expect(component.buildPayload().movements[0]).toMatchObject({weight: 60, weightUnit: 'KG'});

      component.moveMovement(0, -1); // out of bounds: no-op
      component.removeMovement(1);
      expect(component.buildPayload().movements.map(m => [m.movementId, m.orderIndex])).toEqual([[10, 1], [20, 2]]);
    });

    it('should resolve the movement search box and offer only the allowed variants', () => {
      component.addMovement();
      const row = component.movementsFormArray.at(0);

      row.patchValue({movementQuery: 'pull-UP '});
      component.onMovementQuery(0);
      expect(row.value.movementId).toBe(20);
      expect(mockMovementService.getMovement).toHaveBeenCalledWith(20);
      expect(component.allowedVariants(0, 'equipment')).toEqual(['PULL_UP_BAR', 'RINGS']);
      expect(component.allowedVariants(0, 'techniques')).toEqual(['KIPPING', 'STRICT']);

      row.patchValue({movementQuery: 'Unknown move'});
      component.onMovementQuery(0);
      expect(row.value.movementId).toBeNull();
      expect(row.get('movementId')!.invalid).toBe(true);
      expect(component.allowedVariants(0, 'equipment')).toEqual([]);
    });

    it('should toggle variants, send them and clear them when the movement changes', () => {
      fillValidForm();
      const row = component.movementsFormArray.at(0);
      row.patchValue({movementQuery: 'Pull-up'});
      component.onMovementQuery(0); // same movement: nothing reset

      component.toggleVariant(0, 'equipment', 'RINGS');
      component.toggleVariant(0, 'techniques', 'STRICT');
      component.toggleVariant(0, 'techniques', 'KIPPING');
      component.toggleVariant(0, 'techniques', 'KIPPING');
      expect(component.isVariantSelected(0, 'equipment', 'RINGS')).toBe(true);
      expect(component.buildPayload().movements[0]).toMatchObject({equipment: ['RINGS'], techniques: ['STRICT']});

      row.patchValue({movementQuery: 'Air Squat'});
      component.onMovementQuery(0);
      expect(component.buildPayload().movements[0]).toMatchObject({movementId: 10, equipment: [], techniques: []});
    });

    it('should display the backend error on failure', () => {
      fillValidForm();
      mockWodService.createWod.mockReturnValue(throwError(() => ({status: 400, error: {detail: 'Invalid'}})));

      component.onSubmit();

      expect(mockNotificationService.showError).toHaveBeenCalledWith('Invalid');
      expect(component.isLoading()).toBe(false);
    });

    it('should display a dedicated message when the user is not allowed', () => {
      fillValidForm();
      mockWodService.createWod.mockReturnValue(throwError(() => ({status: 403})));

      component.onSubmit();

      expect(mockNotificationService.showError).toHaveBeenCalledWith('WOD.MESSAGES.FORBIDDEN');
    });
  });

  describe('create mode preset from the list button', () => {
    beforeEach(() => {
      writableGyms.set([{gymId: 5, gymName: 'Box'}, {gymId: 7, gymName: 'Zeus'}]);
    });

    it('should target the requested gym when the user may program it', async () => {
      await setup(null, {gymId: '7'});
      expect(component.wodForm.getRawValue()).toMatchObject({visibility: 'GYM', gymId: 7});
    });

    it('should fall back to the first writable gym for a gym the user cannot program', async () => {
      await setup(null, {gymId: '99'});
      expect(component.wodForm.getRawValue()).toMatchObject({visibility: 'GYM', gymId: 5});
    });

    it('should start a personal WOD when asked', async () => {
      await setup(null, {visibility: 'PRIVATE'});
      expect(component.wodForm.getRawValue()).toMatchObject({visibility: 'PRIVATE', gymId: null});
    });
  });

  describe('create mode as gym staff', () => {
    beforeEach(async () => {
      writableGyms.set([{gymId: 5, gymName: 'Box'}, {gymId: 7, gymName: 'Zeus'}]);
      await setup(null);
    });

    it('should default to the first gym where the user may program WODs', () => {
      expect(component.canTargetGym()).toBe(true);
      expect(component.wodForm.controls.visibility.value).toBe('GYM');

      fillValidForm();
      expect(component.buildPayload()).toMatchObject({gymId: 5, authorId: undefined});
    });

    it('should let the user target another writable gym', () => {
      fillValidForm();
      component.wodForm.patchValue({gymId: 7});
      expect(component.buildPayload()).toMatchObject({gymId: 7});
    });

    it('should require a gym for the gym visibility', () => {
      fillValidForm();
      component.wodForm.patchValue({gymId: null});
      expect(component.wodForm.errors).toEqual({gymRequired: true});
    });

    it('should allow switching back to a personal WOD', () => {
      fillValidForm();
      component.wodForm.patchValue({visibility: 'PRIVATE'});
      expect(component.buildPayload()).toMatchObject({gymId: undefined, authorId: 3});
    });
  });

  describe('edit mode', () => {
    beforeEach(async () => setup('8'));

    it('should patch the form and rebuild the movements in order', () => {
      expect(component.isEditMode()).toBe(true);
      expect(mockWodService.getWod).toHaveBeenCalledWith(8);
      expect(component.wodForm.getRawValue()).toMatchObject({
        title: 'Gym Fran',
        visibility: 'GYM',
        timeCapMinutes: 7,
        timeCapSeconds: 5,
        repScheme: '21-15-9'
      });
      expect(component.movementsFormArray.length).toBe(2);
      expect(component.movementsFormArray.at(0).value.movementId).toBe(10);
      expect(component.wodForm.controls.title.enabled).toBe(true);
      expect(component.wodForm.controls.isPublic.enabled).toBe(true);
    });

    it('should prefill the search box and drop stored variants the movement no longer allows', () => {
      expect(component.movementsFormArray.at(0).value.movementQuery).toBe('Thruster');
      mockMovementService.getMovement.mockReturnValueOnce(
        of({id: 99, name: 'Dip', equipment: ['RINGS'], techniques: [], targetedMuscles: []} as any));

      component.addMovement({id: 3, movement: {id: 99, name: 'Dip'} as any, orderIndex: 3, equipment: ['RINGS', 'BOX']});

      expect(component.movementsFormArray.at(2).value.equipment).toEqual(['RINGS']);
    });

    it('should lock the ownership fields', () => {
      expect(component.wodForm.controls.visibility.disabled).toBe(true);
      expect(component.wodForm.controls.gymId.disabled).toBe(true);
      expect(component.originalGymName()).toBe('Box');
    });

    it('should keep the original gym and preserve movement characteristics on update', () => {
      component.onSubmit();

      expect(mockWodService.updateWod).toHaveBeenCalled();
      const [id, payload] = mockWodService.updateWod.mock.lastCall!;
      expect(id).toBe(8);
      expect(payload).toMatchObject({gymId: 5, timeCapSeconds: 425});
      expect(payload.movements[0]).toMatchObject({movementId: 10, orderIndex: 1, weight: 43, weightUnit: 'KG', equipment: ['BARBELL']});
      expect(mockNotificationService.showSuccess).toHaveBeenCalledWith('WOD.MESSAGES.SUCCESS_UPDATE');
    });

    it('should send the category, the texts of both languages and the women load', () => {
      component.wodForm.patchValue({category: 'GIRLS', descriptionFr: ' Pour le temps ', descriptionEn: ''});
      component.movementsFormArray.at(0).patchValue({weightFemale: 30, notesFr: 'Prise large', scalingOptionsEn: 'PVC'});

      const payload = component.buildPayload();

      expect(payload).toMatchObject({category: 'GIRLS', descriptionFr: 'Pour le temps', descriptionEn: undefined});
      expect(payload.movements[0]).toMatchObject({weight: 43, weightFemale: 30, notesFr: 'Prise large', scalingOptionsEn: 'PVC'});
      expect(component.textControl('notes', 'FR')).toBe('notesFr');
      expect(component.textControl('scalingOptions', 'EN')).toBe('scalingOptionsEn');
    });

    it('should send the men/women calories and heights, without a height unit when no height is given', () => {
      expect(component.buildPayload().movements[0].heightUnit).toBeUndefined();

      component.movementsFormArray.at(0).patchValue({calories: 14, caloriesFemale: 12, height: 60, heightFemale: 50});

      expect(component.buildPayload().movements[0])
        .toMatchObject({calories: 14, caloriesFemale: 12, height: 60, heightFemale: 50, heightUnit: 'CENTIMETERS'});
    });

    it('should explain that a WOD with scores is locked (409)', () => {
      mockWodService.updateWod.mockReturnValue(throwError(() => ({status: 409, error: {}})));

      component.onSubmit();

      expect(mockNotificationService.showError).toHaveBeenCalledWith('WOD.MESSAGES.LOCKED');
    });
  });

  it('should leave the edit page when Aldebaran refuses modification', async () => {
    mockWodService.getWod.mockReturnValue(of({...existingWod, capabilities: {canModify: false, locked: false, canJudge: false}}));
    await setup('8');

    expect(mockNotificationService.showError).toHaveBeenCalledWith('WOD.MESSAGES.FORBIDDEN');
    expect(router.navigate).toHaveBeenCalledWith(['/wods', 8]);
    expect(component.movementsFormArray.length).toBe(0);
  });

  it('should leave the edit page when the WOD is locked', async () => {
    mockWodService.getWod.mockReturnValue(of({...existingWod, capabilities: {canModify: true, locked: true, canJudge: false}}));
    await setup('8');

    expect(mockNotificationService.showError).toHaveBeenCalledWith('WOD.MESSAGES.LOCKED');
    expect(router.navigate).toHaveBeenCalledWith(['/wods', 8]);
  });

  it('should go back to the list when the edited WOD cannot be loaded', async () => {
    mockWodService.getWod.mockReturnValue(throwError(() => ({status: 404, error: {detail: 'Not found'}})));
    await setup('8');

    expect(mockNotificationService.showError).toHaveBeenCalledWith('Not found');
    expect(router.navigate).toHaveBeenCalledWith(['/wods']);
  });
});
