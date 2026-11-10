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
import {NEVER, of, throwError} from 'rxjs';
import {Location} from '@angular/common';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';
import {ThemeService} from '../../../core/services/theme.service';

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
    region: 'THORAX'
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
        {provide: ThemeService, useValue: {currentTheme: signal('dark')}},
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
    expect(mockMuscleService.getMuscle.mock.calls[0][0]).toBe(1);
    expect(component.muscle()).toEqual(mockMuscle);
  });

  it('should list the movements using the muscle, by role', () => {
    expect(mockMuscleService.getUsage.mock.calls[0][0]).toBe(1);
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

  it('shows the group and its members as tiles on a group page', () => {
    mockMuscleService.getMuscle.mockReturnValue(of({...mockMuscle, id: 2, members: [{id: 3, medicalName: 'A'}, {id: 4, medicalName: 'B'}]}));
    fixture.componentRef.setInput('id', '2');
    fixture.detectChanges();
    expect(component.tiles().map(t => t.id)).toEqual([2, 3, 4]);
    expect(fixture.nativeElement.querySelectorAll('[data-slot="muscle-members"] a').length).toBe(3);
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')).not.toBeNull();
  });

  it('shows the tiles of the parent on a member page, without reloading them between members', () => {
    const group = {...mockMuscle, id: 2, members: [{id: 3, medicalName: 'A'}, {id: 4, medicalName: 'B'}]};
    const memberOf = (id: number) => ({...mockMuscle, id, parent: {id: 2, medicalName: 'G'}});
    mockMuscleService.getMuscle.mockImplementation((id: number) => of(id === 2 ? group : memberOf(id)));

    fixture.componentRef.setInput('id', '3');
    fixture.detectChanges();
    expect(component.tiles().map(t => t.id)).toEqual([2, 3, 4]);

    mockMuscleService.getMuscle.mockClear();
    fixture.componentRef.setInput('id', '4');
    fixture.detectChanges();
    expect(mockMuscleService.getMuscle.mock.calls.map(c => c[0])).toEqual([4]);
    expect(component.tiles().map(t => t.id)).toEqual([2, 3, 4]);
  });

  it('keeps the scene on screen while another muscle loads', () => {
    mockMuscleService.getMuscle.mockReturnValue(NEVER);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    expect(component.isLoading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-muscle-scene')).not.toBeNull();
  });

  it('credits the model under the tiles of a muscle absent from it', () => {
    mockMuscleService.getMuscle.mockReturnValue(of({...mockMuscle, id: 2, members: [{id: 3, medicalName: 'A'}]}));
    fixture.componentRef.setInput('id', '2');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-muscle-credit')).not.toBeNull();
  });

  it('shows the medical name beside the title only when the title does not say it', () => {
    translateService.use('fr');
    expect(component.medicalNameShown({...mockMuscle, commonNameFr: 'Droit fémoral', medicalName: 'Rectus Femoris'})).toBe('Rectus Femoris');
    expect(component.medicalNameShown({...mockMuscle, commonNameFr: 'Latissimus Dorsi (Lats)', medicalName: 'Latissimus Dorsi'})).toBeUndefined();
    expect(component.medicalNameShown({...mockMuscle, commonNameFr: null, medicalName: 'Sartorius'})).toBeUndefined();
  });

  it('has no tile for a standalone muscle', () => {
    expect(component.tiles()).toEqual([]);
    expect(fixture.nativeElement.querySelector('[data-slot="muscle-members"]')).toBeNull();
  });

  it('shows no credit for a muscle absent from the 3D model', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-muscle-credit')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-slot="muscle-scene-empty"]')).not.toBeNull();
  });
});
