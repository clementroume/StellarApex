import {TestBed} from '@angular/core/testing';
import {CanActivateFn, Router} from '@angular/router';
import {adminGuard} from './admin.guard';
import {AuthService} from '../../api/antares/services/auth.service';
import {signal} from '@angular/core';

describe('adminGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => adminGuard(...guardParameters));

  let mockAuthService: any;
  let mockRouter: any;

  beforeEach(() => {
    mockAuthService = {
      currentUser: signal(null)
    };
    mockRouter = {
      navigate: jasmine.createSpy('navigate')
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: mockAuthService},
        {provide: Router, useValue: mockRouter}
      ]
    });
  });

  it('devrait autoriser l\'accès si l\'utilisateur est ADMIN', () => {
    mockAuthService.currentUser.set({platformRole: 'ADMIN'});
    expect(executeGuard({} as any, {} as any)).toBeTrue();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('devrait bloquer l\'accès et rediriger si l\'utilisateur est simple USER', () => {
    mockAuthService.currentUser.set({platformRole: 'USER'});
    expect(executeGuard({} as any, {} as any)).toBeFalse();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('devrait bloquer l\'accès et rediriger si l\'utilisateur n\'est pas connecté', () => {
    mockAuthService.currentUser.set(null);
    expect(executeGuard({} as any, {} as any)).toBeFalse();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
