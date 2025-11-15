import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * The main entry point for the Angular application.
 *
 * This function bootstraps the root component (`AppComponent`) with the provided
 * application configuration (`appConfig`), initiating the entire application.
 *
 * Per Angular guidelines, this file is named `main.ts` and resides directly
 * in the `src` directory, serving as the primary entry point.
 */
bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
