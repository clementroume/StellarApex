import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementListComponent} from './movement-list.component';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of, Subject} from 'rxjs';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext} from '@angular/common/http';
import {vi} from 'vitest';
import {RecordService} from '../../../api/aldebaran/services/record.service';
import {UnitService} from '../../../core/services/unit.service';

describe('MovementListComponent', () => {
  let component: MovementListComponent;
  let fixture: ComponentFixture<MovementListComponent>;

  let mockMovementService: any;
  let mockAuthService: any;
  let mockExporterService: any;
  let mockNotificationService: any;

  let mockCurrentUserSignal: WritableSignal<any>;

  const mockMovements = [
    {
      id: 1,
      name: 'Back Squat',
      nameAbbreviation: 'BS',
      category: 'SQUAT',
      modality: 'WEIGHTLIFTING'
    },
    {
      id: 2,
      name: 'Front Squat',
      nameAbbreviation: 'FS',
      category: 'SQUAT',
      modality: 'WEIGHTLIFTING'
    }
  ];

  beforeEach(async () => {
    // 1. Update the spy to use 'getMovements' instead of 'searchMovements'
    const movementServiceSpyMethods = {
      getMovements: vi.fn().mockName("MovementService.getMovements"),
      getUsualNames: vi.fn().mockName("MovementService.getUsualNames"),
      getReferenceData: vi.fn().mockName("MovementService.getReferenceData")
    };

    mockMovementService = {
      ...movementServiceSpyMethods,
      refreshNeeded$: new Subject<void>(),
      savedSearchQuery: signal(''),
      savedActiveTab: signal('WEIGHTLIFTING'),
      savedFamily: signal<string | null>(null)
    };

    // 2. Mock the new method
    mockMovementService.getMovements.mockReturnValue(of(mockMovements));
    mockMovementService.getUsualNames.mockReturnValue(of([
      {movementId: 2, movementName: 'Front Squat', category: 'SQUAT', nameEn: 'Front Squat', techniques: [], equipment: []},
      {movementId: 1, movementName: 'Back Squat', category: 'SQUAT', nameEn: 'Pause Squat', nameFr: 'Squat pausé', searchTerms: 'tempo',
        techniques: ['PAUSED'], equipment: []}
    ]));

    mockMovementService.getReferenceData.mockReturnValue(of({
      categoryGroups: {
        'WEIGHTLIFTING': ['SQUAT', 'PULL', 'PRESS'],
        'GYMNASTICS': ['BODYWEIGHT']
      }
    }));

    mockCurrentUserSignal = signal({platformRole: 'USER'});
    mockAuthService = {
      currentUser: mockCurrentUserSignal
    };

    mockExporterService = {
      exportMovements: vi.fn().mockName("ExporterService.exportMovements")
    };
    mockNotificationService = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    };

    await TestBed.configureTestingModule({
      imports: [MovementListComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: MovementService, useValue: mockMovementService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: ExporterService, useValue: mockExporterService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: RecordService, useValue: {getMyRecords: vi.fn().mockReturnValue(of([
          {movementId: 1, movementName: 'Back Squat', category: 'SQUAT', variants: [
            {variantKey: '', base: true, equipment: [], techniques: [], estimates: [],
              records: [{reps: 1, weightKg: 90, date: '2026-01-01', scoreId: 1, wodId: 1, wodTitle: 'T'}]}
          ]}
        ]))}},
        {provide: UnitService, useValue: {formatScoreLoad: (v: number) => `${v} kg`, formatPercentLoad: (v: number) => `${v} kg`}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load ALL movements on init', () => {
    // Verify that getMovements was called with the correct HttpContext
    const callArgs = vi.mocked(mockMovementService.getMovements).mock.lastCall as any[];
    expect(callArgs[0]).toBeInstanceOf(HttpContext);
  });

  it('should pre-initialize all known categories', () => {
    const grouped = component.movementsByCategory();
    expect(grouped.has('PULL')).toBe(true);
    expect(grouped.get('PULL')?.length).toBe(0); // Existing empty category
    expect(grouped.has('SQUAT')).toBe(true);
    expect(grouped.get('SQUAT')?.length).toBe(2); // Populated category
  });

  it('should filter locally by name or abbreviation', () => {
    // Test search by abbreviation
    component.searchQuery.set('fs');
    expect(component.filteredMovements().length).toBe(1);
    expect(component.filteredMovements()[0].name).toBe('Front Squat');

    // Test search by partial name
    component.searchQuery.set('back');
    expect(component.filteredMovements().length).toBe(1);
    expect(component.filteredMovements()[0].nameAbbreviation).toBe('BS');
  });

  it('should find a movement through its usual names, and show only those that are not its own name', () => {
    component.searchQuery.set('pausé');
    expect(component.filteredMovements().map(m => m.id)).toEqual([1]);
    expect(component.shownNames().map(o => o.nameEn)).toEqual(['Pause Squat']);

    component.searchQuery.set('front');
    expect(component.filteredMovements().map(m => m.id)).toEqual([2]);
    expect(component.shownNames()).toEqual([]);

    component.searchQuery.set('tempo');
    expect(component.filteredMovements().map(m => m.id)).toEqual([1]);
  });

  it('should build a card with its variants and my record, and filter by family', () => {
    component.activeTab.set('WEIGHTLIFTING');
    fixture.detectChanges();
    const cards = [...fixture.nativeElement.querySelectorAll('[data-slot="movement-card"]')] as HTMLElement[];
    expect(cards.map(c => c.querySelector('.font-semibold')?.textContent)).toEqual(['Back Squat', 'Front Squat']);
    expect(component.cards()[0]).toMatchObject({variantCount: 1, preview: 'Pause Squat'});
    expect(cards[0].querySelector('[data-slot="card-record"]')?.textContent).toContain('90 kg');
    expect(cards[1].querySelector('[data-slot="card-record"]')).toBeNull();

    component.activeFamily.set('PULL');
    expect(component.cards()).toEqual([]);
    component.setModality('GYMNASTICS');
    expect(component.activeFamily()).toBeNull();
  });

  it('should search the whole catalog, whatever the discipline', () => {
    component.setModality('GYMNASTICS');
    expect(component.cards()).toEqual([]);
    component.onSearchChange('front');
    expect(component.cards().map(c => c.movement.name)).toEqual(['Front Squat']);
  });

  it('should not call the backend on input change (onSearchChange)', () => {
    mockMovementService.getMovements.mockClear(); // Reset counter after ngOnInit

    // Call the updated method which now takes a string directly
    component.onSearchChange('squat');

    expect(component.searchQuery()).toBe('squat');
    // The service must not be called since filtering is handled locally
    expect(mockMovementService.getMovements).not.toHaveBeenCalled();
  });
});
