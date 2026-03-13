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
  heroArrowLeft,
  heroArrowRightOnRectangle,
  heroBars3,
  heroChevronDown,
  heroCog8Tooth,
  heroDocumentArrowDown,
  heroEnvelope,
  heroExclamationTriangle,
  heroEye,
  heroGlobeAlt,
  heroKey,
  heroMagnifyingGlass,
  heroMoon,
  heroPencilSquare,
  heroPlay,
  heroPlus,
  heroSun,
  heroTrash,
  heroUser,
  heroVideoCamera,
} from '@ng-icons/heroicons/outline';
import {
  hugeBodyPartMuscle,
  hugeCircleArrowLeft02,
  hugeDashboardSpeed01,
  hugeKettlebell,
  hugeWorkoutStretching
} from '@ng-icons/huge-icons';

export const APP_ICONS = {
  heroUser,
  heroEnvelope,
  heroKey,
  heroCog8Tooth,
  heroPencilSquare,
  heroGlobeAlt,
  heroExclamationTriangle,
  heroSun,
  heroMoon,
  heroChevronDown,
  heroMagnifyingGlass,
  heroArrowLeft,
  heroPlus,
  heroTrash,
  hugeBodyPartMuscle,
  hugeKettlebell,
  heroEye,
  hugeWorkoutStretching,
  heroVideoCamera,
  heroPlay,
  heroBars3,
  heroArrowRightOnRectangle,
  hugeDashboardSpeed01,
  hugeCircleArrowLeft02,
  heroDocumentArrowDown
};

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
    provideIcons(APP_ICONS)
  ],
};
