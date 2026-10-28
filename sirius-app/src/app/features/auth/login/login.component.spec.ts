import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {LoginComponent} from './login.component';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of} from 'rxjs';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: MockedObject<AuthService>;

  beforeEach(async () => {
    authServiceSpy = {
      login: vi.fn().mockName("AuthService.login")
    } as any;
    authServiceSpy.login.mockReturnValue(of({} as any));
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: authServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    vi.spyOn(component, 'redirectToExternal').mockImplementation(() => {
    });
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize the login form', () => {
    expect(component.loginForm).toBeDefined();
    expect(component.loginForm.controls['email']).toBeDefined();
    expect(component.loginForm.controls['password']).toBeDefined();
  });

  it('should make the form invalid when fields are empty', () => {
    expect(component.loginForm.valid).toBe(false);
  });

  it('should make the form valid when fields are filled correctly', () => {
    component.loginForm.controls['email'].setValue('test@example.com');
    component.loginForm.controls['password'].setValue('password123');
    expect(component.loginForm.valid).toBe(true);
  });

  it('should redirect the browser to a checked external target (admin tools behind Traefik)', () => {
    component.loginForm.controls['email'].setValue('test@test.com');
    component.loginForm.controls['password'].setValue('password');
    (component as any).target = {kind: 'external', url: 'https://docs.stellar.apex/'};

    component.onSubmit();

    expect(component.redirectToExternal).toHaveBeenCalledWith('https://docs.stellar.apex/');
  });

  it('should go to the app path of the returnUrl, query string included', () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    component.loginForm.controls['email'].setValue('test@test.com');
    component.loginForm.controls['password'].setValue('password');
    (component as any).target = {kind: 'internal', url: '/my-account/gyms/join?gym=4&code=X'};

    component.onSubmit();

    expect(navigateByUrl).toHaveBeenCalledWith('/my-account/gyms/join?gym=4&code=X');
    expect(component.redirectToExternal).not.toHaveBeenCalled();
  });

  it('should hand the returnUrl to the sign-up link', () => {
    (component as any).forwardReturnUrl = '/join?gym=4&code=X';
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href^="/auth/register"]');
    expect(decodeURIComponent(link.getAttribute('href')!)).toBe('/auth/register?returnUrl=/join?gym=4&code=X');
  });
});
