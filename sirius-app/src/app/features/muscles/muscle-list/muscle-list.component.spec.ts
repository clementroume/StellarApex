import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleListComponent} from './muscle-list.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of, Subject, throwError} from 'rxjs';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {DialogService} from '../../../core/services/dialog.service';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;

  let mockMuscleService: jasmine.SpyObj<MuscleService> & { refreshNeeded$: Subject<void> };
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockDialogService: jasmine.SpyObj<DialogService>;
  let mockExporterService: jasmine.SpyObj<ExporterService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  let mockCurrentUserSignal: WritableSignal<any>;

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
    mockMuscleService = jasmine.createSpyObj('MuscleService', ['getMuscles', 'getMuscle', 'getReferenceData']) as any;
    mockMuscleService.refreshNeeded$ = new Subject<void>();
    mockMuscleService.getMuscles.and.returnValue(of([mockMuscle as any]));
    mockMuscleService.getMuscle.and.returnValue(of(mockMuscle as any));
    mockMuscleService.getReferenceData.and.returnValue(of({
      muscleGroups: ['CHEST', 'BACK', 'LEGS'],
      muscleRoles: ['AGONIST', 'SYNERGIST']
    } as any));

    mockCurrentUserSignal = signal({platformRole: 'USER'});

    mockAuthService = jasmine.createSpyObj('AuthService', [], {
      currentUser: mockCurrentUserSignal
    });

    mockDialogService = jasmine.createSpyObj('DialogService', ['openMuscle']);
    mockExporterService = jasmine.createSpyObj('ExporterService', ['exportMuscles']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MuscleListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: DialogService, useValue: mockDialogService},
        {provide: ExporterService, useValue: mockExporterService},
        {provide: NotificationService, useValue: mockNotificationService}
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
    mockCurrentUserSignal.set({platformRole: 'USER'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
  });

  it('should display edit buttons for an admin user', () => {
    mockCurrentUserSignal.set({platformRole: 'ADMIN'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeTrue();
  });

  it('should cycle sorting correctly within a specific group (asc -> desc -> reset)', () => {
    component.sortBy('CHEST', 'medicalName');
    expect(component.sortStates()['CHEST'].column).toBe('medicalName');
    expect(component.sortStates()['CHEST'].direction).toBe('asc');

    component.sortBy('CHEST', 'medicalName');
    expect(component.sortStates()['CHEST'].direction).toBe('desc');

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

  it('should fetch muscle details and open global modal on openDetails', () => {
    component.openDetails(1);
    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith(1);
    expect(mockDialogService.openMuscle).toHaveBeenCalledWith(mockMuscle as any);
  });

  describe('exportCsv', () => {
    beforeEach(() => {
      spyOn(window, 'confirm');
    });

    it('should abort export if user cancels confirm dialog', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.exportCsv();
      expect(mockExporterService.exportMuscles).not.toHaveBeenCalled();
    });

    it('should call export service and show success notification if confirmed', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockExporterService.exportMuscles.and.returnValue(of('Export successful'));

      component.exportCsv();

      expect(mockExporterService.exportMuscles).toHaveBeenCalled();
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(component.isExporting()).toBeFalse();
    });

    it('should show error notification if export fails', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockExporterService.exportMuscles.and.returnValue(throwError(() => new Error('Export failed')));

      component.exportCsv();

      expect(mockExporterService.exportMuscles).toHaveBeenCalled();
      expect(mockNotificationService.showError).toHaveBeenCalled();
      expect(component.isExporting()).toBeFalse();
    });
  });
});
