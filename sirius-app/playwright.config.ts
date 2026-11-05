import {defineConfig, devices} from '@playwright/test';
import {loadLocalEnv} from './e2e/support/env';

// Credentials of the local test accounts come from e2e/local.env (git-ignored).
loadLocalEnv();

/**
 * End-to-end journeys against the local Docker stack (see "End-to-end tests" in README.md).
 * Chromium only, desktop and mobile (375 px). Not part of CI yet.
 */
export default defineConfig({
  testDir: './e2e/tests',
  globalSetup: './e2e/support/global-setup.ts',
  globalTeardown: './e2e/support/global-teardown.ts',
  outputDir: 'test-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: {timeout: 10_000},
  reporter: [['list'], ['html', {open: 'never', outputFolder: 'playwright-report'}]],
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'en-US',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {name: 'desktop', use: {...devices['Desktop Chrome']}},
    {name: 'mobile', use: {...devices['Desktop Chrome'], viewport: {width: 375, height: 812}, hasTouch: true, isMobile: true}}
  ],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000
  }
});
