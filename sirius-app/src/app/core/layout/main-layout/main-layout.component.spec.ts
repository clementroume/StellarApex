import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MainLayoutComponent} from './main-layout.component';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';
import {vi} from 'vitest';

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    const authServiceSpy = {
      logout: vi.fn().mockName("AuthService.logout"),
      currentUser: signal(null)
    };
    const themeServiceSpy = {
      toggleTheme: vi.fn().mockName("ThemeService.toggleTheme"),
      currentTheme: signal('light')
    };

    await TestBed.configureTestingModule({
      imports: [
        MainLayoutComponent,
      ],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: authServiceSpy},
        {provide: ThemeService, useValue: themeServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the layout', () => {
    expect(component).toBeTruthy();
  });

  it('should include the <app-navbar> tag', () => {
    const navbar = fixture.nativeElement.querySelector('app-navbar');
    expect(navbar).toBeTruthy();
  });

  it('should include the <router-outlet> tag to display child pages', () => {
    const routerOutlet = fixture.nativeElement.querySelector('router-outlet');
    expect(routerOutlet).toBeTruthy();
  });
});
