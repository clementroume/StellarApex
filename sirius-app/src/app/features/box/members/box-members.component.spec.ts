import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {BoxMembersComponent} from './box-members.component';
import {MembershipService} from '../../../api/antares/services/membership.service';
import {AccessService} from '../../../core/services/access.service';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MembershipCapabilities, MembershipResponse} from '../../../api/antares/models/membership.model';
import {APP_ICONS} from '../../../app.config';

describe('BoxMembersComponent', () => {
  let fixture: ComponentFixture<BoxMembersComponent>;
  let component: BoxMembersComponent;
  let membershipService: any;

  const none: MembershipCapabilities = {
    canChangeStatus: false, canChangeRole: false, canChangePermissions: false, canRemove: false, canBan: false, canBecomeHolder: false
  };
  const member = (id: number, overrides: Partial<MembershipResponse> = {}): MembershipResponse => ({
    id,
    user: {id: id * 10, firstName: 'User', lastName: String(id), email: `u${id}@box.com`, platformRole: 'USER'},
    gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], createdAt: '2026-10-01T10:00:00', capabilities: none,
    ...overrides
  });
  const coachCaps = {...none, canChangeStatus: true, canRemove: true};
  const ownerCaps = {...none, canChangeStatus: true, canChangeRole: true, canChangePermissions: true, canRemove: true, canBan: true};
  const buttonsOf = (row: Element) => Array.from(row.querySelectorAll('button')).map(b => b.textContent!.trim());

  const box = {gymId: 4, gymName: 'Box', gymStatus: 'ACTIVE', gymRole: 'COACH', status: 'ACTIVE', permissions: ['MANAGE_MEMBERSHIPS'], gymProgramming: false, usable: true};

  async function setup(members: MembershipResponse[]): Promise<void> {
    membershipService = {
      getMemberships: vi.fn().mockName('MembershipService.getMemberships').mockReturnValue(of(members)),
      updateMembership: vi.fn().mockName('MembershipService.updateMembership')
        .mockImplementation((id: number, payload: any) => of(member(id, {...payload, capabilities: ownerCaps}))),
      deleteMembership: vi.fn().mockName('MembershipService.deleteMembership').mockReturnValue(of(undefined))
    };
    await TestBed.configureTestingModule({
      imports: [BoxMembersComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: MembershipService, useValue: membershipService},
        {
          provide: AccessService, useValue: {
            userId: signal(10), isAdmin: signal(false),
            gymsWithPermission: vi.fn().mockReturnValue([box]),
            hasGymPermission: vi.fn().mockReturnValue(false)
          }
        },
        {provide: DisplayGymService, useValue: {gymId: signal(null)}},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BoxMembersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should load the managed gym and list pending requests first', async () => {
    await setup([member(1), member(2, {status: 'PENDING', capabilities: coachCaps})]);

    expect(membershipService.getMemberships).toHaveBeenCalledWith(4);
    const pending = fixture.nativeElement.querySelector('[data-slot="pending-members"]');
    expect(pending.textContent).toContain('User 2');
    expect(buttonsOf(pending.querySelector('[data-slot="member"]'))).toEqual(['BOX.MEMBERS.VALIDATE', 'BOX.MEMBERS.REFUSE']);
  });

  it('should only show the actions the capabilities allow', async () => {
    await setup([member(1, {capabilities: coachCaps}), member(2, {gymRole: 'OWNER'}), member(3, {capabilities: ownerCaps})]);
    const rows = fixture.nativeElement.querySelectorAll('[data-slot="members"] [data-slot="member"]');

    expect(buttonsOf(rows[0])).toEqual(['BOX.MEMBERS.REMOVE']);
    expect(buttonsOf(rows[1])).toEqual([]);
    expect(buttonsOf(rows[2])).toEqual(['BOX.MEMBERS.EDIT', 'BOX.MEMBERS.REMOVE', 'BOX.MEMBERS.BAN']);
  });

  it('should validate a request', async () => {
    const request = member(2, {status: 'PENDING', capabilities: coachCaps});
    await setup([request]);
    component.setStatus(request, 'ACTIVE');

    expect(membershipService.updateMembership).toHaveBeenCalledWith(2, {status: 'ACTIVE', gymRole: 'ATHLETE', permissions: []});
    expect(component.members()[0].status).toBe('ACTIVE');
  });

  it('should refuse a request after confirmation and reload', async () => {
    const request = member(2, {status: 'PENDING', capabilities: coachCaps});
    await setup([request]);
    component.ask('refuse', request);
    component.confirmAction();

    expect(membershipService.deleteMembership).toHaveBeenCalledWith(2);
    expect(membershipService.getMemberships).toHaveBeenCalledTimes(2);
  });

  it('should send the role and coach permissions, none for an athlete', async () => {
    const athlete = member(3, {capabilities: ownerCaps});
    await setup([athlete]);
    component.startEdit(athlete);
    component.onEditRole('COACH');
    component.togglePermission('SCORE_VERIFY', true);
    component.togglePermission('WOD_WRITE', true);
    component.saveEdit(athlete);
    expect(membershipService.updateMembership).toHaveBeenLastCalledWith(3,
      {status: 'ACTIVE', gymRole: 'COACH', permissions: ['WOD_WRITE', 'SCORE_VERIFY']});

    component.startEdit(athlete);
    component.togglePermission('WOD_WRITE', true);
    component.saveEdit(athlete);
    expect(membershipService.updateMembership).toHaveBeenLastCalledWith(3, {status: 'ACTIVE', gymRole: 'ATHLETE', permissions: []});
  });

  it('should filter by status and role', async () => {
    await setup([member(1), member(2, {status: 'INACTIVE'}), member(3, {gymRole: 'COACH'})]);
    component.onStatusFilter('INACTIVE');
    expect(component.others().map(m => m.id)).toEqual([2]);
    component.onStatusFilter(null);
    component.onRoleFilter('COACH');
    expect(component.others().map(m => m.id)).toEqual([3]);
  });
});
