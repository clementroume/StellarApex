import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {BoxSettingsComponent} from './box-settings.component';
import {GymService} from '../../../api/antares/services/gym.service';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {MembershipService} from '../../../api/antares/services/membership.service';
import {UserService} from '../../../api/antares/services/user.service';
import {AccessService} from '../../../core/services/access.service';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {GymSettingsResponse} from '../../../api/antares/models/gym.model';
import {APP_ICONS} from '../../../app.config';

describe('BoxSettingsComponent', () => {
  let fixture: ComponentFixture<BoxSettingsComponent>;
  let component: BoxSettingsComponent;
  let gymService: any;
  let membershipService: any;
  let userService: any;

  const settings: GymSettingsResponse = {
    id: 4, name: 'Spartacus CrossFit', description: 'Box', isProgramming: false, status: 'ACTIVE',
    enrollmentCode: 'SPARTA 1', isAutoSubscription: false
  };
  const caps = (canBecomeHolder: boolean) => ({
    canChangeStatus: true, canChangeRole: true, canChangePermissions: true, canRemove: true, canBan: true, canBecomeHolder
  });
  const coach = {id: 50, user: {id: 5, firstName: 'Co', lastName: 'Ach'}, gymRole: 'COACH', status: 'ACTIVE', capabilities: caps(true)};
  const owner = {id: 51, user: {id: 1, firstName: 'Ow', lastName: 'Ner'}, gymRole: 'OWNER', status: 'ACTIVE', capabilities: caps(false)};

  async function setup(gymRole: string): Promise<void> {
    gymService = {
      getSettings: vi.fn().mockReturnValue(of(settings)),
      updateGym: vi.fn().mockReturnValue(of({...settings, name: 'Sparta'})),
      updateSettings: vi.fn().mockReturnValue(of(settings)),
      regenerateEnrollmentCode: vi.fn().mockReturnValue(of({...settings, enrollmentCode: 'NEWCODE1'})),
      transferOwnership: vi.fn().mockReturnValue(of([])),
      deleteGym: vi.fn().mockReturnValue(of(undefined))
    };
    membershipService = {getMemberships: vi.fn().mockReturnValue(of([coach, owner]))};
    userService = {refreshCurrentUser: vi.fn().mockReturnValue(of({}))};
    const membership = {gymId: 4, gymName: 'Spartacus CrossFit', gymRole, gymProgramming: false, usable: true};
    await TestBed.configureTestingModule({
      imports: [BoxSettingsComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: GymService, useValue: gymService},
        {provide: MembershipService, useValue: membershipService},
        {provide: UserService, useValue: userService},
        {provide: AccessService, useValue: {isAdmin: signal(false), gymsWithPermission: vi.fn().mockReturnValue([membership])}},
        {provide: DisplayGymService, useValue: {gymId: signal(null)}},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}},
        {
          provide: SessionService,
          useValue: {
            getSettings: vi.fn().mockReturnValue(of({defaultVisibility: 'AFTER_PARTICIPANTS', timeZone: 'Europe/Paris'})),
            updateSettings: vi.fn()
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BoxSettingsComponent);
    component = fixture.componentInstance;
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  it('should load the settings and build the invitation link', async () => {
    await setup('OWNER');
    expect(gymService.getSettings).toHaveBeenCalledWith(4);
    expect(component.identityForm.getRawValue().name).toBe('Spartacus CrossFit');
    expect(component.inviteLink()).toBe(`${location.origin}/join?gym=4&code=SPARTA%201`);
  });

  it('should rename the gym and reload the user', async () => {
    await setup('OWNER');
    component.identityForm.setValue({name: ' Sparta ', description: ''});
    component.saveIdentity();

    expect(gymService.updateGym).toHaveBeenCalledWith(4, {name: 'Sparta', description: null});
    expect(userService.refreshCurrentUser).toHaveBeenCalled();
  });

  it('should regenerate the code after confirmation', async () => {
    await setup('OWNER');
    component.openDialog('regenerate');
    component.confirmDialog();

    expect(gymService.regenerateEnrollmentCode).toHaveBeenCalledWith(4);
    expect(component.settings()?.enrollmentCode).toBe('NEWCODE1');
  });

  it('should offer the transfer to members Antares allows, and transfer after confirmation', async () => {
    await setup('OWNER');
    expect(component.transferCandidates().map(m => m.id)).toEqual([50]);

    component.onTransferTo('50');
    component.openDialog('transfer');
    component.confirmDialog();
    expect(gymService.transferOwnership).toHaveBeenCalledWith(4, 50);
    expect(userService.refreshCurrentUser).toHaveBeenCalled();
  });

  it('should delete only once the name is typed', async () => {
    await setup('OWNER');
    component.openDialog('delete');
    component.deleteConfirmation.set('wrong');
    component.confirmDialog();
    expect(gymService.deleteGym).not.toHaveBeenCalled();

    component.openDialog('delete');
    component.deleteConfirmation.set('Spartacus CrossFit');
    component.confirmDialog();
    expect(gymService.deleteGym).toHaveBeenCalledWith(4);
  });

  it('should hide the danger zone from a coach holding MANAGE_SETTINGS', async () => {
    await setup('COACH');
    expect(fixture.nativeElement.querySelector('[data-slot="danger-zone"]')).toBeNull();
    expect(membershipService.getMemberships).not.toHaveBeenCalled();
  });
});
