import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {UncountedLine} from '../../../api/aldebaran/models/balance.model';
import {BalanceService} from '../../../api/aldebaran/services/balance.service';
import {AdminBalanceComponent} from './admin-balance.component';

const line = (wodId: number, reason: UncountedLine['reason'], standalone = true): UncountedLine => ({
  wodId, wodTitle: `WOD ${wodId}`, isPublic: wodId === 1, standalone, orderIndex: 1, movementName: 'Burpee',
  repsScheme: reason === 'UNREADABLE' ? '10 each side' : null, reason
});

describe('AdminBalanceComponent', () => {
  async function create(response: unknown) {
    await TestBed.configureTestingModule({
      imports: [AdminBalanceComponent],
      providers: [provideRouter([]), provideTranslateService(), {provide: BalanceService, useValue: {getUncountedLines: () => response}}]
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminBalanceComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the uncounted lines, counts them per reason and filters', async () => {
    const fixture = await create(of([line(1, 'UNREADABLE'), line(2, 'NO_SET_VALUE'), line(3, 'UNREADABLE', false)]));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-slot="uncounted-line"]')).toHaveLength(3);
    expect(fixture.componentInstance.counts()).toEqual({UNREADABLE: 2, NO_SET_VALUE: 1});
    expect(el.querySelectorAll('a[href="/wods/1"]')).toHaveLength(1);
    expect(el.querySelector('a[href="/wods/3"]')).toBeNull();
    fixture.componentInstance.pickReason('NO_SET_VALUE');
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-slot="uncounted-line"]')).toHaveLength(1);
    fixture.componentInstance.pickReason('NO_SET_VALUE');
    expect(fixture.componentInstance.shown()).toHaveLength(3);
  });

  it('says when every line counts', async () => {
    const fixture = await create(of([]));
    expect(fixture.nativeElement.querySelector('[data-slot="balance-all-counted"]')).not.toBeNull();
  });

  it('says so when the control cannot be loaded', async () => {
    const fixture = await create(throwError(() => new Error('down')));
    expect(fixture.nativeElement.textContent).toContain('ADMIN.BALANCE.LOAD_ERROR');
  });
});
