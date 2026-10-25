import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withInterceptors, withXhr} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';
import {authInterceptor} from './auth.interceptor';
import {loadingInterceptor} from './loading.interceptor';

/**
 * Wiring test with the real services of app.config.ts: the translation files are loaded through
 * the HTTP interceptors, so no interceptor may eagerly inject a service that depends on
 * TranslateService (circular dependency NG0200 at startup).
 */
describe('authInterceptor dependency wiring', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withXhr(), withInterceptors([authInterceptor, loadingInterceptor])),
        provideHttpClientTesting(),
        provideTranslateService({
          loader: provideTranslateHttpLoader({prefix: './assets/i18n/', suffix: '.json'}),
          fallbackLang: 'en'
        })
      ]
    });
  });

  it('should load translations through the interceptors without a circular dependency', () => {
    const translate = TestBed.inject(TranslateService);
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne('./assets/i18n/en.json').flush({GLOBAL: {SAVE: 'Save'}});

    expect(translate.instant('GLOBAL.SAVE')).toBe('Save');
    httpMock.verify();
  });
});
