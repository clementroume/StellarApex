import {vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {NavbarComponent} from './navbar.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {DisplayGymService} from '../../services/display-gym.service';
import {AccessService} from '../../services/access.service';
import {NavSpace, SpacesService} from '../spaces.service';
import {APP_ICONS} from '../../../app.config';

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  const authService = {logout: vi.fn().mockName('AuthService.logout').mockReturnValue(of(void 0))};
  const themeService = {toggleTheme: vi.fn().mockName('ThemeService.toggleTheme'), currentTheme: signal('dark')};
  let hasChoice: WritableSignal<boolean>;
  const spaces = signal<NavSpace[]>([
    {key: 'today', link: '/today', label: 'NAVBAR.SPACES.TODAY', icon: 'heroHome', paths: ['/today'], children: []},
    {key: 'admin', link: '/admin/support', label: 'NAVBAR.SPACES.ADMIN', icon: 'heroLifebuoy', paths: ['/admin'], children: []},
  ]);

  beforeEach(async () => {
    hasChoice = signal(false);
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: AuthService, useValue: authService},
        {provide: ThemeService, useValue: themeService},
        {provide: DisplayGymService, useValue: {hasChoice, gymId: signal(null), select: vi.fn()}},
        {provide: AccessService, useValue: {memberships: signal([])}},
        {provide: SpacesService, useValue: {spaces, current: signal(spaces()[0])}},
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  it('should link every visible space and mark the current one', () => {
    const links = Array.from(el().querySelectorAll('nav a'));
    expect(links.map(a => a.getAttribute('href'))).toEqual(['/today', '/admin/support']);
    expect(links[0].getAttribute('aria-current')).toBe('page');
  });

  it('should toggle the theme and log out', () => {
    (el().querySelector('#theme-toggle') as HTMLButtonElement).click();
    expect(themeService.toggleTheme).toHaveBeenCalled();

    (el().querySelector('button[aria-label="NAVBAR.LOG_OUT"]') as HTMLButtonElement).click();
    expect(authService.logout).toHaveBeenCalled();
  });

  it('should show the box selector only with several boxes/progs', () => {
    expect(el().querySelector('app-gym-selector')).toBeNull();
    hasChoice.set(true);
    fixture.detectChanges();
    expect(el().querySelector('app-gym-selector')).not.toBeNull();
  });
});
