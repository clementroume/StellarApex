import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SettingsComponent} from './settings.component';
import {HttpErrorResponse, provideHttpClient, withXhr} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {NotificationService} from '../../../core/services/notification.service';
import {of, throwError} from 'rxjs';
import {signal} from '@angular/core';
import {UserResponse} from '../../../api/antares/models/user.model';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let translateService: TranslateService;
  let authServiceSpy: MockedObject<AuthService>;
  let userServiceSpy: MockedObject<UserService>;
  let notificationServiceSpy: MockedObject<NotificationService>;

  beforeEach(async () => {
    TestBed.resetTestingModule();

    authServiceSpy = {
      currentUser: signal<UserResponse | null>({theme: 'light', locale: 'en'} as UserResponse)
    } as any;

    userServiceSpy = {
      changePassword: vi.fn().mockName("UserService.changePassword"),
      updatePreferences: vi.fn().mockName("UserService.updatePreferences"),
      deleteAccount: vi.fn().mockName("UserService.deleteAccount")
    } as any;

    notificationServiceSpy = {
      showSuccess: vi.fn().mockName("NotificationService.showSuccess"),
      showError: vi.fn().mockName("NotificationService.showError")
    } as any;

    await TestBed.configureTestingModule({
      imports: [
        SettingsComponent,
        TranslateModule.forRoot()
      ],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: AuthService, useValue: authServiceSpy},
        {provide: UserService, useValue: userServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    translateService = TestBed.inject(TranslateService);

    translateService.setFallbackLang('en');
    translateService.use('en');

    fixture.detectChanges();

    component.deleteModal = {
      nativeElement: {
        showModal: vi.fn().mockName("HTMLDialogElement.showModal"),
        close: vi.fn().mockName("HTMLDialogElement.close")
      } as any
    };
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Password Form', () => {
    it('should initialize the password form', () => {
      expect(component.passwordForm).toBeDefined();
      expect(component.passwordForm.valid).toBe(false);
    });

    it('should have a password mismatch error if passwords do not match', () => {
      const form = component.passwordForm;
      form.controls['currentPassword'].setValue('12345678');
      form.controls['newPassword'].setValue('newPassword123');
      form.controls['confirmationPassword'].setValue('wrongPassword123');
      expect(form.hasError('passwordsMismatch')).toBe(true);
    });

    it('should be valid if all password fields are correct', () => {
      const form = component.passwordForm;
      form.controls['currentPassword'].setValue('12345678');
      form.controls['newPassword'].setValue('newPassword123');
      form.controls['confirmationPassword'].setValue('newPassword123');
      expect(form.valid).toBe(true);
    });

    it('should call authService.changePassword on submit', () => {
      const form = component.passwordForm;
      form.controls['currentPassword'].setValue('oldPassword123');
      form.controls['newPassword'].setValue('newPassword123A');
      form.controls['confirmationPassword'].setValue('newPassword123A');

      userServiceSpy.changePassword.mockReturnValue(of(void 0));

      component.onSubmit();

      expect(userServiceSpy.changePassword).toHaveBeenCalled();
      expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
      expect(form.pristine).toBe(true);
    });
  });

  describe('Delete Account', () => {
    it('should open the confirmation modal', () => {
      component.openDeleteConfirmation();
      expect(component.deleteModal.nativeElement.showModal).toHaveBeenCalled();
    });

    it('should call deleteAccount service on confirmation and close modal on success', () => {
      userServiceSpy.deleteAccount.mockReturnValue(of(void 0));

      component.confirmDeleteAccount();

      expect(userServiceSpy.deleteAccount).toHaveBeenCalled();
      expect(component.deleteModal.nativeElement.close).toHaveBeenCalled();
      expect(notificationServiceSpy.showSuccess).toHaveBeenCalledWith('SETTINGS.SUCCESS_DELETE');
    });

    it('should show error notification if deletion fails', () => {
      const errorResponse = new HttpErrorResponse({
        error: {detail: 'Deletion failed'},
        status: 500
      });
      userServiceSpy.deleteAccount.mockReturnValue(throwError(() => errorResponse));

      component.confirmDeleteAccount();

      expect(component.deleteModal.nativeElement.close).toHaveBeenCalled();
      expect(notificationServiceSpy.showError).toHaveBeenCalledWith('Deletion failed');
    });
  });
});
