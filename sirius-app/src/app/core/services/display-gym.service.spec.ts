import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {DisplayGymService} from './display-gym.service';
import {AccessService} from './access.service';
import {MembershipSummary} from '../../api/antares/models/membership.model';

const membership = (gymId: number): MembershipSummary => ({
  gymId, gymName: 'Gym ' + gymId, gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: false
});

describe('DisplayGymService', () => {
  let memberships: WritableSignal<MembershipSummary[]>;

  const create = () => {
    TestBed.configureTestingModule({
      providers: [{provide: AccessService, useValue: {memberships}}]
    });
    return TestBed.inject(DisplayGymService);
  };

  beforeEach(() => {
    localStorage.clear();
    memberships = signal([membership(4), membership(7)]);
  });

  it('should show all boxes by default and offer a choice with two memberships', () => {
    const service = create();
    expect(service.gymId()).toBeNull();
    expect(service.hasChoice()).toBe(true);
    expect(service.matches(4)).toBe(true);
  });

  it('should remember the chosen box on this device', () => {
    create().select(7);
    TestBed.tick();
    expect(localStorage.getItem('displayGymId')).toBe('7');

    TestBed.resetTestingModule();
    const again = create();
    expect(again.gymId()).toBe(7);
    expect(again.membership()?.gymName).toBe('Gym 7');
    expect(again.matches(4)).toBe(false);
    expect(again.matches(7)).toBe(true);
  });

  it('should fall back to all when the stored box is no longer a usable membership', () => {
    localStorage.setItem('displayGymId', '7');
    const service = create();
    memberships.set([membership(4)]);
    expect(service.gymId()).toBeNull();
    expect(service.hasChoice()).toBe(false);
  });
});
