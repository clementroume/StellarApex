import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {UserResponse} from '../../../api/antares/models/user.model';
import {NotificationService} from '../../../core/services/notification.service';
import {UnitService} from '../../../core/services/unit.service';
import {AthleteProfileComponent} from './athlete-profile.component';

describe('AthleteProfileComponent', () => {
  let fixture: ComponentFixture<AthleteProfileComponent>;
  let component: AthleteProfileComponent;
  const user = {id: 1, division: 'WOMEN', birthYear: 1992, heightCm: 168} as UserResponse;
  const userService = {
    getBodyWeights: vi.fn().mockName('UserService.getBodyWeights'),
    updateAthleteProfile: vi.fn().mockName('UserService.updateAthleteProfile'),
    recordBodyWeight: vi.fn().mockName('UserService.recordBodyWeight'),
    deleteBodyWeight: vi.fn().mockName('UserService.deleteBodyWeight')
  };
  const notifications = {
    showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
    showError: vi.fn().mockName('NotificationService.showError')
  };
  // Imperial user: heights in inches, weights in pounds
  const units = {
    heightUnit: signal('in'),
    massUnit: signal('LBS'),
    heightFromCm: (cm: number) => Math.round(cm / 2.54),
    heightToCm: (inches: number) => Math.round(inches * 2.54),
    bodyWeightToKg: (lb: number) => Number((lb * 0.45359237).toFixed(2)),
    formatBodyWeight: (kg: number) => `${kg} kg`
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    userService.getBodyWeights.mockReturnValue(of([
      {id: 2, measuredOn: '2026-10-28', weightKg: 61.2},
      {id: 1, measuredOn: '2026-10-01', weightKg: 62}
    ]));
    await TestBed.configureTestingModule({
      imports: [AthleteProfileComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser: signal(user)}},
        {provide: UserService, useValue: userService},
        {provide: NotificationService, useValue: notifications},
        {provide: UnitService, useValue: units}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should show the profile in the user units, the privacy note and the weight history', () => {
    expect(component.division()).toBe('WOMEN');
    expect(component.profileForm.getRawValue()).toEqual({birthYear: 1992, height: 66});
    expect(component.latest()?.weightKg).toBe(61.2);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('ATHLETE_PROFILE.PRIVACY');
    expect(text).toContain('61.2 kg');
  });

  it('should save the division, birth year and height in cm (null clears)', () => {
    userService.updateAthleteProfile.mockReturnValue(of(user));
    component.onDivision('MEN');
    component.profileForm.patchValue({birthYear: null, height: 70});

    component.saveProfile();

    expect(userService.updateAthleteProfile).toHaveBeenCalledWith({division: 'MEN', birthYear: null, heightCm: 178});
    expect(notifications.showSuccess).toHaveBeenCalled();
  });

  it('should refuse a birth year in the future', () => {
    component.profileForm.patchValue({birthYear: new Date().getFullYear() + 1});
    component.saveProfile();
    expect(userService.updateAthleteProfile).not.toHaveBeenCalled();
  });

  it('should record a weight in kg and reload the history, and delete a measure', () => {
    userService.recordBodyWeight.mockReturnValue(of({id: 3, measuredOn: '2026-10-29', weightKg: 60}));
    userService.deleteBodyWeight.mockReturnValue(of(undefined));
    component.weightForm.setValue({weight: 132.3, measuredOn: '2026-10-29'});

    component.addWeight();

    expect(userService.recordBodyWeight).toHaveBeenCalledWith({weightKg: 60.01, measuredOn: '2026-10-29'});
    expect(userService.getBodyWeights).toHaveBeenCalledTimes(2);

    component.deleteWeight({id: 1, measuredOn: '2026-10-01', weightKg: 62});
    expect(userService.deleteBodyWeight).toHaveBeenCalledWith(1);
    expect(component.weights().map(w => w.id)).toEqual([2]);
  });
});
