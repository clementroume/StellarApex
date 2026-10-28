import {computed, effect, inject, Injectable, signal} from '@angular/core';
import {AccessService} from './access.service';

const STORAGE_KEY = 'displayGymId';

/**
 * Box/prog the user chose to display (D7): one of their usable memberships, or null for all of them.
 * Purely a UI filter remembered on this device: never send it as an "active gym", rights stay
 * evaluated per resource by the backends.
 */
@Injectable({providedIn: 'root'})
export class DisplayGymService {
  // --- INJECTIONS ---
  private readonly accessService = inject(AccessService);

  // --- STATE (SIGNALS & VARIABLES) ---
  private readonly stored = signal<number | null>(this.read());

  // --- COMPUTED STATE ---
  /** Chosen gym id if it is still one of the user's usable memberships, else null (all). */
  readonly gymId = computed(() => {
    const id = this.stored();
    return id !== null && this.accessService.memberships().some(m => m.gymId === id) ? id : null;
  });
  readonly membership = computed(() => this.accessService.memberships().find(m => m.gymId === this.gymId()) ?? null);
  /** The selector is only useful with at least two boxes/progs. */
  readonly hasChoice = computed(() => this.accessService.memberships().length > 1);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => this.write(this.stored()));
  }

  // --- ACTIONS & EVENTS ---
  select(gymId: number | null): void {
    this.stored.set(gymId);
  }

  /** True when an item of this gym (or without gym) should be shown under the current choice. */
  matches(gymId: number | null | undefined): boolean {
    const chosen = this.gymId();
    return chosen === null || gymId === chosen;
  }

  // --- HELPERS ---
  private read(): number | null {
    try {
      const value = Number(localStorage.getItem(STORAGE_KEY));
      return Number.isInteger(value) && value > 0 ? value : null;
    } catch {
      return null;
    }
  }

  private write(gymId: number | null): void {
    try {
      if (gymId === null) localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, String(gymId));
    } catch {
      // Storage unavailable (private mode): the choice lasts for this visit
    }
  }
}
