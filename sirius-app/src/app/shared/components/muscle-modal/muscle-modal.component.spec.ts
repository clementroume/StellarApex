import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleModalComponent} from './muscle-modal.component';
import {DialogService} from '../../../core/services/dialog.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {of, throwError} from 'rxjs';

describe('MuscleModalComponent', () => {
  let component: MuscleModalComponent;
  let fixture: ComponentFixture<MuscleModalComponent>;

  let mockDialogService: any;
  let mockAuthService: any;
  let mockMuscleService: jasmine.SpyObj<MuscleService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    mockDialogService = {
      muscleToView: signal({id: 1, medicalName: 'Pectoralis'}),
      closeMuscle: jasmine.createSpy('closeMuscle')
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMuscleService = jasmine.createSpyObj('MuscleService', ['deleteMuscle', 'notifyRefresh']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [MuscleModalComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {provide: DialogService, useValue: mockDialogService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: NotificationService, useValue: mockNotificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call dialogService.closeMuscle on onClose', () => {
    component.onClose();
    expect(mockDialogService.closeMuscle).toHaveBeenCalled();
  });

  it('should format localized name and description properly', () => {
    const mockMuscle: any = {
      medicalName: 'Pectoralis',
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest',
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN'
    };

    component.activeLang.set('fr');
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc FR');

    component.activeLang.set('en');
    expect(component.getLocalizedName(mockMuscle)).toBe('Chest');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc EN');
  });

  it('should fallback to medical name if common name is missing', () => {
    const mockMuscle: any = {medicalName: 'Pectoralis'};
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoralis');
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete();
      expect(mockMuscleService.deleteMuscle).not.toHaveBeenCalled();
    });

    it('should delete muscle and close modal on success', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMuscleService.deleteMuscle.and.returnValue(of(undefined));

      component.onDelete();

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockMuscleService.notifyRefresh).toHaveBeenCalled();
      expect(mockDialogService.closeMuscle).toHaveBeenCalled();
    });

    it('should display conflict error on 409 status', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMuscleService.deleteMuscle.and.returnValue(throwError(() => ({status: 409})));

      component.onDelete();

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showError).toHaveBeenCalled();
    });
  });
});
