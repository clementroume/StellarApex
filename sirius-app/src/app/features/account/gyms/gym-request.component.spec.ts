import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {GymRequestComponent} from './gym-request.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {APP_ICONS} from '../../../app.config';

describe('GymRequestComponent', () => {
  let fixture: ComponentFixture<GymRequestComponent>;
  let currentUser: WritableSignal<any>;
  let gymService: any;
  let userService: any;

  beforeEach(async () => {
    currentUser = signal({id: 1, memberships: []});
    gymService = {createGym: vi.fn().mockName('GymService.createGym').mockReturnValue(of({id: 9}))};
    userService = {refreshCurrentUser: vi.fn().mockReturnValue(of({}))};

    await TestBed.configureTestingModule({
      imports: [GymRequestComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser}},
        {provide: UserService, useValue: userService},
        {provide: GymService, useValue: gymService},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(GymRequestComponent);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('should send a program request with a trimmed name', () => {
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.setType(true);
    component.form.patchValue({name: ' Zeus ', description: ' '});
    component.submit();

    expect(gymService.createGym).toHaveBeenCalledWith({name: 'Zeus', description: undefined, isProgramming: true});
    expect(userService.refreshCurrentUser).toHaveBeenCalled();
  });

  it('should not send without a name', () => {
    fixture.detectChanges();
    fixture.componentInstance.submit();
    expect(gymService.createGym).not.toHaveBeenCalled();
  });

  it('should say a request already waits instead of showing the form', () => {
    currentUser.set({
      id: 1, memberships: [{gymId: 3, gymName: 'Pending Box', gymStatus: 'PENDING_APPROVAL', gymRole: 'OWNER', status: 'ACTIVE'}]
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-slot="pending-request"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
});
