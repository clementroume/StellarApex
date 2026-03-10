import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementFormComponent} from './movement-form.component';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';

import {provideIcons} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {APP_ICONS} from '../../../app.config';

describe('MovementFormComponent', () => {
  let component: MovementFormComponent;
  let fixture: ComponentFixture<MovementFormComponent>;
  let movementServiceSpy: jasmine.SpyObj<MovementService>;
  let muscleServiceSpy: jasmine.SpyObj<MuscleService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    movementServiceSpy = jasmine.createSpyObj('MovementService', ['getMovement', 'createMovement', 'updateMovement', 'getReferenceData']);
    movementServiceSpy.createMovement.and.returnValue(of({} as any));

    movementServiceSpy.getReferenceData.and.returnValue(of({
      categoryGroups: {'WEIGHTLIFTING': ['DEADLIFT']},
      equipmentGroups: {'NONE': ['BODYWEIGHT']},
      techniqueGroups: {'STYLE': ['STRICT']}
    } as any));

    muscleServiceSpy = jasmine.createSpyObj('MuscleService', ['getMuscles', 'getReferenceData']);
    muscleServiceSpy.getMuscles.and.returnValue(of([
      {id: 1, medicalName: 'Pectoralis Major', muscleGroup: 'CHEST'}
    ]));

    muscleServiceSpy.getReferenceData.and.returnValue(of({
      muscleRoles: ['AGONIST', 'SYNERGIST', 'STABILIZER'],
      muscleGroups: ['CHEST', 'BACK']
    } as any));

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MovementFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MovementService, useValue: movementServiceSpy},
        {provide: MuscleService, useValue: muscleServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: {get: () => null}}} // Simulates default "Create" mode
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component in create mode', () => {
    expect(component).toBeTruthy();
    expect(component.isEditMode()).toBeFalse();
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
      expect(component.isItemSelected('equipment', 'BARBELL')).toBeTrue();
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

  describe('Form Submission', () => {
    it('should not submit if the form is invalid', () => {
      component.onSubmit();
      expect(movementServiceSpy.createMovement).not.toHaveBeenCalled();
    });

    it('should call createMovement if valid', () => {
      // Fill mandatory fields
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
