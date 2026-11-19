import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {ProgrammingBalance, ProgrammingQuery} from '../models/programming.model';

/** The balance of a gym's programming (Aldebaran `/programming`, lot D'): staff of the gym only. */
@Injectable({providedIn: 'root'})
export class ProgrammingService {
  private readonly http = inject(HttpClient);

  /**
   * The balance of a cycle of a gym's sessions.
   *
   * @param gymId The box or program.
   * @param query The cycle (Monday and weeks), the switches and the profile to detail (no days: none).
   */
  getBalance(gymId: number, query: ProgrammingQuery, context?: HttpContext): Observable<ProgrammingBalance> {
    let params = new HttpParams()
      .set('start', query.start)
      .set('weeks', query.weeks)
      .set('scaled', query.scaled)
      .set('optional', query.optional);
    for (const day of query.days) {
      params = params.append('days', day);
    }
    return this.http.get<ProgrammingBalance>(`${environment.trainingUrl}/programming/gyms/${gymId}/balance`, {params, context});
  }
}
