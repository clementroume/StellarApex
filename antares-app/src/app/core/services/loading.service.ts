import { Injectable, computed, signal } from '@angular/core';

/**
 * A singleton service to manage the global loading state of the application.
 * It uses a counter to handle multiple concurrent HTTP requests, ensuring the loader
 * is only hidden when all requests are complete.
 */
@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private readonly _activeRequests = signal<number>(0);

  /** A computed signal that is `true` if there are any active requests. */
  public readonly isLoading = computed(() => this._activeRequests() > 0);

  /**
   * Shows the global loading indicator by incrementing the active request counter.
   * To be called by an interceptor before a request is sent.
   */
  show(): void {
    this._activeRequests.update(count => count + 1);
  }

  /**
   * Hides the global loading indicator by decrementing the active request counter.
   * To be called by an interceptor when a request completes or errors.
   */
  hide(): void {
    this._activeRequests.update(count => Math.max(0, count - 1));
  }
}
