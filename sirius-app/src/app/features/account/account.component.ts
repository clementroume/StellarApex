import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [RouterModule, TranslateModule, NgIcon],
  templateUrl: './account.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {
}
