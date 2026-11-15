import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {WeeklyBalance} from '../models/balance.model';

/** The athlete's weekly balance (Aldebaran `/balance`, lot D): only one's own. */
@Injectable({providedIn: 'root'})
export class BalanceService {
  private readonly http = inject(HttpClient);

  /**
   * The current user's balance of a week.
   *
   * @param weekStart Monday of the week, in the athlete's local calendar (yyyy-MM-dd).
   */
  getMyBalance(weekStart: string, context?: HttpContext): Observable<WeeklyBalance> {
    return this.http.get<WeeklyBalance>(`${environment.trainingUrl}/balance/me`, {params: {week: weekStart}, context});
  }
}
