import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {ProblemDetail} from '../../../core/models/problem-detail.model';

/**
 * Component for displaying and editing the user's profile information.
 * It uses a reactive form to manage user data and editing state.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule],
  templateUrl: './profile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent implements OnInit {
  public isEditing = false;
  public readonly profileForm: FormGroup;

  public readonly authService = inject(AuthService);
  public readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.profileForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(50)]],
      lastName: ['', [Validators.required, Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.email]]
    });
  }

  /**
   * On component initialization, populate the form with the current user's data.
   */
  ngOnInit(): void {
    const currentUser = this.authService.currentUser();
    if (currentUser) {
      this.profileForm.patchValue(currentUser);
    }
  }

  /**
   * Enters editing mode.
   */
  enterEditMode(): void {
    this.isEditing = true;
  }

  /**
   * Cancels the editing mode and reverts any changes in the form.
   */
  onCancel(): void {
    const currentUser = this.authService.currentUser();
    if (currentUser) {
      this.profileForm.patchValue(currentUser); // Revert to original values
    }
    this.isEditing = false;
  }

  /**
   * Handles the submission of the profile update form.
   */
  onSave(): void {
    if (this.profileForm.invalid) {
      return;
    }

    this.userService.updateProfile(this.profileForm.value).subscribe({
      next: () => {
        this.translate.get('PROFILE.SUCCESS_UPDATE').subscribe((message: string) => {
          this.notificationService.showSuccess(message);
        });
        this.isEditing = false;
      },
      error: (err: HttpErrorResponse) => {
        const problem: ProblemDetail = err.error;
        const message = problem?.detail || problem?.title || 'Une erreur est survenue';
        this.notificationService.showError(message);
      }
    });
  }
}
