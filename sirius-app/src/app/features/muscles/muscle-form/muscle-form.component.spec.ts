import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleFormComponent} from './muscle-form.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of} from 'rxjs';
import {TranslateModule} from '@ngx-translate/core';

describe('MuscleFormComponent', () => {
  let component: MuscleFormComponent;
  let fixture: ComponentFixture<MuscleFormComponent>;
  let muscleServiceSpy: jasmine.SpyObj<MuscleService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;
  let mockActivatedRoute: any;
  let router: Router;

  beforeEach(async () => {
    muscleServiceSpy = jasmine.createSpyObj('MuscleService', [
      'getMuscle',
      'createMuscle',
      'updateMuscle',
      'getReferenceData'
    ]);

    muscleServiceSpy.getMuscle.and.returnValue(of({
      id: 1, medicalName: 'Test', muscleGroup: 'CHEST'
    } as any));

    muscleServiceSpy.createMuscle.and.returnValue(of({} as any));
    muscleServiceSpy.updateMuscle.and.returnValue(of({} as any));

    muscleServiceSpy.getReferenceData.and.returnValue(of({
      muscleGroups: ['CHEST', 'BACK', 'LEGS', 'ARMS', 'SHOULDERS', 'CORE']
    } as any));

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    mockActivatedRoute = {
      snapshot: {paramMap: {get: () => null}} // Default: Create mode
    };

    await TestBed.configureTestingModule({
      imports: [
        MuscleFormComponent,
        TranslateModule.forRoot()
      ],
      providers: [
        provideRouter([]),
        {provide: MuscleService, useValue: muscleServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy},
        {provide: ActivatedRoute, useValue: mockActivatedRoute}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleFormComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
  });

  it('should initialize in Create mode if no ID is present in the route', () => {
    fixture.detectChanges();
    expect(component.isEditMode()).toBeFalse();
    expect(component.muscleForm.get('medicalName')?.value).toBe('');
  });

  it('should initialize in Edit mode and load data if an ID is present', () => {
    mockActivatedRoute.snapshot.paramMap.get = () => '1';
    fixture.detectChanges();

    expect(component.isEditMode()).toBeTrue();
    expect(muscleServiceSpy.getMuscle).toHaveBeenCalledWith(1 as any);
    expect(component.muscleForm.get('medicalName')?.value).toBe('Test');
  });

  it('should prevent submission if the form is invalid', () => {
    fixture.detectChanges();
    component.onSubmit();
    expect(muscleServiceSpy.createMuscle).not.toHaveBeenCalled();
  });

  it('should call createMuscle upon valid submission in Create mode', () => {
    const navigateSpy = spyOn(router, 'navigate');
    fixture.detectChanges();

    component.muscleForm.patchValue({
      medicalName: 'NewMuscle',
      muscleGroup: 'LEGS'
    });

    component.onSubmit();
    expect(muscleServiceSpy.createMuscle).toHaveBeenCalled();
    expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/muscles']);
  });

  it('should call updateMuscle upon valid submission in Edit mode', () => {
    const navigateSpy = spyOn(router, 'navigate');
    mockActivatedRoute.snapshot.paramMap.get = () => '1';
    fixture.detectChanges();

    component.muscleForm.patchValue({
      medicalName: 'UpdatedMuscle'
    });

    component.onSubmit();

    expect(muscleServiceSpy.updateMuscle).toHaveBeenCalled();
    expect(muscleServiceSpy.createMuscle).not.toHaveBeenCalled();
    expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/muscles']);
  });
});
