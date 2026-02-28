import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SettingsComponent} from './settings.component';
import {HttpErrorResponse, provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {NotificationService} from '../../../core/services/notification.service';
import {of, throwError} from 'rxjs';
import {signal} from '@angular/core';
import {UserResponse} from '../../../api/antares/models/user.model';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let translateService: TranslateService;

  // Mocks
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let userServiceSpy: jasmine.SpyObj<UserService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    // Create mocks for business logic services
    authServiceSpy = jasmine.createSpyObj('AuthService', [], {
      currentUser: signal<UserResponse | null>({theme: 'light', locale: 'en'} as UserResponse)
    });
    userServiceSpy = jasmine.createSpyObj('UserService', ['changePassword', 'updatePreferences', 'deleteAccount']);

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['showSuccess', 'showError']);

    await TestBed.configureTestingModule({
      imports: [
        SettingsComponent,
        TranslateModule.forRoot()
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthService, useValue: authServiceSpy},
        {provide: UserService, useValue: userServiceSpy},
        {provide: NotificationService, useValue: notificationServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    translateService = TestBed.inject(TranslateService);

    translateService.setDefaultLang('en');
    translateService.use('en');

    // 1. Initialiser la vue d'abord (Angular va lier le vrai élément DOM à deleteModal)
    fixture.detectChanges();

    // 2. Écraser l'élément réel par notre Mock AVEC des Spies
    // Cela nous permet de vérifier si showModal() et close() sont appelés,
    // même si l'environnement de test ne supporte pas nativement <dialog>
    component.deleteModal = {
      nativeElement: jasmine.createSpyObj('HTMLDialogElement', ['showModal', 'close'])
    };
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Password Form', () => {
    it('should initialize the password form', () => {
      expect(component.passwordForm).toBeDefined();
      expect(component.passwordForm.valid).toBeFalse();
    });

    it('should have a password mismatch error if passwords do not match', () => {
      const form = component.passwordForm;
      form.controls['currentPassword'].setValue('12345678');
      form.controls['newPassword'].setValue('newPassword123');
      form.controls['confirmationPassword'].setValue('wrongPassword123');
      expect(form.hasError('passwordsMismatch')).toBeTrue();
    });

    it('should be valid if all password fields are correct', () => {
      const form = component.passwordForm;
      form.controls['currentPassword'].setValue('12345678');
      form.controls['newPassword'].setValue('newPassword123');
      form.controls['confirmationPassword'].setValue('newPassword123');
      expect(form.valid).toBeTrue();
    });

    it('should call authService.changePassword on submit', () => {
      const form = component.passwordForm;

      // CORRECTION : Utiliser un mot de passe valide (> 8 caractères)
      // Sinon le formulaire est invalide et le service n'est jamais appelé
      form.controls['currentPassword'].setValue('oldPassword123');
      form.controls['newPassword'].setValue('newPassword123');
      form.controls['confirmationPassword'].setValue('newPassword123');

      userServiceSpy.changePassword.and.returnValue(of(void 0));

      component.onSubmitPassword();

      expect(userServiceSpy.changePassword).toHaveBeenCalled();
      expect(notificationServiceSpy.showSuccess).toHaveBeenCalled();
      expect(form.pristine).toBeTrue();
    });
  });

  describe('Delete Account', () => {
    it('should open the confirmation modal', () => {
      component.openDeleteConfirmation();
      // Maintenant nativeElement.showModal est bien un Spy
      expect(component.deleteModal.nativeElement.showModal).toHaveBeenCalled();
    });

    it('should call deleteAccount service on confirmation and close modal on success', () => {
      userServiceSpy.deleteAccount.and.returnValue(of(void 0));

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
      userServiceSpy.deleteAccount.and.returnValue(throwError(() => errorResponse));

      component.confirmDeleteAccount();

      expect(component.deleteModal.nativeElement.close).toHaveBeenCalled();
      expect(notificationServiceSpy.showError).toHaveBeenCalledWith('Deletion failed');
    });
  });
});
