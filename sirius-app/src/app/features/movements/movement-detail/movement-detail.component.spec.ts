import type {Mock, MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementDetailComponent} from './movement-detail.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {Location} from '@angular/common';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('MovementDetailComponent', () => {
  let component: MovementDetailComponent;
  let fixture: ComponentFixture<MovementDetailComponent>;
  let translateService: TranslateService;

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
    targetedMuscles: [],
    techniques: ['POWER', 'SQUAT'],
    equipment: ['BARBELL'],
    usualNames: [
      {nameEn: 'Squat'},
      {nameEn: 'Power Clean', techniques: ['POWER']}
    ]
  };

  beforeEach(async () => {
    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMovementService = {
      getMovement: vi.fn().mockName("MovementService.getMovement"),
      getReferenceData: vi.fn().mockName("MovementService.getReferenceData"),
      getVariant: vi.fn().mockName("MovementService.getVariant"),
      deleteMovement: vi.fn().mockName("MovementService.deleteMovement"),
      notifyRefresh: vi.fn().mockName("MovementService.notifyRefresh")
    } as any;
    mockMovementService.getMovement.mockReturnValue(of(mockMovement));
    mockMovementService.getReferenceData.mockReturnValue(of({
      categoryGroups: {}, equipmentGroups: {}, techniqueGroups: {RECEPTION: ['POWER', 'SQUAT']}, exclusiveTechniqueSets: [['POWER', 'SQUAT']]
    } as any));
    mockMovementService.getVariant.mockReturnValue(of({usualNameEn: 'Squat', muscles: []} as any));

    mockNotificationService = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;
    mockLocation = {
      back: vi.fn().mockName("Location.back")
    } as any;

    await TestBed.configureTestingModule({
      imports: [MovementDetailComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: mockAuthService},
        {provide: MovementService, useValue: mockMovementService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementDetailComponent);
    component = fixture.componentInstance;

    translateService = TestBed.inject(TranslateService);

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

  it('should ask the catalog for the chosen variant, one reception at a time', () => {
    mockMovementService.getVariant.mockReturnValue(of({usualNameEn: 'Power Clean', usualNameFr: 'Power clean', muscles: []} as any));
    component.toggleTechnique('POWER');
    fixture.detectChanges();
    expect(mockMovementService.getVariant).toHaveBeenLastCalledWith(1, ['POWER'], [], expect.anything());
    expect(component.displayName()).toBe('Power Clean');

    component.toggleTechnique('SQUAT');
    fixture.detectChanges();
    expect(mockMovementService.getVariant).toHaveBeenLastCalledWith(1, ['SQUAT'], [], expect.anything());
  });

  it('should leave a usual name when it is tapped again', () => {
    const pecDeck = {nameEn: 'Pec Deck', techniques: ['SQUAT'], equipment: ['BARBELL']} as any;
    mockMovementService.getVariant.mockReturnValue(of({usualNameEn: 'Pec Deck', muscles: []} as any));
    component.applyUsualName(pecDeck);
    fixture.detectChanges();
    expect(mockMovementService.getVariant).toHaveBeenLastCalledWith(1, ['SQUAT'], ['BARBELL'], expect.anything());

    component.applyUsualName(pecDeck);
    fixture.detectChanges();
    expect(mockMovementService.getVariant).toHaveBeenLastCalledWith(1, [], [], expect.anything());
  });

  it('should ask the catalog for a chain name by its component technique', () => {
    component.applyUsualName({nameEn: 'Clean and Push Jerk', techniques: ['2:POWER'], equipment: []} as any);
    fixture.detectChanges();
    expect(mockMovementService.getVariant).toHaveBeenLastCalledWith(1, ['2:POWER'], [], expect.anything());
  });

  it('should offer a shortcut per usual name, without the plain movement', () => {
    expect(component.usualNames().map(u => u.nameEn)).toEqual(['Power Clean']);
    expect(component.isUsualNameOn({nameEn: 'Power Clean'})).toBe(false);
  });

  it('should go back when goBack is called', () => {
    component.goBack();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  it('should format localized description and cues properly', () => {
    translateService.use('fr');
    fixture.detectChanges();
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc FR');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues FR');

    translateService.use('en');
    fixture.detectChanges();
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc EN');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues EN');
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
