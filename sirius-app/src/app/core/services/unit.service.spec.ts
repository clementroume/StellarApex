import type {MockedObject} from 'vitest';
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {of} from 'rxjs';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {UnitService} from './unit.service';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {UserResponse} from '../../api/antares/models/user.model';

describe('UnitService', () => {
  let service: UnitService;
  let translate: TranslateService;
  let currentUser: WritableSignal<UserResponse | null>;
  let userServiceSpy: MockedObject<UserService>;

  const user = (overrides: Partial<UserResponse> = {}): UserResponse => ({
    id: 1, email: 'a@b.c', firstName: 'A', lastName: 'B', platformRole: 'USER', memberships: [],
    locale: 'fr', theme: 'dark', createdAt: '', ...overrides
  });

  beforeEach(() => {
    try {
      localStorage.removeItem('unitSystem');
    } catch {
      // Storage unavailable: nothing to clean
    }
    currentUser = signal<UserResponse | null>(null);
    userServiceSpy = {updatePreferences: vi.fn().mockName('UserService.updatePreferences')} as any;
    userServiceSpy.updatePreferences.mockReturnValue(of(user()));

    TestBed.configureTestingModule({
      providers: [
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser}},
        {provide: UserService, useValue: userServiceSpy}
      ]
    });
    service = TestBed.inject(UnitService);
    translate = TestBed.inject(TranslateService);
  });

  it('should follow the language without an explicit choice (French: kg)', () => {
    translate.use('fr');
    expect(service.unitSystem()).toBe('METRIC');
    expect(service.massUnit()).toBe('KG');

    translate.use('en');
    expect(service.unitSystem()).toBe('IMPERIAL');
  });

  it('should use the choice saved in the account, except during a support session', () => {
    translate.use('fr');
    currentUser.set(user({unitSystem: 'IMPERIAL'}));
    expect(service.unitSystem()).toBe('IMPERIAL');

    currentUser.set(user({unitSystem: 'IMPERIAL', supportSession: {id: 1} as any}));
    expect(service.unitSystem()).toBe('METRIC');
  });

  it('should save a new choice with the other preferences unchanged', () => {
    translate.use('fr');
    currentUser.set(user({locale: 'fr', theme: 'light'}));

    service.setUnitSystem('IMPERIAL');

    expect(service.unitSystem()).toBe('IMPERIAL');
    expect(userServiceSpy.updatePreferences)
      .toHaveBeenCalledWith({locale: 'fr', theme: 'light', unitSystem: 'IMPERIAL'});
  });

  it('should not save the choice during a support session', () => {
    currentUser.set(user({supportSession: {id: 1} as any}));

    service.setUnitSystem('METRIC');

    expect(service.unitSystem()).toBe('METRIC');
    expect(userServiceSpy.updatePreferences).not.toHaveBeenCalled();
  });

  it('should convert loads to the display unit, rounded as gyms write them', () => {
    service.setUnitSystem('METRIC');
    expect(service.formatLoad(95, 65, 'LBS')).toBe('43/29 kg');
    expect(service.formatLoad(43, 30, 'KG')).toBe('43/30 kg');
    expect(service.formatLoad(135, null, 'LBS')).toBe('61 kg');

    service.setUnitSystem('IMPERIAL');
    expect(service.formatLoad(43, 30, 'KG')).toBe('95/66 lb');
    expect(service.formatLoad(95, 65, 'LBS')).toBe('95/65 lb');
  });

  it('should return an empty text without a load', () => {
    expect(service.formatLoad(undefined, undefined, 'KG')).toBe('');
    expect(service.formatDistance(null, null, 'METERS')).toBe('');
  });

  it('should convert imperial distances and heights to metric, rounded as gyms write them', () => {
    service.setUnitSystem('METRIC');
    expect(service.formatDistance(24, 20, 'INCHES')).toBe('60/50 cm');
    expect(service.formatDistance(10, 9, 'FEET')).toBe('3/2.7 m');
    expect(service.formatDistance(200, null, 'FEET')).toBe('61 m');
    expect(service.formatDistance(1, null, 'MILES')).toBe('1.6 km');
    expect(service.formatDistance(400, null, 'METERS')).toBe('400 m');

    translate.use('fr');
    expect(service.formatDistance(1, null, 'MILES')).toBe('1,6 km');
  });

  it('should keep runs and rows in meters in imperial, converting short distances and heights', () => {
    service.setUnitSystem('IMPERIAL');
    expect(service.formatDistance(400, null, 'METERS')).toBe('400 m');
    expect(service.formatDistance(2, null, 'KILOMETERS')).toBe('2 km');
    expect(service.formatDistance(15, null, 'METERS')).toBe('49 ft');
    expect(service.formatDistance(60, 50, 'CENTIMETERS')).toBe('24/20 in');
    expect(service.formatDistance(24, 20, 'INCHES')).toBe('24/20 in');
  });
});
