import type {Mock, MockedObject} from 'vitest';
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {Location} from '@angular/common';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {WodDetailComponent} from './wod-detail.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AccessService} from '../../../core/services/access.service';
import {APP_ICONS} from '../../../app.config';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';

describe('WodDetailComponent', () => {
  let component: WodDetailComponent;
  let fixture: ComponentFixture<WodDetailComponent>;
  let router: Router;

  let mockWodService: MockedObject<WodService>;
  let mockNotificationService: MockedObject<NotificationService>;
  let mockLocation: MockedObject<Location>;

  const movement = (id: number, name: string): any => ({id, name, category: 'SQUAT', equipment: [], techniques: [], targetedMuscles: []});

  const buildWod = (overrides: Partial<WodResponse> = {}): WodResponse => ({
    id: 1,
    title: 'Fran',
    wodType: 'FOR_TIME',
    scoreType: 'TIME',
    authorId: 10,
    isPublic: false,
    timeCapSeconds: 600,
    modalities: ['WEIGHTLIFTING'],
    capabilities: {canModify: true, locked: false},
    movements: [
      {id: 12, movement: movement(2, 'Pull-up'), orderIndex: 2, repsScheme: '21-15-9'},
      {id: 11, movement: movement(1, 'Thruster'), orderIndex: 1, repsScheme: '21-15-9', weight: 43, weightUnit: 'KG'}
    ],
    createdAt: '',
    updatedAt: '',
    ...overrides
  });

  const init = (wod: WodResponse = buildWod()) => {
    mockWodService.getWod.mockReturnValue(of(wod));
    fixture = TestBed.createComponent(WodDetailComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
  };

  beforeEach(async () => {

    mockWodService = {
      getWod: vi.fn().mockName('WodService.getWod'),
      deleteWod: vi.fn().mockName('WodService.deleteWod'),
      notifyRefresh: vi.fn().mockName('WodService.notifyRefresh')
    } as any;
    mockNotificationService = {
      showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
      showError: vi.fn().mockName('NotificationService.showError')
    } as any;
    mockLocation = {back: vi.fn().mockName('Location.back')} as any;

    await TestBed.configureTestingModule({
      imports: [WodDetailComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {
          provide: AccessService,
          useValue: {memberships: signal([{gymId: 5, gymName: 'Box', gymRole: 'COACH', permissions: []}])}
        },
        {provide: WodService, useValue: mockWodService},
        {provide: NotificationService, useValue: mockNotificationService},
        {provide: Location, useValue: mockLocation}
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
  });

  it('should load the WOD and sort its movements by order', () => {
    init();
    expect(mockWodService.getWod).toHaveBeenCalledWith(1);
    expect(component.wod()?.title).toBe('Fran');
    expect(component.sortedMovements().map(m => m.movement.name)).toEqual(['Thruster', 'Pull-up']);
    expect(fixture.nativeElement.textContent).toContain('Thruster');
  });

  it('should notify and go back when the WOD cannot be loaded', () => {
    mockWodService.getWod.mockReturnValue(throwError(() => ({status: 403})));
    fixture = TestBed.createComponent(WodDetailComponent);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();

    expect(mockNotificationService.showError).toHaveBeenCalled();
    expect(mockLocation.back).toHaveBeenCalled();
  });

  describe('visibility', () => {
    it('should be PUBLIC, GYM or PRIVATE', () => {
      init(buildWod({isPublic: true}));
      expect(component.visibility()).toBe('PUBLIC');

      component.wod.set(buildWod({gymId: 5}));
      expect(component.visibility()).toBe('GYM');

      component.wod.set(buildWod());
      expect(component.visibility()).toBe('PRIVATE');
    });
  });

  describe('rights', () => {
    it('should show the actions when Aldebaran allows modification', () => {
      init();
      expect(component.canModify()).toBe(true);
      expect(fixture.nativeElement.querySelector('app-detail-admin-actions')).toBeTruthy();
    });

    it('should hide the actions when Aldebaran refuses modification', () => {
      init(buildWod({capabilities: {canModify: false, locked: false}}));
      expect(component.canModify()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-detail-admin-actions')).toBeNull();
    });

    it('should hide the actions and show a badge when the WOD is locked', () => {
      init(buildWod({capabilities: {canModify: true, locked: true}}));
      expect(component.canModify()).toBe(false);
      expect(component.isLocked()).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('WOD.MESSAGES.LOCKED_BADGE');
    });

    it('should hide the actions when capabilities are missing', () => {
      init(buildWod({capabilities: undefined}));
      expect(component.canModify()).toBe(false);
    });

    it('should display the gym name of a gym WOD', () => {
      init(buildWod({gymId: 5}));
      expect(component.gymName()).toBe('Box');
      expect(fixture.nativeElement.textContent).toContain('Box');
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      init();
      vi.spyOn(window, 'confirm');
    });

    it('should do nothing if confirm is cancelled', () => {
      (window.confirm as Mock).mockReturnValue(false);
      component.onDelete(component.wod()!);
      expect(mockWodService.deleteWod).not.toHaveBeenCalled();
    });

    it('should delete the WOD and navigate back to the list', () => {
      const navigateSpy = vi.spyOn(router, 'navigate');
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(of(undefined));

      component.onDelete(component.wod()!);

      expect(mockWodService.deleteWod).toHaveBeenCalledWith(1);
      expect(mockNotificationService.showSuccess).toHaveBeenCalled();
      expect(mockWodService.notifyRefresh).toHaveBeenCalled();
      expect(navigateSpy).toHaveBeenCalledWith(['/wods']);
    });

    it('should display the backend message when the WOD is locked (409)', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(throwError(() => ({status: 409, error: {detail: 'Locked!'}})));

      component.onDelete(component.wod()!);

      expect(mockNotificationService.showError).toHaveBeenCalledWith('Locked!');
    });

    it('should display a generic error otherwise', () => {
      (window.confirm as Mock).mockReturnValue(true);
      mockWodService.deleteWod.mockReturnValue(throwError(() => ({status: 500})));

      component.onDelete(component.wod()!);

      expect(mockNotificationService.showError).toHaveBeenCalledWith('GLOBAL.DELETE_ERROR');
    });
  });
});
