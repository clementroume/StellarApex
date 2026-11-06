import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable, shareReplay, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {AccessService} from '../../../core/services/access.service';
import {BoardRecord, CanonicalLift, MovementRecords, RecordPoint} from '../models/record.model';

/** Per-movement records (Aldebaran `/records`, lot C): own records, canonical lifts, box boards. */
@Injectable({providedIn: 'root'})
export class RecordService {
  private readonly http = inject(HttpClient);
  private readonly accessService = inject(AccessService);

  /** Bumped by `invalidate()`: screens showing data derived from my records read it to reload them. */
  readonly version = signal(0);

  // Shared by the screens that only read them (% loads in WODs, records page), until a score changes
  // or another user signs in (support session included)
  private myRecords$?: Observable<MovementRecords[]>;
  private myRecordsUser?: number | null;
  private lifts$?: Observable<CanonicalLift[]>;

  /** The current user's records, grouped by movement and variant (base version first), cached. */
  getMyRecords(context?: HttpContext): Observable<MovementRecords[]> {
    const userId = this.accessService.userId();
    if (this.myRecordsUser !== userId) {
      this.myRecords$ = undefined;
      this.myRecordsUser = userId;
    }
    // A failed load is not kept: the next call asks again
    this.myRecords$ ??= this.http.get<MovementRecords[]>(this.buildUrl('/me'), {context})
      .pipe(tap({error: () => this.myRecords$ = undefined}), shareReplay({bufferSize: 1, refCount: false}));
    return this.myRecords$;
  }

  /** The current user's records of one movement (not cached). */
  getMyMovementRecords(movementId: number, context?: HttpContext): Observable<MovementRecords[]> {
    const params = new HttpParams().set('movementId', movementId);
    return this.http.get<MovementRecords[]>(this.buildUrl('/me'), {params, context});
  }

  /** Progression of my N RM on the base version of a movement: each day a new best was set. */
  getMyProgress(movementId: number, reps: number = 1, context?: HttpContext): Observable<RecordPoint[]> {
    const params = new HttpParams().set('movementId', movementId).set('reps', reps);
    return this.http.get<RecordPoint[]>(this.buildUrl('/me/progress'), {params, context});
  }

  /** The canonical "N RM" lifts of the catalogue, cached. */
  getCanonicalLifts(context?: HttpContext): Observable<CanonicalLift[]> {
    this.lifts$ ??= this.http.get<CanonicalLift[]>(this.buildUrl('/lifts'), {context})
      .pipe(tap({error: () => this.lifts$ = undefined}), shareReplay({bufferSize: 1, refCount: false}));
    return this.lifts$;
  }

  /** The records board of a box for a movement (members of the box only). */
  getGymBoard(gymId: number, movementId: number, context?: HttpContext): Observable<BoardRecord[]> {
    const params = new HttpParams().set('movementId', movementId);
    return this.http.get<BoardRecord[]>(this.buildUrl(`/gyms/${gymId}`), {params, context});
  }

  /** Forgets the cached records: a score was logged, corrected or deleted. */
  invalidate(): void {
    this.myRecords$ = undefined;
    this.version.update(v => v + 1);
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/records${path}`;
  }
}
