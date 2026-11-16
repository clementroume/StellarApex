import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {ReadableTextsCheck, UncountedLine, WeeklyBalance} from '../models/balance.model';

/**
 * The athlete's weekly balance (Aldebaran `/balance`, lot D): only one's own; plus the checks of the
 * line texts the balance reads (D121).
 */
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

  /**
   * Whether the balance reads each text of a WOD line as that WOD counts it (D121): the WOD form
   * warns, never blocks.
   *
   * @param check The texts of the lines, in order, and the WOD's format, score type, ladder and N RM.
   * @return One flag per text; a blank text is readable (its measures count).
   */
  readableTexts(check: ReadableTextsCheck, context?: HttpContext): Observable<boolean[]> {
    return this.http.post<boolean[]>(`${environment.trainingUrl}/balance/readable-texts`, check, {context});
  }

  /** Every WOD line that counts nothing in the balance (platform admins). */
  getUncountedLines(): Observable<UncountedLine[]> {
    return this.http.get<UncountedLine[]>(`${environment.trainingUrl}/balance/uncounted-lines`);
  }
}
