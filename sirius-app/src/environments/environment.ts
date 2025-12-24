/**
 * This file contains configuration variables for the development environment.
 *
 * During a production build (`ng build --configuration production`), this file is replaced
 * by the contents of `environment.prod.ts`.
 */
export const environment = {
  /**
   * The base URL for the backend API.
   * This is used by services like AuthService to construct full endpoint URLs.
   */
  authUrl: '/antares',
};
