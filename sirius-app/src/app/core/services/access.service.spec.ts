import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {AccessService} from './access.service';
import {AuthService} from '../../api/antares/services/auth.service';
import {MembershipSummary} from '../../api/antares/models/membership.model';

describe('AccessService', () => {
  let currentUser: WritableSignal<any>;
  let service: AccessService;

  const membership = (gymId: number, overrides: Partial<MembershipSummary> = {}): MembershipSummary => ({
    gymId,
    gymName: `Gym ${gymId}`,
    gymStatus: 'ACTIVE',
    gymRole: 'ATHLETE',
    status: 'ACTIVE',
    permissions: [],
    ...overrides
  });

  beforeEach(() => {
    currentUser = signal({id: 1, platformRole: 'USER', memberships: []});
    TestBed.configureTestingModule({providers: [{provide: AuthService, useValue: {currentUser}}]});
    service = TestBed.inject(AccessService);
  });

  it('should keep only memberships accepted by Antares', () => {
    currentUser.set({
      id: 1, platformRole: 'USER', memberships: [
        membership(1, {status: 'PENDING'}),
        membership(2, {gymStatus: 'SUSPENDED'}),
        membership(3, {gymStatus: 'PENDING_APPROVAL', gymRole: 'OWNER'}),
        membership(4)
      ]
    });
    expect(service.memberships().map(m => m.gymId)).toEqual([3, 4]);
  });

  it('should check permissions per gym', () => {
    currentUser.set({
      id: 1, platformRole: 'USER', memberships: [
        membership(1, {gymRole: 'COACH', permissions: ['WOD_WRITE']}),
        membership(2, {gymRole: 'COACH', permissions: []})
      ]
    });

    expect(service.isAdmin()).toBe(false);
    expect(service.hasGymPermission(1, 'WOD_WRITE')).toBe(true);
    expect(service.hasGymPermission(2, 'WOD_WRITE')).toBe(false);
    expect(service.hasGymPermission(null, 'WOD_WRITE')).toBe(false);
    expect(service.gymsWithPermission('WOD_WRITE').map(m => m.gymId)).toEqual([1]);
  });

  it('should give every right to a platform admin', () => {
    currentUser.set({id: 1, platformRole: 'ADMIN', memberships: [membership(1)]});

    expect(service.isAdmin()).toBe(true);
    expect(service.hasGymPermission(99, 'MANAGE_SETTINGS')).toBe(true);
    expect(service.gymsWithPermission('WOD_WRITE').map(m => m.gymId)).toEqual([1]);
  });

  it('should give the target user rights during a support session', () => {
    currentUser.set({
      id: 7, platformRole: 'USER', memberships: [membership(1, {gymRole: 'COACH', permissions: []})],
      supportSession: {id: 1, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: ''}
    });

    expect(service.isAdmin()).toBe(false);
    expect(service.userId()).toBe(7);
    expect(service.hasGymPermission(1, 'WOD_WRITE')).toBe(false);
  });
});
