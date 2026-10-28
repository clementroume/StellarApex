import {TestBed} from '@angular/core/testing';
import {provideRouter, Router, UrlTree} from '@angular/router';
import {gymPermissionGuard} from './gym-permission.guard';
import {AccessService} from '../services/access.service';

describe('gymPermissionGuard', () => {
  const run = (gyms: unknown[]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), {provide: AccessService, useValue: {gymsWithPermission: () => gyms}}]
    });
    return TestBed.runInInjectionContext(() => gymPermissionGuard('MANAGE_MEMBERSHIPS')({} as never, {} as never));
  };

  it('should open the screen to staff holding the permission', () => {
    expect(run([{gymId: 4}])).toBe(true);
  });

  it('should send others back to My box', () => {
    const result = run([]) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/box');
  });
});
