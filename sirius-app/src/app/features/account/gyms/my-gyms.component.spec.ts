import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {MyGymsComponent} from './my-gyms.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MembershipSummary} from '../../../api/antares/models/membership.model';
import {APP_ICONS} from '../../../app.config';

describe('MyGymsComponent', () => {
  let fixture: ComponentFixture<MyGymsComponent>;
  let currentUser: WritableSignal<any>;
  let gymService: any;
  let userService: any;
  let notificationService: any;

  const membership = (gymId: number, overrides: Partial<MembershipSummary> = {}): MembershipSummary => ({
    gymId, gymName: `Gym ${gymId}`, gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [],
    gymProgramming: false, usable: true, ...overrides
  });
  const text = () => fixture.nativeElement.textContent as string;
  const buttons = () => Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];

  beforeEach(async () => {
    currentUser = signal({id: 1, memberships: []});
    gymService = {
      leaveGym: vi.fn().mockName('GymService.leaveGym').mockReturnValue(of(undefined)),
      deleteGym: vi.fn().mockName('GymService.deleteGym').mockReturnValue(of(undefined))
    };
    userService = {refreshCurrentUser: vi.fn().mockName('UserService.refreshCurrentUser').mockReturnValue(of({}))};
    notificationService = {showSuccess: vi.fn(), showError: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [MyGymsComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser}},
        {provide: UserService, useValue: userService},
        {provide: GymService, useValue: gymService},
        {provide: NotificationService, useValue: notificationService}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(MyGymsComponent);
  });

  it('should say when the user has no gym', () => {
    fixture.detectChanges();
    expect(text()).toContain('MY_GYMS.NONE');
  });

  it('should show role, statuses and permissions, and keep former memberships apart', () => {
    currentUser.set({
      id: 1, memberships: [
        membership(1, {gymRole: 'COACH', permissions: ['WOD_WRITE']}),
        membership(2, {status: 'PENDING', usable: false}),
        membership(3, {status: 'INACTIVE', usable: false, gymName: 'Old Box'})
      ]
    });
    fixture.detectChanges();

    expect(text()).toContain('GYM.ROLES.COACH');
    expect(text()).toContain('GYM.PERMISSIONS.WOD_WRITE.DESCRIPTION');
    expect(text()).toContain('MY_GYMS.PENDING_HINT');
    expect(fixture.nativeElement.querySelectorAll('[data-slot="my-gym"]').length).toBe(2);
    expect(fixture.nativeElement.querySelector('[data-slot="former-gym"]').textContent).toContain('Old Box');
  });

  it('should offer leave to a member, withdraw to a pending one, and neither to the holder', () => {
    currentUser.set({
      id: 1, memberships: [
        membership(1),
        membership(2, {status: 'PENDING', usable: false}),
        membership(3, {gymRole: 'OWNER'})
      ]
    });
    fixture.detectChanges();

    const labels = buttons().map(b => b.textContent!.trim());
    expect(labels.filter(l => l.includes('MY_GYMS.LEAVE')).length).toBe(1);
    expect(labels.filter(l => l.includes('MY_GYMS.WITHDRAW')).length).toBe(1);
    expect(text()).toContain('MY_GYMS.HOLDER_HINT');
  });

  it('should leave after confirmation and reload the user', () => {
    currentUser.set({id: 1, memberships: [membership(4)]});
    fixture.detectChanges();
    buttons().find(b => b.textContent!.includes('MY_GYMS.LEAVE'))!.click();
    expect(fixture.componentInstance.pendingAction()?.kind).toBe('leave');

    fixture.componentInstance.confirm();

    expect(gymService.leaveGym).toHaveBeenCalledWith(4);
    expect(userService.refreshCurrentUser).toHaveBeenCalled();
    expect(notificationService.showSuccess).toHaveBeenCalledWith('MY_GYMS.LEFT');
    expect(fixture.componentInstance.pendingAction()).toBeNull();
  });

  it('should delete a rejected request of the holder', () => {
    currentUser.set({id: 1, memberships: [membership(5, {gymRole: 'OWNER', gymStatus: 'REJECTED', usable: false})]});
    fixture.detectChanges();
    buttons().find(b => b.textContent!.includes('MY_GYMS.DELETE_REQUEST'))!.click();
    fixture.componentInstance.confirm();

    expect(gymService.deleteGym).toHaveBeenCalledWith(5);
  });

  it('should show the backend message when leaving is refused', () => {
    gymService.leaveGym.mockReturnValue(throwError(() => ({status: 409, error: {detail: 'Transfer first'}})));
    currentUser.set({id: 1, memberships: [membership(4)]});
    fixture.detectChanges();
    fixture.componentInstance.ask('leave', membership(4));
    fixture.componentInstance.confirm();

    expect(notificationService.showError).toHaveBeenCalledWith('Transfer first');
    expect(userService.refreshCurrentUser).not.toHaveBeenCalled();
  });
});
