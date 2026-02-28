import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {LoadingService} from '../../services/loading.service';

/**
 * A global loader component that displays a full-screen spinner.
 *
 * It listens to the `isLoading` signal from the `LoadingService` and becomes visible
 * whenever the application is busy, for example, during HTTP requests.
 */
@Component({
  selector: 'app-global-loader',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './global-loader.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalLoaderComponent {
  /**
   * Injects the LoadingService to access the global loading state.
   * Made public to be accessible from the component's template.
   */
  public readonly loadingService = inject(LoadingService);
}
