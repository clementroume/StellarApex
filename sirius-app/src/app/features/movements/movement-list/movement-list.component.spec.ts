import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementListComponent} from './movement-list.component';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {of, Subject, throwError} from 'rxjs';
import {provideIcons} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {DialogService} from '../../../core/services/dialog.service';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {APP_ICONS} from '../../../app.config';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';

describe('MovementListComponent', () => {
  let component: MovementListComponent;
  let fixture: ComponentFixture<MovementListComponent>;
  let movementServiceSpy: jasmine.SpyObj<MovementService> & { refreshNeeded$: Subject<void> };
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let dialogServiceSpy: jasmine.SpyObj<DialogService>;
  let exporterServiceSpy: jasmine.SpyObj<ExporterService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  const mockMovements: MovementSummaryResponse[] = [
    {id: 1, name: 'Back Squat', category: 'SQUAT'},
    {id: 2, name: 'Pull-up', category: 'PULLING'}
  ];

  beforeEach(async () => {
    // Ajout explicite du mock du Subject refreshNeeded$
    movementServiceSpy = jasmine.createSpyObj('MovementService', ['searchMovements', 'getMovement', 'getReferenceData']) as any;
    movementServiceSpy.refreshNeeded$ = new Subject<void>();
    movementServiceSpy.searchMovements.and.returnValue(of(mockMovements));
    movementServiceSpy.getReferenceData.and.returnValue(of({
      categoryGroups: {'WEIGHTLIFTING': ['SQUAT', 'PULLING']},
      equipmentGroups: {},
      techniqueGroups: {}
    }));

    authServiceSpy = jasmine.createSpyObj('AuthService', [], {
      currentUser: signal({platformRole: 'ADMIN'})
    });

    dialogServiceSpy = jasmine.createSpyObj('DialogService', ['openMovement']);

    exporterServiceSpy = jasmine.createSpyObj('ExporterService', ['exportMovements']);
    notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MovementListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MovementService, useValue: movementServiceSpy},
        {provide: AuthService, useValue: authServiceSpy},
        {provide: DialogService, useValue: dialogServiceSpy},
        {provide: ExporterService, useValue: exporterServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should trigger search when input changes', () => {
    const event = {target: {value: 'Squat'}} as unknown as Event;
    component.onSearchChange(event);
    expect(movementServiceSpy.searchMovements).toHaveBeenCalledWith('Squat');
  });

  it('should clear the list in case of API error', () => {
    movementServiceSpy.searchMovements.and.returnValue(throwError(() => new Error('API Error')));
    component.loadMovements('ErrorSearch');
    expect(component.movements().length).toBe(0);
  });

  it('should reload movements when refreshNeeded$ emits', () => {
    movementServiceSpy.searchMovements.calls.reset();
    movementServiceSpy.refreshNeeded$.next();
    expect(movementServiceSpy.searchMovements).toHaveBeenCalledWith('');
  });

  it('should fetch movement details and open global modal on openDetails', () => {
    const mockFullMovement = {id: 1, name: 'Back Squat', category: 'SQUAT', targetedMuscles: []};
    movementServiceSpy.getMovement.and.returnValue(of(mockFullMovement as any));

    component.openDetails(1);

    expect(movementServiceSpy.getMovement).toHaveBeenCalledWith(1);
    expect(dialogServiceSpy.openMovement).toHaveBeenCalledWith(mockFullMovement as any);
  });

  describe('exportCsv', () => {
    beforeEach(() => {
      spyOn(window, 'confirm');
    });

    it('should abort export if user cancels confirm dialog', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.exportCsv();
      expect(exporterServiceSpy.exportMovements).not.toHaveBeenCalled();
    });

    it('should call export service and show success notification if confirmed', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      exporterServiceSpy.exportMovements.and.returnValue(of('Export successful'));

      component.exportCsv();

      expect(exporterServiceSpy.exportMovements).toHaveBeenCalled();
      expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
      expect(component.isExporting()).toBeFalse();
    });

    it('should show error notification if export fails', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      exporterServiceSpy.exportMovements.and.returnValue(throwError(() => new Error('Export failed')));

      component.exportCsv();

      expect(exporterServiceSpy.exportMovements).toHaveBeenCalled();
      expect(notificationServiceSpy.showError).toHaveBeenCalled();
      expect(component.isExporting()).toBeFalse();
    });
  });
});
