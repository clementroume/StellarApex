import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {NotificationService} from '../../services/notification.service';

/**
 * A global component to display toast notifications.
 *
 * It subscribes to the `notification` signal from the `NotificationService`
 * and displays success or error alerts accordingly in a fixed position on the screen.
 */
@Component({
  selector: 'app-notification',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationComponent {
  /**
   * Injects the NotificationService to access the global notification state.
   * Made public to be accessible from the component's template.
   */
  public readonly notificationService = inject(NotificationService);
}
