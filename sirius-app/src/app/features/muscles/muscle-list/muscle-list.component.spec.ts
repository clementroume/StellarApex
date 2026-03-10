import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleListComponent} from './muscle-list.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of} from 'rxjs';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {DialogService} from '../../../core/services/dialog.service';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;
  let mockMuscleService: any;
  let mockAuthService: any;
  let mockDialogService: any;

  const mockMuscle = {
    id: 1,
    medicalName: 'Pectoralis',
    commonNameFr: 'Pectoraux',
    commonNameEn: 'Chest',
    descriptionFr: 'Desc FR',
    descriptionEn: 'Desc EN',
    muscleGroup: 'CHEST'
  };

  beforeEach(async () => {
    mockMuscleService = {
      getMuscles: jasmine.createSpy('getMuscles').and.returnValue(of([mockMuscle])),
      getMuscle: jasmine.createSpy('getMuscle').and.returnValue(of(mockMuscle)),
      getReferenceData: jasmine.createSpy('getReferenceData').and.returnValue(of({
        muscleGroups: ['CHEST', 'BACK', 'LEGS'],
        muscleRoles: ['AGONIST', 'SYNERGIST']
      }))
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'USER'})
    };

    mockDialogService = jasmine.createSpyObj('DialogService', ['openMuscle']);

    await TestBed.configureTestingModule({
      imports: [MuscleListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: DialogService, useValue: mockDialogService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component and load muscles', () => {
    expect(component).toBeTruthy();
    expect(mockMuscleService.getMuscles).toHaveBeenCalled();
    expect(mockMuscleService.getReferenceData).toHaveBeenCalled();
    expect(component.groupedMuscles().get('CHEST')?.length).toBe(1);
  });

  it('should hide edit buttons for a standard user', () => {
    mockAuthService.currentUser.set({platformRole: 'USER'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
  });

  it('should display edit buttons for an admin user', () => {
    mockAuthService.currentUser.set({platformRole: 'ADMIN'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeTrue();
  });

  it('should cycle sorting correctly within a specific group (asc -> desc -> reset)', () => {
    // 1st Click: Ascending
    component.sortBy('CHEST', 'medicalName');
    expect(component.sortStates()['CHEST'].column).toBe('medicalName');
    expect(component.sortStates()['CHEST'].direction).toBe('asc');

    // 2nd Click: Descending
    component.sortBy('CHEST', 'medicalName');
    expect(component.sortStates()['CHEST'].direction).toBe('desc');

    // 3rd Click: Reset (no sorting)
    component.sortBy('CHEST', 'medicalName');
    expect(component.sortStates()['CHEST']).toBeUndefined();
  });

  it('should return the correct localized name and description', () => {
    const muscle = component.groupedMuscles().get('CHEST')![0];

    component.activeLang.set('fr');
    expect(component.getLocalizedName(muscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(muscle)).toBe('Desc FR');

    component.activeLang.set('en');
    expect(component.getLocalizedName(muscle)).toBe('Chest');
    expect(component.getLocalizedDescription(muscle)).toBe('Desc EN');
  });

  it('should sort properly on the dynamic commonName within a specific group', () => {
    // 1st Click: Ascending
    component.sortBy('CHEST', 'commonName');
    expect(component.sortStates()['CHEST'].column).toBe('commonName');
    expect(component.sortStates()['CHEST'].direction).toBe('asc');
  });

  it('should fetch muscle details and open global modal on openDetails', () => {
    component.openDetails(1);

    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith(1);
    expect(mockDialogService.openMuscle).toHaveBeenCalledWith(mockMuscle);
  });
});
