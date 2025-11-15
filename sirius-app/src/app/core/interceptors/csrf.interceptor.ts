import {HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {environment} from '../../../environments/environment';

const baseApiUrl = `${environment.apiUrl}/api/v1`;
/**
 * Checks if a URL targets the backend API.
 * @param url The request URL.
 */
const isApiUrl = (url: string): boolean => url.startsWith(baseApiUrl);

/**
 * Checks if a URL targets an authentication endpoint.
 * @param url The request URL.
 */
const isAuthUrl = (url: string): boolean => url.startsWith(`${baseApiUrl}/auth/`);

/**
 * Checks if an HTTP method is one that typically modifies state on the server.
 * @param method The HTTP method string.
 */
const isMutatingMethod = (method: string): boolean => ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

/**
 * Reads a browser cookie by its name.
 * This implementation is carefully crafted to correctly escape the cookie name for use in a RegExp
 * and uses the performant `exec` method.
 * @param name The name of the cookie to read.
 * @returns The cookie's value or `null` if not found.
 */
function readCookie(name: string): string | null {
  // Escape special RegExp characters in the cookie name.
  const escapedName = name.replace(/([.$?*|{}()[\]\\+^])/g, '\\$1');

  // Build the RegExp to find the cookie.
  const cookieRegex = new RegExp(`(?:^|; )${escapedName}=([^;]*)`);

  // Use the exec() method for performance and clarity.
  const match = cookieRegex.exec(document.cookie);

  // If a match is found, decode and return the cookie value (capture group 1).
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * A functional interceptor that implements the "double-submit cookie" pattern for CSRF protection.
 *
 * For any mutating request (POST, PUT, etc.) to the API (excluding auth endpoints),
 * this interceptor reads the `XSRF-TOKEN` cookie and adds its value to the `X-XSRF-TOKEN` header.
 * The backend then verifies that the cookie and header values match.
 */
export const csrfInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  // Apply this logic only to mutating API requests that are not for authentication.
  if (isApiUrl(req.url) && isMutatingMethod(req.method) && !isAuthUrl(req.url)) {
    const token = readCookie('XSRF-TOKEN');
    if (token && !req.headers.has('X-XSRF-TOKEN')) {
      // Clone the request and add the CSRF header.
      const modifiedReq = req.clone({setHeaders: {'X-XSRF-TOKEN': token}});
      return next(modifiedReq);
    }
  }

  // For all other requests (GET, auth, etc.), pass them through without modification.
  return next(req);
};
