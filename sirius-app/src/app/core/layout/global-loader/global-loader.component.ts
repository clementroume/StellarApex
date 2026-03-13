import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {LoadingService} from '../../services/loading.service';

@Component({
  selector: 'app-global-loader',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './global-loader.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalLoaderComponent {
  public readonly loadingService = inject(LoadingService);
}
