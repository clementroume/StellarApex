import {TestBed} from '@angular/core/testing';
import {AppComponent} from './app.component';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {AuthService} from './api/antares/services/auth.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppComponent
      ],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideTranslateService()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it("should apply the user's language, except the impersonated user's during a support session", () => {
    TestBed.createComponent(AppComponent);
    const translate = TestBed.inject(TranslateService);
    const authService = TestBed.inject(AuthService);
    const user: any = {id: 1, firstName: 'A', lastName: 'B', email: 'a@b.c', platformRole: 'USER', memberships: [], theme: 'dark', createdAt: ''};

    authService.updateCurrentUser({...user, locale: 'fr'});
    TestBed.tick();
    expect(translate.currentLang()).toBe('fr');

    authService.updateCurrentUser({
      ...user, id: 2, locale: 'en',
      supportSession: {id: 1, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: ''}
    });
    TestBed.tick();
    expect(translate.currentLang()).toBe('fr');
  });
});
