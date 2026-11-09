import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementFormComponent} from './movement-form.component';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of} from 'rxjs';
import {provideIcons} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {APP_ICONS} from '../../../app.config';
import {provideTranslateService} from '@ngx-translate/core';

describe('MovementFormComponent', () => {
  let component: MovementFormComponent;
  let fixture: ComponentFixture<MovementFormComponent>;
  let movementServiceSpy: MockedObject<MovementService>;
  let muscleServiceSpy: MockedObject<MuscleService>;
  let notificationServiceSpy: MockedObject<NotificationService>;

  beforeEach(async () => {
    movementServiceSpy = {
      getMovement: vi.fn().mockName("MovementService.getMovement"),
      getMovements: vi.fn().mockName("MovementService.getMovements").mockReturnValue(of([])),
      createMovement: vi.fn().mockName("MovementService.createMovement"),
      updateMovement: vi.fn().mockName("MovementService.updateMovement"),
      getReferenceData: vi.fn().mockName("MovementService.getReferenceData")
    } as any;
    movementServiceSpy.createMovement.mockReturnValue(of({} as any));
    movementServiceSpy.updateMovement.mockReturnValue(of({} as any));

    movementServiceSpy.getReferenceData.mockReturnValue(of({
      categoryGroups: {'WEIGHTLIFTING': ['DEADLIFT']},
      equipmentGroups: {'NONE': ['BODYWEIGHT']},
      techniqueGroups: {'STYLE': ['STRICT'], 'RECEPTION': ['POWER', 'SQUAT']},
      exclusiveTechniqueSets: [['POWER', 'SQUAT']]
    } as any));

    movementServiceSpy.getMovement.mockReturnValue(of({
      id: 1,
      name: 'Loaded Squat',
      category: 'SQUAT',
      equipment: ['BARBELL'],
      techniques: [],
      targetedMuscles: [
        {muscle: {id: 1, medicalName: 'Quads'}, role: 'AGONIST', impactFactor: 1.0}
      ]
    } as any));
    movementServiceSpy.getMovements.mockReturnValue(of([
      {id: 2, name: 'Clean', category: 'CLEAN'}, {id: 3, name: 'Jerk', category: 'PRESS_AND_JERK'}
    ] as any));

    muscleServiceSpy = {
      getMuscles: vi.fn().mockName("MuscleService.getMuscles"),
      getReferenceData: vi.fn().mockName("MuscleService.getReferenceData")
    } as any;
    muscleServiceSpy.getMuscles.mockReturnValue(of([
      {id: 1, medicalName: 'Pectoralis Major', muscleGroup: 'CHEST'}
    ]));
    muscleServiceSpy.getReferenceData.mockReturnValue(of({
      muscleRoles: ['AGONIST', 'SYNERGIST', 'STABILIZER'],
      muscleGroups: ['CHEST', 'BACK']
    } as any));

    notificationServiceSpy = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;
  });

  const setupTestBed = async (routeParamId: string | null) => {
    await TestBed.configureTestingModule({
      imports: [MovementFormComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: MovementService, useValue: movementServiceSpy},
        {provide: MuscleService, useValue: muscleServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: {get: () => routeParamId}}}
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  };

  describe('Create Mode', () => {
    beforeEach(async () => {
      await setupTestBed(null);
    });

    it('should create the component in create mode', () => {
      expect(component).toBeTruthy();
      expect(component.isEditMode()).toBe(false);
    });

    it('should initialize the muscles catalog', () => {
      expect(muscleServiceSpy.getMuscles).toHaveBeenCalled();
      expect(component.availableMuscles().length).toBe(1);
    });

    describe('Tags Management (Chips)', () => {
      it('should add an equipment if it is not selected', () => {
        component.toggleSelection('equipment', 'BARBELL');
        const equipments = component.movementForm.get('equipment')?.value;
        expect(equipments).toContain('BARBELL');
        expect(component.isItemSelected('equipment', 'BARBELL')).toBe(true);
      });

      it('should remove an equipment if it is already selected', () => {
        component.movementForm.patchValue({equipment: ['BARBELL']});
        component.toggleSelection('equipment', 'BARBELL');
        const equipments = component.movementForm.get('equipment')?.value;
        expect(equipments).not.toContain('BARBELL');
      });
    });

    describe('Muscles FormArray Management', () => {
      it('should add an empty muscle line', () => {
        expect(component.musclesFormArray.length).toBe(0);
        component.addMuscle();
        expect(component.musclesFormArray.length).toBe(1);
        expect(component.musclesFormArray.at(0).get('role')?.value).toBe('AGONIST');
      });

      it('should delete a specific muscle line', () => {
        component.addMuscle();
        component.addMuscle();
        expect(component.musclesFormArray.length).toBe(2);

        component.removeMuscle(0);
        expect(component.musclesFormArray.length).toBe(1);
      });
    });

    describe('Composition', () => {
      const option = (movementId: number, nameEn: string) =>
        ({movementId, movementName: nameEn, category: 'CLEAN', nameEn, techniques: [], equipment: []});

      it('should write a step with the techniques its movement allows, one reception at a time', () => {
        movementServiceSpy.getMovement.mockReturnValue(of({id: 2, name: 'Clean', techniques: ['POWER', 'SQUAT']} as any));
        component.addComponent();
        component.onComponentPicked(0, option(2, 'Clean'));
        expect(component.componentTechniques(0)).toEqual(['POWER', 'SQUAT']);

        component.toggleComponentTechnique(0, 'POWER');
        component.toggleComponentTechnique(0, 'SQUAT');
        expect(component.componentsArray.at(0).value).toMatchObject({movementId: 2, techniques: ['SQUAT']});
      });

      it('should add accepted movements once, remove them and empty the search for the next one', () => {
        const key = component.acceptedPickerKey();
        component.addAccepted(option(3, 'Jerk'));
        component.addAccepted(option(3, 'Jerk'));
        expect(component.acceptedMovements().map(m => m.name)).toEqual(['Jerk']);
        expect(component.acceptedPickerKey()).toBeGreaterThan(key);

        component.removeAccepted(3);
        expect(component.acceptedMovements()).toEqual([]);
        expect(component.movementForm.controls.acceptedMovementIds.value).toEqual([]);
      });
    });

    describe('Form Submission', () => {
      it('should not submit if the form is invalid', () => {
        component.onSubmit();
        expect(movementServiceSpy.createMovement).not.toHaveBeenCalled();
      });

      it('should call createMovement if valid', () => {
        component.movementForm.patchValue({
          name: 'Bench Press',
          category: 'PUSHING'
        });
        component.onSubmit();

        expect(movementServiceSpy.createMovement).toHaveBeenCalled();
        expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
      });
    });
  });

  describe('Edit Mode', () => {
    beforeEach(async () => {
      await setupTestBed('1'); // Simule une URL du type /movements/edit/1
    });

    it('should initialize in edit mode and load movement data', () => {
      expect(component.isEditMode()).toBe(true);
      expect(movementServiceSpy.getMovement).toHaveBeenCalledWith(1);

      // Vérification des données de base
      expect(component.movementForm.get('name')?.value).toBe('Loaded Squat');
      expect(component.movementForm.get('equipment')?.value).toContain('BARBELL');

      // Vérification vitale : Le FormArray des muscles a bien été peuplé !
      expect(component.musclesFormArray.length).toBe(1);
      expect(component.musclesFormArray.at(0).get('muscleId')?.value).toBe(1);
      expect(component.musclesFormArray.at(0).get('role')?.value).toBe('AGONIST');
    });

    it('should call updateMovement on submit', () => {
      component.onSubmit();
      expect(movementServiceSpy.updateMovement).toHaveBeenCalled();
      expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
    });
  });
});
