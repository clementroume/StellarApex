import {ApplicationConfig, isDevMode} from '@angular/core';
import {provideServiceWorker} from '@angular/service-worker';
import {provideRouter, withComponentInputBinding, withViewTransitions} from '@angular/router';
import {provideHttpClient, withInterceptors, withXhr, withXsrfConfiguration} from '@angular/common/http';

import {provideTranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';

import {routes} from './app.routes';
import {authInterceptor} from './core/interceptors/auth.interceptor';
import {loadingInterceptor} from './core/interceptors/loading.interceptor';
import {provideIcons} from '@ng-icons/core';
import {LAYOUT_ICONS} from './core/layout/layout-icons';

/**
 * Icons of the layout only (navbar, bottom-nav, space-nav, support banner), registered at start-up.
 * Every lazy component provides the icons it names (`viewProviders: [provideIcons({...})]`), so they
 * stay out of the initial bundle (D160).
 */
export const APP_ICONS = LAYOUT_ICONS;

export const appConfig: ApplicationConfig = {
  providers: [
    // 1. ROUTING CONFIGURATION
    // Sets up the application routes with modern features.
    provideRouter(
      routes,
      withComponentInputBinding(), // Binds route parameters directly to component inputs.
      withViewTransitions() // Enables native View Transitions for smoother route changes.
    ),

    // 2. HTTP CLIENT CONFIGURATION
    // Configures the HttpClient with a chain of functional interceptors.
    provideHttpClient(withXhr(),
      // Activate XSRF protection
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
      // Register functional interceptors
      withInterceptors([
        authInterceptor,    // #1: Adds withCredentials and handles 401 code session refresh.
        loadingInterceptor, // #2: Manages global loading indicator during HTTP requests.
      ])
    ),

    // 3. INTERNATIONALIZATION (I18N) CONFIGURATION
    // Sets up ngx-translate for multi-language support.
    provideTranslateService({
      loader: provideTranslateHttpLoader({
        prefix: './assets/i18n/',
        suffix: '.json'
      }),
      fallbackLang: 'en'
    }),

    // 4. GLOBAL ICON IMPORT
    provideIcons(APP_ICONS),

    // 5. PWA (D40): installable, app shell cached; API calls always go to the network (ngsw-config.json)
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    })
  ],
};
