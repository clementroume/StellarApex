import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {staffGymSelection} from './staff-gym';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';

describe('staffGymSelection', () => {
  let displayed: WritableSignal<number | null>;
  let managed: WritableSignal<any[]>;

  beforeEach(() => {
    displayed = signal(null);
    managed = signal([{gymId: 4, gymName: 'Box'}, {gymId: 7, gymName: 'Zeus'}]);
    TestBed.configureTestingModule({
      providers: [
        {provide: AccessService, useValue: {gymsWithPermission: () => managed()}},
        {provide: DisplayGymService, useValue: {gymId: displayed}}
      ]
    });
  });

  const create = () => TestBed.runInInjectionContext(() => staffGymSelection('MANAGE_SETTINGS'));

  it('should default to the first managed gym', () => {
    expect(create().gymId()).toBe(4);
  });

  it('should follow the displayed box/prog when the user manages it, never another', () => {
    const selection = create();
    displayed.set(7);
    expect(selection.gymId()).toBe(7);
    displayed.set(99);
    expect(selection.gymId()).toBe(4);
  });

  it('should prefer the gym picked on the page while it stays manageable', () => {
    const selection = create();
    displayed.set(4);
    selection.select(7);
    expect(selection.gymId()).toBe(7);
    managed.set([{gymId: 4, gymName: 'Box'}]);
    expect(selection.gymId()).toBe(4);
    expect(selection.membership()?.gymName).toBe('Box');
  });
});
