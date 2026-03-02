import {ApplicationConfig} from '@angular/core';
import {provideRouter, withComponentInputBinding, withViewTransitions} from '@angular/router';
import {provideHttpClient, withInterceptors, withXsrfConfiguration} from '@angular/common/http';

import {provideTranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';

import {routes} from './app.routes';
import {authInterceptor} from './core/interceptors/auth.interceptor';
import {loadingInterceptor} from './core/interceptors/loading.interceptor';
import {provideIcons} from '@ng-icons/core';
import {
  heroChevronDown,
  heroCog8Tooth,
  heroEnvelope,
  heroExclamationTriangle,
  heroGlobeAlt,
  heroKey,
  heroMoon,
  heroPencilSquare,
  heroSun,
  heroUser
} from '@ng-icons/heroicons/outline';

/**
 * The main application configuration for this standalone Angular application.
 * It sets up all the core providers for routing, HTTP client, and internationalization.
 */
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
    provideHttpClient(
      // Activate XSRF protection
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
      // Register functional interceptors
      withInterceptors([
        authInterceptor,    // #1: Adds withCredentials and handles 401 session refresh.
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
    provideIcons({
      heroUser,
      heroEnvelope,
      heroKey,
      heroCog8Tooth,
      heroPencilSquare,
      heroGlobeAlt,
      heroExclamationTriangle,
      heroSun,
      heroMoon,
      heroChevronDown
    })
  ],
};
