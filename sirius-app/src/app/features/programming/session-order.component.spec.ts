import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../app.config';
import {OrderPart, OrderProposal} from '../../api/aldebaran/models/programming.model';
import {ProgrammingService} from '../../api/aldebaran/services/programming.service';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {NotificationService} from '../../core/services/notification.service';
import {SessionOrderComponent} from './session-order.component';

const part = (ids: number[], title: string, fromDay: OrderPart['fromDay'], pinned = false): OrderPart =>
  ({blockIds: ids, title, pinned, strength: false, fromDay});

/** One week: the WODs of Monday and Wednesday swapped, Friday locked. */
function proposal(pinned = false): OrderProposal {
  return {
    start: '2026-11-23', end: '2026-12-20', mode: 'WOD_ONLY', heavy: 'STRICT',
    before: {profilesInAlert: 5, excess: 9, sameDomain: 2, moves: 0},
    after: {profilesInAlert: 0, excess: 0, sameDomain: 1, moves: 2},
    weeks: [
      {
        monday: '2026-11-23', changed: true, days: [
          {day: 'MONDAY', date: '2026-11-23', sessionId: 1, locked: false,
            skill: part([11], 'Squat', 'MONDAY', pinned), wod: part([12], 'Fran', 'MONDAY'),
            skillAfter: part([11], 'Squat', 'MONDAY', pinned), wodAfter: part([32], 'Helen', 'WEDNESDAY')},
          {day: 'WEDNESDAY', date: '2026-11-25', sessionId: 3, locked: false,
            skill: part([31], 'Press', 'WEDNESDAY'), wod: part([32], 'Helen', 'WEDNESDAY'),
            skillAfter: part([31], 'Press', 'WEDNESDAY'), wodAfter: part([12], 'Fran', 'MONDAY')},
          {day: 'FRIDAY', date: '2026-11-27', sessionId: 5, locked: true,
            skill: part([51], 'Clean', 'FRIDAY'), wod: part([52], 'Grace', 'FRIDAY'),
            skillAfter: part([51], 'Clean', 'FRIDAY'), wodAfter: part([52], 'Grace', 'FRIDAY')}
        ]
      },
      {monday: '2026-11-30', changed: false, days: []}
    ]
  };
}

describe('SessionOrderComponent', () => {
  let fixture: ComponentFixture<SessionOrderComponent>;
  let service: {proposeOrder: ReturnType<typeof vi.fn>; applyOrder: ReturnType<typeof vi.fn>};
  let notifications: {showSuccess: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn>};

  async function setup(apply: () => unknown = () => of(undefined)) {
    service = {
      proposeOrder: vi.fn().mockName('ProgrammingService.proposeOrder').mockImplementation(() => of(proposal())),
      applyOrder: vi.fn().mockName('ProgrammingService.applyOrder').mockImplementation(apply)
    };
    notifications = {showSuccess: vi.fn(), showError: vi.fn()};
    await TestBed.configureTestingModule({
      imports: [SessionOrderComponent],
      providers: [
        provideRouter([]), provideIcons(APP_ICONS), provideTranslateService(),
        {provide: ProgrammingService, useValue: service},
        {provide: NotificationService, useValue: notifications},
        {provide: AccessService, useValue: {gymsWithPermission: () => [{gymId: 4, gymName: 'Box', gymRole: 'OWNER'}], staffGyms: () => []}},
        {provide: DisplayGymService, useValue: {gymId: signal(null)}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SessionOrderComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = (s: string) => fixture.nativeElement.querySelector(s) as HTMLElement | null;
  const all = (s: string) => [...fixture.nativeElement.querySelectorAll(s)] as HTMLElement[];
  const lastRequest = () => service.proposeOrder.mock.calls.at(-1)![1];

  it('asks for a WOD-only proposal of the current cycle and shows the weeks', async () => {
    await setup();
    expect(lastRequest()).toMatchObject({weeks: 4, mode: 'WOD_ONLY', heavy: 'STRICT', pinnedBlockIds: []});
    expect(el('[data-slot="order-summary"]')!.textContent).toContain('PROGRAMMING.ORDER.SUMMARY');
    expect(all('[data-slot="order-week"]')).toHaveLength(2);
    // Two WODs moved, the locked Friday cannot be pinned, the moves in words
    expect(all('[data-moved]')).toHaveLength(2);
    expect(all('[data-slot="order-day"]')[2].querySelector('[data-slot="order-pin"]')).toBeNull();
    expect(all('[data-slot="order-moves"] li')).toHaveLength(2);
    // Only the changed week can be kept, kept by default
    expect(all('[data-slot="order-keep"]')).toHaveLength(1);
    expect((el('[data-slot="order-keep"]') as HTMLInputElement).checked).toBe(true);
  });

  it('asks again when the mode changes or a part is pinned', async () => {
    await setup();
    (el('[data-mode="WOD_AND_SKILLS"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(lastRequest().mode).toBe('WOD_AND_SKILLS');
    expect(fixture.nativeElement.textContent).toContain('PROGRAMMING.ORDER.HEAVY');
    (all('[data-slot="order-pin"]')[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(lastRequest().pinnedBlockIds).toEqual([11]);
  });

  it('applies the kept weeks after confirmation, without the locked days', async () => {
    await setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.apply();
    expect(service.applyOrder).toHaveBeenCalledWith(4, {
      mode: 'WOD_ONLY',
      weeks: [{
        monday: '2026-11-23', days: [
          {sessionId: 1, skillBlockIds: [11], wodBlockIds: [32]},
          {sessionId: 3, skillBlockIds: [31], wodBlockIds: [12]}
        ]
      }]
    });
    expect(notifications.showSuccess).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/box/programming'], expect.anything());
  });

  it('says why an application failed and asks again', async () => {
    await setup(() => throwError(() => ({status: 409, error: {detail: 'locked'}})));
    const calls = service.proposeOrder.mock.calls.length;
    fixture.componentInstance.apply();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(notifications.showError).toHaveBeenCalledWith('locked');
    expect(service.proposeOrder.mock.calls.length).toBe(calls + 1);
  });
});
