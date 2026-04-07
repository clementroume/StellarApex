import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementListComponent} from './movement-list.component';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
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
    const movementServiceSpyMethods = jasmine.createSpyObj('MovementService', [
      'getMovements',
      'getReferenceData',
      'toggleCategoryExpansion'
    ]);

    mockMovementService = {
      ...movementServiceSpyMethods,
      refreshNeeded$: new Subject<void>(),
      savedSearchQuery: signal(''),
      savedActiveTab: signal('WEIGHTLIFTING'),
      savedExpandedCategories: signal(new Set<string>())
    };

    // 2. Mock the new method
    mockMovementService.getMovements.and.returnValue(of(mockMovements));

    mockMovementService.getReferenceData.and.returnValue(of({
      categoryGroups: {
        'WEIGHTLIFTING': ['SQUAT', 'PULL', 'PRESS'],
        'GYMNASTICS': ['BODYWEIGHT']
      }
    }));

    mockCurrentUserSignal = signal({platformRole: 'USER'});
    mockAuthService = {
      currentUser: mockCurrentUserSignal
    };

    mockExporterService = jasmine.createSpyObj('ExporterService', ['exportMovements']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MovementListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MovementService, useValue: mockMovementService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: ExporterService, useValue: mockExporterService},
        {provide: NotificationService, useValue: mockNotificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load ALL movements on init', () => {
    // Verify that getMovements was called with the correct HttpContext
    const callArgs = mockMovementService.getMovements.calls.mostRecent().args as any[];
    expect(callArgs[0]).toBeInstanceOf(HttpContext);
  });

  it('should pre-initialize all known categories', () => {
    const grouped = component.movementsByCategory();
    expect(grouped.has('PULL')).toBeTrue();
    expect(grouped.get('PULL')?.length).toBe(0); // Existing empty category
    expect(grouped.has('SQUAT')).toBeTrue();
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

  it('should not call the backend on input change (onSearchChange)', () => {
    mockMovementService.getMovements.calls.reset(); // Reset counter after ngOnInit

    // Call the updated method which now takes a string directly
    component.onSearchChange('squat');

    expect(component.searchQuery()).toBe('squat');
    // The service must not be called since filtering is handled locally
    expect(mockMovementService.getMovements).not.toHaveBeenCalled();
  });
});
