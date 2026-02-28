import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ProfileComponent} from './profile.component';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {signal} from '@angular/core';
import {UserResponse} from '../../../api/antares/models/user.model';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;

  const mockUser: UserResponse = {
    id: 1, email: 'test@test.com', firstName: 'John', lastName: 'Doe',
    platformRole: 'USER', memberships: [], locale: 'en', theme: 'light',
    createdAt: ''
  };

  beforeEach(async () => {
    const authServiceMock = {
      currentUser: signal(mockUser),
    };
    const userServiceSpy = jasmine.createSpyObj('UserService', ['updateProfile']);

    await TestBed.configureTestingModule({
      imports: [ProfileComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthService, useValue: authServiceMock},
        {provide: UserService, useValue: userServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize the form with the current user data on init', () => {
    expect(component.profileForm.value).toEqual({
      firstName: 'John',
      lastName: 'Doe',
      email: 'test@test.com'
    });
  });

  it('should switch to edit mode', () => {
    expect(component.isEditing).toBeFalse();
    component.enterEditMode();
    expect(component.isEditing).toBeTrue();
  });

  it('should cancel edit mode and revert form values', () => {
    component.enterEditMode();
    component.profileForm.controls['firstName'].setValue('Jane');
    component.onCancel();

    expect(component.isEditing).toBeFalse();
    expect(component.profileForm.controls['firstName'].value).toBe('John');
  });
});
