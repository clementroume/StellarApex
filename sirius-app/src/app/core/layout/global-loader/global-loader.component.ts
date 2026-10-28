import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {LoadingService} from '../../services/loading.service';

@Component({
  selector: 'app-global-loader',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './global-loader.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalLoaderComponent {
  public readonly loadingService = inject(LoadingService);
}
