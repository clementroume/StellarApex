import {Injectable} from '@angular/core';

/**
 * Toast messages, rendered by `app-notification` (spartan sonner) at the app root. sonner is loaded
 * on first use to keep it out of the initial bundle.
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  showSuccess(message: string): void {
    void import('@spartan-ng/brain/sonner').then(({toast}) => toast.success(message));
  }

  showError(message: string): void {
    void import('@spartan-ng/brain/sonner').then(({toast}) => toast.error(message));
  }

  /** Lasting message with one action button (e.g. reload for a new version). */
  showAction(message: string, label: string, onClick: () => void): void {
    void import('@spartan-ng/brain/sonner').then(({toast}) =>
      toast(message, {duration: Infinity, action: {label, onClick}}));
  }
}
