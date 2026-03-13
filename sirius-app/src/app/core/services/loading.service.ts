import {computed, Injectable, signal} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private readonly _activeRequests = signal<number>(0);
  public readonly isLoading = computed(() => this._activeRequests() > 0);

  show(): void {
    this._activeRequests.update(count => count + 1);
  }

  hide(): void {
    this._activeRequests.update(count => Math.max(0, count - 1));
  }
}
