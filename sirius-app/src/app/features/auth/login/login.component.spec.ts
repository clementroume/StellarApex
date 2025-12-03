import {ComponentFixture, TestBed} from '@angular/core/testing';
import {LoginComponent} from './login.component';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService} from '../../../core/services/auth.service';
import {of} from 'rxjs';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);
    authServiceSpy.login.and.returnValue(of({} as any));
    await TestBed.configureTestingModule({
      imports: [LoginComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthService, useValue: authServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    spyOn(component, 'redirectToExternal');
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
    expect(component.loginForm.valid).toBeFalse();
  });

  it('should make the form valid when fields are filled correctly', () => {
    component.loginForm.controls['email'].setValue('test@example.com');
    component.loginForm.controls['password'].setValue('password123');
    expect(component.loginForm.valid).toBeTrue();
  });

  it('should call redirectToExternal when returnUrl starts with http', () => {
    // Arrange
    component.loginForm.controls['email'].setValue('test@test.com');
    component.loginForm.controls['password'].setValue('password');
    // On force une URL externe via la propriété privée (ou via queryParams si on mockait ActivatedRoute)
    (component as any).returnUrl = 'http://external-site.com';

    // Act
    component.onSubmit();

    // Assert
    expect(component.redirectToExternal).toHaveBeenCalledWith('http://external-site.com');
  });
});
