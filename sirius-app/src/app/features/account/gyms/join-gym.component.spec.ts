import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {GYM_SEARCH_DEBOUNCE_MS, JoinGymComponent} from './join-gym.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {APP_ICONS} from '../../../app.config';

describe('JoinGymComponent', () => {
  let fixture: ComponentFixture<JoinGymComponent>;
  let component: JoinGymComponent;
  let gymService: any;
  let userService: any;
  let notificationService: any;

  const sparta = {id: 1, name: 'Spartacus CrossFit', description: 'Box', isProgramming: false};
  const zeus = {id: 2, name: 'Zeus Programming', isProgramming: true};

  beforeEach(async () => {
    vi.useFakeTimers();
    gymService = {
      searchGyms: vi.fn().mockName('GymService.searchGyms').mockReturnValue(of({content: [sparta, zeus], last: true})),
      getGym: vi.fn().mockName('GymService.getGym').mockReturnValue(of(sparta)),
      joinGym: vi.fn().mockName('GymService.joinGym').mockReturnValue(of({gymId: 1, status: 'PENDING'}))
    };
    userService = {refreshCurrentUser: vi.fn().mockReturnValue(of({}))};
    notificationService = {showSuccess: vi.fn(), showError: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [JoinGymComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser: signal({id: 1, memberships: [{gymId: 2, status: 'ACTIVE'}]})}},
        {provide: UserService, useValue: userService},
        {provide: GymService, useValue: gymService},
        {provide: NotificationService, useValue: notificationService}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(JoinGymComponent);
    component = fixture.componentInstance;
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => vi.useRealTimers());

  it('should list active gyms and mark the ones the user already belongs to', () => {
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('[data-slot="gym-result"]');
    expect(rows.length).toBe(2);
    expect(rows[1].textContent).toContain('JOIN_GYM.MEMBER');
  });

  it('should search by name after a pause and filter by type', () => {
    fixture.detectChanges();
    component.onSearchChange('spar');
    vi.advanceTimersByTime(GYM_SEARCH_DEBOUNCE_MS);
    expect(gymService.searchGyms).toHaveBeenLastCalledWith('spar', null, 0, 20);

    component.onTypeChange('PROGRAM');
    expect(gymService.searchGyms).toHaveBeenLastCalledWith('spar', 'PROGRAM', 0, 20);
  });

  it('should pre-fill the gym and code of an invitation link', () => {
    fixture.componentRef.setInput('gym', '1');
    fixture.componentRef.setInput('code', 'SPARTA');
    fixture.detectChanges();

    expect(gymService.getGym).toHaveBeenCalledWith(1);
    expect(component.selected()).toEqual(sparta);
    expect(component.form.getRawValue().enrollmentCode).toBe('SPARTA');
    expect(fixture.nativeElement.querySelector('[data-slot="join-form"]').textContent).toContain('JOIN_GYM.INVITATION');
  });

  it('should warn about an invitation to an unknown gym', () => {
    gymService.getGym.mockReturnValue(throwError(() => ({status: 404})));
    fixture.componentRef.setInput('gym', '99');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('JOIN_GYM.INVITATION_NOT_FOUND');
  });

  it('should join with the code, then reload the user', () => {
    fixture.detectChanges();
    component.select(sparta);
    component.form.setValue({enrollmentCode: ' sparta '});
    component.join();

    expect(gymService.joinGym).toHaveBeenCalledWith({gymId: 1, enrollmentCode: 'sparta'});
    expect(notificationService.showSuccess).toHaveBeenCalledWith('JOIN_GYM.REQUESTED');
    expect(userService.refreshCurrentUser).toHaveBeenCalled();
  });

  it('should require the code and show the backend message on refusal', () => {
    fixture.detectChanges();
    component.select(sparta);
    component.join();
    expect(gymService.joinGym).not.toHaveBeenCalled();

    gymService.joinGym.mockReturnValue(throwError(() => ({status: 400, error: {detail: 'Invalid code'}})));
    component.form.setValue({enrollmentCode: 'WRONG'});
    component.join();
    expect(notificationService.showError).toHaveBeenCalledWith('Invalid code');
  });
});
