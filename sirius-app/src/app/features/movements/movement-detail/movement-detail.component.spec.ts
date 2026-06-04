import type {Mock, MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementDetailComponent} from './movement-detail.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {Location} from '@angular/common';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('MovementDetailComponent', () => {
  let component: MovementDetailComponent;
  let fixture: ComponentFixture<MovementDetailComponent>;

  let mockAuthService: any;
  let mockMovementService: MockedObject<MovementService>;
  let mockNotificationService: MockedObject<NotificationService>;
  let mockLocation: MockedObject<Location>;
  let router: Router;

  const mockMovement: any = {
    id: 1,
    name: 'Squat',
    category: 'WEIGHTLIFTING',
    descriptionFr: 'Desc FR',
    descriptionEn: 'Desc EN',
    coachingCuesFr: 'Cues FR',
    coachingCuesEn: 'Cues EN',
    targetedMuscles: []
  };

  beforeEach(async () => {
    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMovementService = {
      getMovement: vi.fn().mockName("MovementService.getMovement"),
      deleteMovement: vi.fn().mockName("MovementService.deleteMovement"),
      notifyRefresh: vi.fn().mockName("MovementService.notifyRefresh")
    } as any;
    mockMovementService.getMovement.mockReturnValue(of(mockMovement));

    mockNotificationService = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;
    mockLocation = {
      back: vi.fn().mockName("Location.back")
    } as any;

    await TestBed.configureTestingModule({
      imports: [MovementDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: AuthService, useValue: mockAuthService},
        {provide: MovementService, useValue: mockMovementService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementDetailComponent);
    component = fixture.componentInstance;

    // Injecte l'ID via le signal input()
    fixture.componentRef.setInput('id', '1');
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create and load movement data', () => {
    expect(component).toBeTruthy();
    expect(mockMovementService.getMovement).toHaveBeenCalledWith(1);
    expect(component.movement()).toEqual(mockMovement);
  });

  it('should go back when goBack is called', () => {
    component.goBack();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  it('should format localized description and cues properly', () => {
    component.activeLang.set('fr');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc FR');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues FR');

    component.activeLang.set('en');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc EN');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues EN');
  });

  it('should format muscle name properly depending on language', () => {
    const mockMuscle: any = {
      medicalName: 'Pectoralis',
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest'
    };

    component.activeLang.set('fr');
    expect(component.getLocalizedMuscleName(mockMuscle)).toBe('Pectoraux');

    component.activeLang.set('en');
    expect(component.getLocalizedMuscleName(mockMuscle)).toBe('Chest');
  });

  describe('onDelete', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as Mock).mockReturnValue(false);
      component.onDelete(mockMovement);
      expect(mockMovementService.deleteMovement).not.toHaveBeenCalled();
    });

    it('should delete movement and navigate away on success', () => {
      const navigateSpy = vi.spyOn(router, 'navigate');
      (window.confirm as Mock).mockReturnValue(true);
      mockMovementService.deleteMovement.mockReturnValue(of(undefined));

      component.onDelete(mockMovement);

      expect(mockMovementService.deleteMovement).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockMovementService.notifyRefresh).toHaveBeenCalled();
      expect(navigateSpy).toHaveBeenCalledWith(['/movements']);
    });

    it('should display conflict error on 409 status', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockMovementService.deleteMovement.mockReturnValue(throwError(() => ({status: 409})));

      component.onDelete(mockMovement);

      expect(mockMovementService.deleteMovement).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showError).toHaveBeenCalled();
    });
  });
});
