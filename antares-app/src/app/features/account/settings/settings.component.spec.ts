import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SettingsComponent } from './settings.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

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
});
