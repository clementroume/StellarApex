import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MainLayoutComponent} from './main-layout.component';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';

// Ajuste ces chemins selon ton arborescence
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    // Mocks basiques pour satisfaire les dépendances de la Navbar
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      currentUser: signal(null)
    });
    const themeServiceSpy = jasmine.createSpyObj('ThemeService', ['toggleTheme'], {
      currentTheme: signal('light')
    });

    await TestBed.configureTestingModule({
      imports: [
        MainLayoutComponent,
        TranslateModule.forRoot() // Requis par la Navbar
      ],
      providers: [
        provideRouter([]), // Requis par les routerLink de la Navbar et le router-outlet
        {provide: AuthService, useValue: authServiceSpy},
        {provide: ThemeService, useValue: themeServiceSpy}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('devrait créer le layout', () => {
    expect(component).toBeTruthy();
  });

  it('devrait intégrer la balise <app-navbar>', () => {
    const navbar = fixture.nativeElement.querySelector('app-navbar');
    expect(navbar).toBeTruthy();
  });

  it('devrait intégrer la balise <router-outlet> pour afficher les pages enfants', () => {
    const routerOutlet = fixture.nativeElement.querySelector('router-outlet');
    expect(routerOutlet).toBeTruthy();
  });
});
