import {ComponentFixture, TestBed} from '@angular/core/testing';
import {RegisterComponent} from './register.component';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of} from 'rxjs';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);
    authServiceSpy.register.and.returnValue(of({} as any));

    await TestBed.configureTestingModule({
      imports: [RegisterComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthService, useValue: authServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize the register form with confirmation password', () => {
    expect(component.registerForm.controls['password']).toBeDefined();
    expect(component.registerForm.controls['confirmationPassword']).toBeDefined();
    expect(component.registerForm.valid).toBeFalse();
  });

  it('should be invalid if passwords do not match', () => {
    const form = component.registerForm;
    form.controls['firstName'].setValue('John');
    form.controls['lastName'].setValue('Doe');
    form.controls['email'].setValue('john@test.com');
    form.controls['password'].setValue('Password123');
    form.controls['confirmationPassword'].setValue('Mismatch123');

    expect(form.hasError('passwordsMismatch')).toBeTrue();
    expect(form.valid).toBeFalse();
  });

  it('should be valid and call register (without confirm password) when fields are correct', () => {
    const form = component.registerForm;
    form.controls['firstName'].setValue('John');
    form.controls['lastName'].setValue('Doe');
    form.controls['email'].setValue('john.doe@example.com');
    form.controls['password'].setValue('Password123');
    form.controls['confirmationPassword'].setValue('Password123');

    expect(form.valid).toBeTrue();

    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
      password: 'Password123'
    });
  });
});
