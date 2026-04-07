import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleDetailComponent} from './muscle-detail.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {of, throwError} from 'rxjs';
import {Location} from '@angular/common';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('MuscleDetailComponent', () => {
  let component: MuscleDetailComponent;
  let fixture: ComponentFixture<MuscleDetailComponent>;

  let mockAuthService: any;
  let mockMuscleService: jasmine.SpyObj<MuscleService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;
  let mockLocation: jasmine.SpyObj<Location>;
  let router: Router;

  const mockMuscle: any = {
    id: 1,
    medicalName: 'Pectoralis',
    commonNameFr: 'Pectoraux',
    commonNameEn: 'Chest',
    descriptionFr: 'Desc FR',
    descriptionEn: 'Desc EN',
    muscleGroup: 'CHEST'
  };

  beforeEach(async () => {
    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMuscleService = jasmine.createSpyObj('MuscleService', ['getMuscle', 'deleteMuscle', 'notifyRefresh']);
    mockMuscleService.getMuscle.and.returnValue(of(mockMuscle));

    mockNotificationService = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);
    mockLocation = jasmine.createSpyObj('Location', ['back']);

    await TestBed.configureTestingModule({
      imports: [MuscleDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: AuthService, useValue: mockAuthService},
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleDetailComponent);
    component = fixture.componentInstance;

    // Injecte l'ID comme si ça venait de l'URL
    fixture.componentRef.setInput('id', '1');
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create and load muscle data', () => {
    expect(component).toBeTruthy();
    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith(1);
    expect(component.muscle()).toEqual(mockMuscle);
  });

  it('should go back when goBack is called', () => {
    component.goBack();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  it('should format localized name and description properly', () => {
    component.activeLang.set('fr');
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc FR');

    component.activeLang.set('en');
    expect(component.getLocalizedName(mockMuscle)).toBe('Chest');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc EN');
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete(mockMuscle);
      expect(mockMuscleService.deleteMuscle).not.toHaveBeenCalled();
    });

    it('should delete muscle and navigate back on success', () => {
      const navigateSpy = spyOn(router, 'navigate');
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMuscleService.deleteMuscle.and.returnValue(of(undefined));

      component.onDelete(mockMuscle);

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockMuscleService.notifyRefresh).toHaveBeenCalled();
      expect(navigateSpy).toHaveBeenCalledWith(['/muscles']);
    });

    it('should display conflict error on 409 status', () => {
      (window.confirm as jasmine.Spy).and.returnValue(true);
      mockMuscleService.deleteMuscle.and.returnValue(throwError(() => ({status: 409})));

      component.onDelete(mockMuscle);

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showError).toHaveBeenCalled();
    });
  });
});
