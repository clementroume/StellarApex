import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementListComponent} from './movement-list.component';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {of, throwError} from 'rxjs';
import {provideIcons} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {DialogService} from '../../../core/services/dialog.service';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {APP_ICONS} from '../../../app.config';

describe('MovementListComponent', () => {
  let component: MovementListComponent;
  let fixture: ComponentFixture<MovementListComponent>;
  let movementServiceSpy: jasmine.SpyObj<MovementService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let dialogServiceSpy: jasmine.SpyObj<DialogService>;

  const mockMovements: MovementSummaryResponse[] = [
    {id: 1, name: 'Back Squat', category: 'SQUAT'},
    {id: 2, name: 'Pull-up', category: 'PULLING'}
  ];

  beforeEach(async () => {
    movementServiceSpy = jasmine.createSpyObj('MovementService', ['searchMovements', 'getMovement', 'getReferenceData']);
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

    await TestBed.configureTestingModule({
      imports: [MovementListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: MovementService, useValue: movementServiceSpy},
        {provide: AuthService, useValue: authServiceSpy},
        {provide: DialogService, useValue: dialogServiceSpy}
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

  it('should fetch movement details and open global modal on openDetails', () => {
    const mockFullMovement = {id: 1, name: 'Back Squat', category: 'SQUAT', targetedMuscles: []};
    movementServiceSpy.getMovement.and.returnValue(of(mockFullMovement as any));

    component.openDetails(1);

    expect(movementServiceSpy.getMovement).toHaveBeenCalledWith(1);
    expect(dialogServiceSpy.openMovement).toHaveBeenCalledWith(mockFullMovement as any);
  });
});
