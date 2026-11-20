import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  OrderApplyRequest, OrderProposal, OrderProposalRequest, ProgrammingBalance, ProgrammingQuery
} from '../models/programming.model';

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

  /**
   * A session-order proposal of the cycle (WOD_WRITE): nothing is written.
   *
   * @param gymId The box or program.
   * @param request The cycle, the switches, the mode, the strength-day option and the pins.
   */
  proposeOrder(gymId: number, request: OrderProposalRequest, context?: HttpContext): Observable<OrderProposal> {
    return this.http.post<OrderProposal>(`${environment.trainingUrl}/programming/gyms/${gymId}/order-proposals`, request, {context});
  }

  /**
   * Applies the weeks of a proposal the coach keeps (WOD_WRITE), in one transaction: 409 when a
   * session became locked since the proposal.
   *
   * @param gymId The box or program.
   * @param request The weeks kept.
   */
  applyOrder(gymId: number, request: OrderApplyRequest): Observable<void> {
    return this.http.post<void>(`${environment.trainingUrl}/programming/gyms/${gymId}/order`, request);
  }
}
