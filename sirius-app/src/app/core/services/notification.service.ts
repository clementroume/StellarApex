import { Injectable, signal } from '@angular/core';

/** Defines the possible types for a notification. */
export type NotificationType = 'success' | 'error';

/** Defines the structure of a notification object. */
export interface Notification {
  message: string;
  type: NotificationType;
}

/**
 * A singleton service to display global notifications (toasts/alerts).
 * Components can use this to provide consistent feedback to the user.
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly _notification = signal<Notification | null>(null);

  /** A public readonly signal representing the current notification to display. */
  public readonly notification = this._notification.asReadonly();

  /**
   * Shows a success notification.
   * @param message The message to display.
   */
  showSuccess(message: string): void {
    this._notification.set({ message, type: 'success' });
    this.autoClear();
  }

  /**
   * Shows an error notification.
   * @param message The message to display.
   */
  showError(message: string): void {
    this._notification.set({ message, type: 'error' });
    this.autoClear();
  }

  /**
   * Clears the current notification after a 5-second delay.
   */
  private autoClear(): void {
    setTimeout(() => this._notification.set(null), 5000);
  }
}
