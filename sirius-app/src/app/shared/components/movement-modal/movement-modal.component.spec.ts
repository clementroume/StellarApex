import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementModalComponent} from './movement-modal.component';
import {DialogService} from '../../../core/services/dialog.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';

describe('MovementModalComponent', () => {
  let component: MovementModalComponent;
  let fixture: ComponentFixture<MovementModalComponent>;

  let mockDialogService: any;
  let mockAuthService: any;
  let mockMuscleService: jasmine.SpyObj<MuscleService>;
  let mockMovementService: jasmine.SpyObj<MovementService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    mockDialogService = {
      movementToView: signal({
        id: 1,
        name: 'Squat',
        category: 'WEIGHTLIFTING',
        targetedMuscles: []
      }),
      closeMovement: jasmine.createSpy('closeMovement'),
      openMuscle: jasmine.createSpy('openMuscle')
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMuscleService = jasmine.createSpyObj('MuscleService', ['getMuscle']);
    mockMuscleService.getMuscle.and.returnValue(of({id: 1, medicalName: 'Pectoralis'} as any));

    mockMovementService = jasmine.createSpyObj('MovementService', ['deleteMovement', 'notifyRefresh']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MovementModalComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: DialogService, useValue: mockDialogService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: MovementService, useValue: mockMovementService},
        {provide: NotificationService, useValue: mockNotificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format localized description and cues properly', () => {
    const mockMovement: any = {
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN',
      coachingCuesFr: 'Cues FR',
      coachingCuesEn: 'Cues EN'
    };

    component.activeLang.set('fr');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc FR');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues FR');

    component.activeLang.set('en');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc EN');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues EN');
  });

  it('should call dialogService.closeMovement on onClose', () => {
    component.onClose();
    expect(mockDialogService.closeMovement).toHaveBeenCalled();
  });

  it('should fetch muscle and call dialogService.openMuscle on openMuscle', () => {
    const mockEvent = {
      preventDefault: jasmine.createSpy('preventDefault'),
      stopPropagation: jasmine.createSpy('stopPropagation')
    } as unknown as Event;

    component.openMuscle(1, mockEvent);

    expect(mockEvent.preventDefault).toHaveBeenCalled();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith(1);
    expect(mockDialogService.openMuscle).toHaveBeenCalledWith({id: 1, medicalName: 'Pectoralis'} as any);
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete();
      expect(mockMovementService.deleteMovement).not.toHaveBeenCalled();
    });

    it('should delete movement and close modal on success', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMovementService.deleteMovement.and.returnValue(of(undefined));

      component.onDelete();

      expect(mockMovementService.deleteMovement).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockMovementService.notifyRefresh).toHaveBeenCalled();
      expect(mockDialogService.closeMovement).toHaveBeenCalled();
    });

    it('should display conflict error on 409 status', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMovementService.deleteMovement.and.returnValue(throwError(() => ({status: 409})));

      component.onDelete();

      expect(mockMovementService.deleteMovement).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showError).toHaveBeenCalled();
    });
  });
});
