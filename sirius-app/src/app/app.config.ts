import {ApplicationConfig} from '@angular/core';
import {provideRouter, withComponentInputBinding, withViewTransitions} from '@angular/router';
import {provideHttpClient, withInterceptors, withNoXsrfProtection} from '@angular/common/http';

import {provideTranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';

import {routes} from './app.routes';
import {authInterceptor} from './core/interceptors/auth.interceptor';
import {csrfInterceptor} from './core/interceptors/csrf.interceptor';
import {loadingInterceptor} from './core/interceptors/loading.interceptor';

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
      withViewTransitions() // Enables native View Transitions for smoother route changes. [cite: 4222]
    ),

    // 2. HTTP CLIENT CONFIGURATION
    // Configures the HttpClient with a chain of functional interceptors. [cite: 1993]
    provideHttpClient(
      withInterceptors([
        csrfInterceptor,    // #1: Adds X-XSRF-TOKEN header to mutating requests.
        authInterceptor,    // #2: Adds withCredentials and handles 401 session refresh.
        loadingInterceptor, // #3: Manages global loading indicator during HTTP requests.
      ]),
      withNoXsrfProtection()
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
  ],
};
