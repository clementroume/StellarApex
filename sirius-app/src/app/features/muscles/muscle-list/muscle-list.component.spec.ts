import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleListComponent} from './muscle-list.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of, Subject} from 'rxjs';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {APP_ICONS} from '../../../app.config';
import {ThemeService} from '../../../core/services/theme.service';
import {provideIcons} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {vi} from 'vitest';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;
  let translateService: TranslateService;

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
      region: 'THORAX'
    },
    {
      id: 1,
      medicalName: 'Pectoralis',
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest',
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN',
      region: 'THORAX'
    }
  ];

  beforeEach(async () => {
    // 1. Mocking the MuscleService
    const muscleServiceSpyMethods = {
      getMuscles: vi.fn().mockName("MuscleService.getMuscles"),
      getReferenceData: vi.fn().mockName("MuscleService.getReferenceData")
    };

    mockMuscleService = {
      ...muscleServiceSpyMethods,
      refreshNeeded$: new Subject<void>(),
      savedRegion: signal('THORAX'),
      savedSearchQuery: signal('')
    };

    mockMuscleService.getMuscles.mockReturnValue(of(mockMuscles));
    mockMuscleService.getReferenceData.mockReturnValue(of({
      regions: ['THORAX', 'BACK', 'THIGH_LEG'],
      muscleRoles: ['AGONIST', 'SYNERGIST']
    }));

    // 2. Mocking Auth
    mockCurrentUserSignal = signal({platformRole: 'USER'});
    mockAuthService = {
      currentUser: mockCurrentUserSignal
    };

    // 3. Other mocks
    mockExporterService = {
      exportMuscles: vi.fn().mockName("ExporterService.exportMuscles")
    };
    mockNotificationService = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    };

    await TestBed.configureTestingModule({
      imports: [MuscleListComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: ThemeService, useValue: {currentTheme: signal('dark')}},
        {provide: AuthService, useValue: mockAuthService},
        {provide: ExporterService, useValue: mockExporterService},
        {provide: NotificationService, useValue: mockNotificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleListComponent);
    component = fixture.componentInstance;
    translateService = TestBed.inject(TranslateService);

    fixture.detectChanges(); // Triggers ngOnInit
  });

  it('should create the component and load muscles bypassing the global loader', () => {
    expect(component).toBeTruthy();
    const callArgs = vi.mocked(mockMuscleService.getMuscles).mock.lastCall as any[];
    expect(callArgs[0]).toBeInstanceOf(HttpContext);
    expect(callArgs[0]?.get(BYPASS_LOADER)).toBe(true);
  });

  it('should pre-initialize all groups even if they are empty', () => {
    const grouped = component.groupedMuscles();
    // LEGS and BACK were returned by getReferenceData but have no muscles in the mock data
    expect(grouped.has('THIGH_LEG')).toBe(true);
    expect(grouped.get('THIGH_LEG')?.length).toBe(0);
    expect(grouped.has('BACK')).toBe(true);
  });

  it('should list each functional group followed by its members, and keep the group for a member match', () => {
    const quads = {id: 10, medicalName: 'Quadriceps Femoris', region: 'THIGH_LEG', parent: null,
      members: [{id: 12, medicalName: 'Vastus Lateralis'}, {id: 11, medicalName: 'Rectus Femoris'}]};
    component.rawMuscles.set([
      {id: 12, medicalName: 'Vastus Lateralis', region: 'THIGH_LEG', parent: {id: 10, medicalName: 'Quadriceps Femoris'}},
      quads,
      {id: 11, medicalName: 'Rectus Femoris', region: 'THIGH_LEG', parent: {id: 10, medicalName: 'Quadriceps Femoris'}},
      {id: 13, medicalName: 'Tibialis Anterior', region: 'THIGH_LEG'}
    ] as any);

    expect(component.groupedMuscles().get('THIGH_LEG')!.map(r => [r.muscle.medicalName, r.isMember])).toEqual([
      ['Quadriceps Femoris', false], ['Rectus Femoris', true], ['Vastus Lateralis', true], ['Tibialis Anterior', false]
    ]);

    component.searchQuery.set('vastus');
    expect(component.groupedMuscles().get('THIGH_LEG')!.map(r => r.muscle.medicalName))
      .toEqual(['Quadriceps Femoris', 'Vastus Lateralis']);
  });

  it('should sort the rows on the name shown, not the medical name', () => {
    component.rawMuscles.set([
      {id: 1, medicalName: 'A Medical', commonNameEn: 'Zed', region: 'THIGH_LEG'},
      {id: 2, medicalName: 'Z Medical', commonNameEn: 'Abs', region: 'THIGH_LEG'}
    ] as any);
    translateService.use('en');
    expect(component.groupedMuscles().get('THIGH_LEG')!.map(r => r.muscle.id)).toEqual([2, 1]);
  });

  it('should filter muscles locally based on the search query (case-insensitive)', () => {
    // Test search by medical name
    component.searchQuery.set('zyg');
    expect(component.filteredMuscles().length).toBe(1);
    expect(component.filteredMuscles()[0].medicalName).toBe('Zygomaticus');

    // Test search by translated name (FR as default or fallback language)
    translateService.use('fr');
    fixture.detectChanges();
    component.searchQuery.set('PÉCTORAUX');
    expect(component.filteredMuscles().length).toBe(1);
    expect(component.filteredMuscles()[0].medicalName).toBe('Pectoralis');
  });

  it('should show the region picked, and every region while searching', () => {
    expect(component.shownRows().map(r => r.muscle.id)).toEqual([1, 2]);
    component.activeRegion.set('THIGH_LEG');
    expect(component.shownRows()).toEqual([]);
    component.onSearchChange('pect');
    fixture.detectChanges();
    expect(component.shownRows().map(r => r.muscle.medicalName)).toEqual(['Pectoralis']);
    expect(fixture.nativeElement.querySelector('[data-slot="region-scene"]')).toBeNull();
  });

  it('should show only the groups of a region, and their members while searching', () => {
    component.rawMuscles.set([
      {id: 10, medicalName: 'Quadriceps Femoris', region: 'THIGH_LEG', members: [{id: 11, medicalName: 'Rectus Femoris'}]},
      {id: 11, medicalName: 'Rectus Femoris', region: 'THIGH_LEG', parent: {id: 10, medicalName: 'Quadriceps Femoris'}}
    ] as any);
    component.activeRegion.set('THIGH_LEG');
    expect(component.shownRows().map(r => r.muscle.id)).toEqual([10]);

    component.onSearchChange('rectus');
    expect(component.shownRows().map(r => r.muscle.id)).toEqual([10, 11]);
  });

  it('should update the searchQuery signal on input', () => {
    component.onSearchChange('biceps');
    expect(component.searchQuery()).toBe('biceps');
  });

  it('should hide edit buttons for a standard user', () => {
    mockCurrentUserSignal.set({platformRole: 'USER'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBe(false);
  });
});
