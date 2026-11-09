import type {Mock, MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleDetailComponent} from './muscle-detail.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
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
  let translateService: TranslateService;

  let mockAuthService: any;
  let mockMuscleService: MockedObject<MuscleService>;
  let mockNotificationService: MockedObject<NotificationService>;
  let mockLocation: MockedObject<Location>;
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

    mockMuscleService = {
      getMuscle: vi.fn().mockName("MuscleService.getMuscle"),
      getUsage: vi.fn().mockName("MuscleService.getUsage"),
      deleteMuscle: vi.fn().mockName("MuscleService.deleteMuscle"),
      notifyRefresh: vi.fn().mockName("MuscleService.notifyRefresh")
    } as any;
    mockMuscleService.getMuscle.mockReturnValue(of(mockMuscle));
    mockMuscleService.getUsage.mockReturnValue(of([
      {movementId: 1, movementName: 'Bench Press', category: 'PRESS', role: 'AGONIST'},
      {movementId: 2, movementName: 'Dip', category: 'DIPS', role: 'AGONIST'},
      {movementId: 3, movementName: 'Push-up', category: 'PUSH', role: 'SYNERGIST', trigger: 'FEET_ELEVATED'}
    ]));

    mockNotificationService = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;
    mockLocation = {
      back: vi.fn().mockName("Location.back")
    } as any;

    await TestBed.configureTestingModule({
      imports: [MuscleDetailComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: mockAuthService},
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleDetailComponent);
    component = fixture.componentInstance;
    translateService = TestBed.inject(TranslateService);

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

  it('should list the movements using the muscle, by role', () => {
    expect(mockMuscleService.getUsage).toHaveBeenCalledWith(1);
    expect(component.countOf('AGONIST')).toBe(2);
    expect(component.shownUsage().map(u => u.movementName)).toEqual(['Bench Press', 'Dip']);
    component.role.set('SYNERGIST');
    expect(component.shownUsage().map(u => u.trigger)).toEqual(['FEET_ELEVATED']);
  });

  it('should go back when goBack is called', () => {
    component.goBack();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  it('should format localized name and description properly', () => {
    translateService.use('fr');
    fixture.detectChanges();
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc FR');

    translateService.use('en');
    fixture.detectChanges();
    expect(component.getLocalizedName(mockMuscle)).toBe('Chest');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc EN');
  });

  describe('onDelete', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as Mock).mockReturnValue(false);
      component.onDelete(mockMuscle);
      expect(mockMuscleService.deleteMuscle).not.toHaveBeenCalled();
    });

    it('should delete muscle and navigate back on success', () => {
      const navigateSpy = vi.spyOn(router, 'navigate');
      (window.confirm as Mock).mockReturnValue(true);
      mockMuscleService.deleteMuscle.mockReturnValue(of(undefined));

      component.onDelete(mockMuscle);

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockMuscleService.notifyRefresh).toHaveBeenCalled();
      expect(navigateSpy).toHaveBeenCalledWith(['/muscles']);
    });

    it('should display conflict error on 409 status', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockMuscleService.deleteMuscle.mockReturnValue(throwError(() => ({status: 409})));

      component.onDelete(mockMuscle);

      expect(mockMuscleService.deleteMuscle).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showError).toHaveBeenCalled();
    });
  });
});
