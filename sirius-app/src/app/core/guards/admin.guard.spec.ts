import {TestBed} from '@angular/core/testing';
import {CanActivateFn, Router} from '@angular/router';
import {signal, WritableSignal} from '@angular/core';
import {vi} from 'vitest';
import {adminGuard} from './admin.guard';
import {AccessService} from '../services/access.service';

describe('adminGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) => TestBed.runInInjectionContext(() => adminGuard(...guardParameters));

  let isAdmin: WritableSignal<boolean>;
  let mockRouter: any;

  beforeEach(() => {
    isAdmin = signal(false);
    mockRouter = {navigate: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        {provide: AccessService, useValue: {isAdmin}},
        {provide: Router, useValue: mockRouter}
      ]
    });
  });

  it('should allow access to an admin', () => {
    isAdmin.set(true);
    expect(executeGuard({} as any, {} as any)).toBe(true);
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should block a regular user (or an admin acting as one) and redirect', () => {
    expect(executeGuard({} as any, {} as any)).toBe(false);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/today']);
  });
});
