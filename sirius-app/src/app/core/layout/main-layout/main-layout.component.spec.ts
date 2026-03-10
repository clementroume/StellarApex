import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MainLayoutComponent} from './main-layout.component';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';

import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    // Basic mocks to satisfy Navbar dependencies
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      currentUser: signal(null)
    });
    const themeServiceSpy = jasmine.createSpyObj('ThemeService', ['toggleTheme'], {
      currentTheme: signal('light')
    });

    await TestBed.configureTestingModule({
      imports: [
        MainLayoutComponent,
        TranslateModule.forRoot() // Required by Navbar
      ],
      providers: [
        provideRouter([]), // Required by Navbar routerLink and router-outlet
        provideIcons(APP_ICONS),
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
