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
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;

  let mockMuscleService: jasmine.SpyObj<MuscleService> & { refreshNeeded$: Subject<void> };
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockDialogService: jasmine.SpyObj<DialogService>;
  let mockExporterService: jasmine.SpyObj<ExporterService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  let mockCurrentUserSignal: WritableSignal<any>;

  const mockMuscles = [
    {
      id: 2,
      medicalName: 'Zygomaticus', // Un nom qui commence par Z (devrait être en 2ème après tri)
      commonNameFr: 'Zygomatique',
      commonNameEn: 'Zygomaticus',
      descriptionFr: 'Desc FR Z',
      descriptionEn: 'Desc EN Z',
      muscleGroup: 'CHEST'
    },
    {
      id: 1,
      medicalName: 'Pectoralis', // Un nom qui commence par P (devrait être en 1er après tri)
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest',
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN',
      muscleGroup: 'CHEST'
    }
  ];

  beforeEach(async () => {
    mockMuscleService = jasmine.createSpyObj('MuscleService', ['getMuscles', 'getMuscle', 'getReferenceData']) as any;
    mockMuscleService.refreshNeeded$ = new Subject<void>();
    // On simule le retour de l'API avec nos deux muscles
    mockMuscleService.getMuscles.and.returnValue(of(mockMuscles as any));
    mockMuscleService.getMuscle.and.returnValue(of(mockMuscles[1] as any)); // Retourne le Pectoralis par défaut pour les détails
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

  it('should create the component and load muscles with bypass loader context', () => {
    expect(component).toBeTruthy();

    // Vérifie que getMuscles a bien été appelé avec le HttpContext pour by-passer le loader global
    const callArgs = mockMuscleService.getMuscles.calls.mostRecent().args;
    expect(callArgs[0]).toBeInstanceOf(HttpContext);
    expect(callArgs[0]?.get(BYPASS_LOADER)).toBeTrue();

    expect(mockMuscleService.getReferenceData).toHaveBeenCalled();
    expect(component.groupedMuscles().get('CHEST')?.length).toBe(2);
  });

  it('should automatically sort muscles alphabetically by medicalName within their group', () => {
    const chestGroup = component.groupedMuscles().get('CHEST');
    expect(chestGroup).toBeDefined();

    // Le mock renvoyait [Zygomaticus, Pectoralis]
    // Après le calcul du composant, l'ordre devrait être [Pectoralis, Zygomaticus]
    expect(chestGroup![0].medicalName).toBe('Pectoralis');
    expect(chestGroup![1].medicalName).toBe('Zygomaticus');
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

  it('should return the correct localized name and description', () => {
    // On récupère le Pectoralis (le 1er de la liste triée)
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
    expect(mockDialogService.openMuscle).toHaveBeenCalledWith(mockMuscles[1] as any);
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
