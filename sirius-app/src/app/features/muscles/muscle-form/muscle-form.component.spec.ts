import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleFormComponent} from './muscle-form.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of} from 'rxjs';
import {provideTranslateService} from '@ngx-translate/core';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';


describe('MuscleFormComponent', () => {
  let component: MuscleFormComponent;
  let fixture: ComponentFixture<MuscleFormComponent>;
  let muscleServiceSpy: MockedObject<MuscleService>;
  let notificationServiceSpy: MockedObject<NotificationService>;
  let mockActivatedRoute: any;
  let router: Router;

  beforeEach(async () => {
    muscleServiceSpy = {
      getMuscle: vi.fn().mockName("MuscleService.getMuscle"),
      createMuscle: vi.fn().mockName("MuscleService.createMuscle"),
      updateMuscle: vi.fn().mockName("MuscleService.updateMuscle"),
      getReferenceData: vi.fn().mockName("MuscleService.getReferenceData")
    } as any;

    muscleServiceSpy.getMuscle.mockReturnValue(of({
      id: 1, medicalName: 'Test', muscleGroup: 'CHEST'
    } as any));

    muscleServiceSpy.createMuscle.mockReturnValue(of({} as any));
    muscleServiceSpy.updateMuscle.mockReturnValue(of({} as any));

    muscleServiceSpy.getReferenceData.mockReturnValue(of({
      muscleGroups: ['CHEST', 'BACK', 'LEGS', 'ARMS', 'SHOULDERS', 'CORE']
    } as any));

    notificationServiceSpy = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;

    mockActivatedRoute = {
      snapshot: {paramMap: {get: () => null}} // Default: Create mode
    };

    await TestBed.configureTestingModule({
      imports: [
        MuscleFormComponent
      ],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
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
    expect(component.isEditMode()).toBe(false);
    expect(component.muscleForm.get('medicalName')?.value).toBe('');
  });

  it('should initialize in Edit mode and load data if an ID is present', () => {
    mockActivatedRoute.snapshot.paramMap.get = () => '1';
    fixture.detectChanges();

    expect(component.isEditMode()).toBe(true);
    expect(muscleServiceSpy.getMuscle).toHaveBeenCalledWith(1 as any);
    expect(component.muscleForm.get('medicalName')?.value).toBe('Test');
  });

  it('should prevent submission if the form is invalid', () => {
    fixture.detectChanges();
    component.onSubmit();
    expect(muscleServiceSpy.createMuscle).not.toHaveBeenCalled();
  });

  it('should call createMuscle upon valid submission in Create mode', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
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
    const navigateSpy = vi.spyOn(router, 'navigate');
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
