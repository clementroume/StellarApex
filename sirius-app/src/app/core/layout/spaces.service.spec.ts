import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {Component} from '@angular/core';
import {SpacesService} from './spaces.service';
import {AccessService} from '../services/access.service';
import {MembershipSummary, Permission} from '../../api/antares/models/membership.model';

@Component({standalone: true, template: ''})
class BlankComponent {
}

describe('SpacesService', () => {
  let memberships: WritableSignal<MembershipSummary[]>;
  let isAdmin: WritableSignal<boolean>;
  let service: SpacesService;

  const membership = (permissions: Permission[]): MembershipSummary => ({
    gymId: 4, gymName: 'Test Box', gymStatus: 'ACTIVE', gymRole: permissions.length ? 'COACH' : 'ATHLETE',
    status: 'ACTIVE', permissions, gymProgramming: false
  });

  beforeEach(() => {
    memberships = signal([]);
    isAdmin = signal(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{path: '**', component: BlankComponent}]),
        {
          provide: AccessService,
          useValue: {
            memberships, isAdmin,
            gymsWithPermission: (p: Permission) => memberships().filter(m => m.permissions.includes(p))
          }
        }
      ]
    });
    service = TestBed.inject(SpacesService);
  });

  const keys = () => service.spaces().map(s => s.key);

  it('should hide My box without membership and Admin for a non admin', () => {
    expect(keys()).toEqual(['today', 'training', 'catalog']);
  });

  it('should show My box to members and its staff entries by permission', () => {
    memberships.set([membership([])]);
    const athleteBox = service.spaces().find(s => s.key === 'box')!;
    expect(athleteBox.children.map(c => c.link)).toEqual(['/box/sessions', '/box/leaderboards']);

    memberships.set([membership(['MANAGE_MEMBERSHIPS'])]);
    const coachBox = service.spaces().find(s => s.key === 'box')!;
    expect(coachBox.children.map(c => c.link)).toContain('/box/members');
    expect(coachBox.children.map(c => c.link)).not.toContain('/box/settings');
  });

  it('should show Admin to a platform admin', () => {
    isAdmin.set(true);
    expect(keys()).toContain('admin');
  });

  it('should find the space of the current URL, catalogue included', async () => {
    await TestBed.inject(Router).navigateByUrl('/movements/3');
    expect(service.current()?.key).toBe('catalog');

    await TestBed.inject(Router).navigateByUrl('/my-account/profile');
    expect(service.current()).toBeNull();
  });
});
