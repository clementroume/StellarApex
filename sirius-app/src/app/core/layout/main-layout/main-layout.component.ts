import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {NavbarComponent} from '../navbar/navbar.component';
import {SupportBannerComponent} from '../support-banner/support-banner.component';
import {BottomNavComponent} from '../bottom-nav/bottom-nav.component';
import {SpaceNavComponent} from '../space-nav/space-nav.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, SupportBannerComponent, SpaceNavComponent, BottomNavComponent],
  templateUrl: './main-layout.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MainLayoutComponent {
}
