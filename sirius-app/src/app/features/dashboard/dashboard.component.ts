import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavbarComponent } from '../../core/components/navbar/navbar.component';
import { TranslateModule } from '@ngx-translate/core';

/**
 * The main dashboard component.
 * It serves as the homepage for authenticated users.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, NavbarComponent, TranslateModule],
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  // This component is currently presentational and holds no business logic.
  // It will be the foundation for future dashboard features.
}
