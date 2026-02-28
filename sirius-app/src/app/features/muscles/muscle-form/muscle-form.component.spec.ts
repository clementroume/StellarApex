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
  let mockMuscleService: any;
  let mockNotificationService: any;
  let mockActivatedRoute: any;
  let router: Router;

  beforeEach(async () => {
    mockMuscleService = {
      getMuscle: jasmine.createSpy('getMuscle').and.returnValue(of({
        id: 1, medicalName: 'Test', muscleGroup: 'CHEST'
      })),
      createMuscle: jasmine.createSpy('createMuscle').and.returnValue(of({})),
      updateMuscle: jasmine.createSpy('updateMuscle').and.returnValue(of({}))
    };

    mockNotificationService = {
      showSuccess: jasmine.createSpy('showSuccess'),
      showError: jasmine.createSpy('showError')
    };

    mockActivatedRoute = {
      snapshot: {paramMap: {get: () => null}} // Par défaut : mode Création
    };

    await TestBed.configureTestingModule({
      imports: [
        MuscleFormComponent,
        TranslateModule.forRoot()
      ],
      providers: [
        // Utilisation du routeur de test fourni par Angular
        provideRouter([]),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: ActivatedRoute, useValue: mockActivatedRoute}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleFormComponent);
    component = fixture.componentInstance;
    // On récupère la vraie instance du routeur de test
    router = TestBed.inject(Router);
  });

  it('devrait initialiser en mode Création si pas d\'ID dans la route', () => {
    fixture.detectChanges();
    expect(component.isEditMode()).toBeFalse();
    expect(component.muscleForm.get('medicalName')?.value).toBe('');
  });

  it('devrait initialiser en mode Édition et charger les données si un ID est présent', () => {
    mockActivatedRoute.snapshot.paramMap.get = () => 'TestMuscle';
    fixture.detectChanges();

    expect(component.isEditMode()).toBeTrue();
    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith('TestMuscle');
    expect(component.muscleForm.get('medicalName')?.value).toBe('Test');
    expect(component.muscleForm.controls.medicalName.disabled).toBeTrue();
  });

  it('devrait empêcher la soumission si le formulaire est invalide', () => {
    fixture.detectChanges();
    component.onSubmit();
    expect(mockMuscleService.createMuscle).not.toHaveBeenCalled();
  });

  it('devrait appeler createMuscle à la soumission valide en mode Création', () => {
    // On pose un espion sur le vrai routeur
    const navigateSpy = spyOn(router, 'navigate');
    fixture.detectChanges();

    component.muscleForm.patchValue({
      medicalName: 'NewMuscle',
      muscleGroup: 'LEGS'
    });

    component.onSubmit();
    expect(mockMuscleService.createMuscle).toHaveBeenCalled();
    expect(mockNotificationService.showSuccess).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/muscles']);
  });
});
