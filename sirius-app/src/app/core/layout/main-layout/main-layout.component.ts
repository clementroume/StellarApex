import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {NavbarComponent} from '../navbar/navbar.component';
import {SupportBannerComponent} from '../support-banner/support-banner.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, SupportBannerComponent],
  templateUrl: './main-layout.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MainLayoutComponent {
}
