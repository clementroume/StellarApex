import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleListComponent} from './muscle-list.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of, Subject} from 'rxjs';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;

  let mockMuscleService: any;
  let mockAuthService: any;
  let mockExporterService: any;
  let mockNotificationService: any;

  let mockCurrentUserSignal: WritableSignal<any>;

  const mockMuscles = [
    {
      id: 2,
      medicalName: 'Zygomaticus',
      commonNameFr: 'Zygomatique',
      commonNameEn: 'Zygomaticus',
      descriptionFr: 'Desc FR Z',
      descriptionEn: 'Desc EN Z',
      muscleGroup: 'CHEST'
    },
    {
      id: 1,
      medicalName: 'Pectoralis',
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest',
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN',
      muscleGroup: 'CHEST'
    }
  ];

  beforeEach(async () => {
    // 1. Mocking the MuscleService
    const muscleServiceSpyMethods = jasmine.createSpyObj('MuscleService', [
      'getMuscles',
      'getReferenceData',
      'toggleGroupExpansion'
    ]);

    mockMuscleService = {
      ...muscleServiceSpyMethods,
      refreshNeeded$: new Subject<void>(),
      savedExpandedGroups: signal(new Set<string>()),
      savedSearchQuery: signal('')
    };

    mockMuscleService.getMuscles.and.returnValue(of(mockMuscles));
    mockMuscleService.getReferenceData.and.returnValue(of({
      muscleGroups: ['CHEST', 'BACK', 'LEGS'],
      muscleRoles: ['AGONIST', 'SYNERGIST']
    }));

    // 2. Mocking Auth
    mockCurrentUserSignal = signal({platformRole: 'USER'});
    mockAuthService = {
      currentUser: mockCurrentUserSignal
    };

    // 3. Other mocks
    mockExporterService = jasmine.createSpyObj('ExporterService', ['exportMuscles']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MuscleListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: ExporterService, useValue: mockExporterService},
        {provide: NotificationService, useValue: mockNotificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges(); // Triggers ngOnInit
  });

  it('should create the component and load muscles bypassing the global loader', () => {
    expect(component).toBeTruthy();
    const callArgs = mockMuscleService.getMuscles.calls.mostRecent().args as any[];
    expect(callArgs[0]).toBeInstanceOf(HttpContext);
    expect(callArgs[0]?.get(BYPASS_LOADER)).toBeTrue();
  });

  it('should pre-initialize all groups even if they are empty', () => {
    const grouped = component.groupedMuscles();
    // LEGS and BACK were returned by getReferenceData but have no muscles in the mock data
    expect(grouped.has('LEGS')).toBeTrue();
    expect(grouped.get('LEGS')?.length).toBe(0);
    expect(grouped.has('BACK')).toBeTrue();
  });

  it('should filter muscles locally based on the search query (case-insensitive)', () => {
    // Test search by medical name
    component.searchQuery.set('zyg');
    expect(component.filteredMuscles().length).toBe(1);
    expect(component.filteredMuscles()[0].medicalName).toBe('Zygomaticus');

    // Test search by translated name (FR as default or fallback language)
    component.activeLang.set('fr');
    component.searchQuery.set('pectoraux');
    expect(component.filteredMuscles().length).toBe(1);
    expect(component.filteredMuscles()[0].medicalName).toBe('Pectoralis');
  });

  it('should update the searchQuery signal on input', () => {
    component.onSearchChange('biceps');
    expect(component.searchQuery()).toBe('biceps');
  });

  it('should hide edit buttons for a standard user', () => {
    mockCurrentUserSignal.set({platformRole: 'USER'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
  });
});
