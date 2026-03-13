import {Injectable, signal} from '@angular/core';

export type NotificationType = 'success' | 'error';

export interface Notification {
  message: string;
  type: NotificationType;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly _notification = signal<Notification | null>(null);
  public readonly notification = this._notification.asReadonly();

  showSuccess(message: string): void {
    this._notification.set({message, type: 'success'});
    this.autoClear();
  }

  showError(message: string): void {
    this._notification.set({message, type: 'error'});
    this.autoClear();
  }

  private autoClear(): void {
    setTimeout(() => this._notification.set(null), 5000);
  }
}
