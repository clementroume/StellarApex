import {TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {LiftBalance} from '../../api/aldebaran/models/balance.model';
import {ReviewForceComponent} from './review-force.component';

describe('ReviewForceComponent', () => {
  const lift = (label: string, sets: number, nearRecord = false): LiftBalance => ({label, sets, nearRecord});

  async function create(lifts: LiftBalance[]) {
    await TestBed.configureTestingModule({imports: [ReviewForceComponent], providers: [provideTranslateService()]}).compileComponents();
    const fixture = TestBed.createComponent(ReviewForceComponent);
    fixture.componentRef.setInput('lifts', lifts);
    fixture.detectChanges();
    return fixture;
  }

  it('keeps 5 slices at most, the smallest grouped under Others', async () => {
    const fixture = await create([lift('A', 6), lift('B', 5), lift('C', 4), lift('D', 3), lift('E', 2), lift('F', 1)]);
    const slices = fixture.componentInstance.slices();
    expect(slices.map(s => s.label)).toEqual(['A', 'B', 'C', 'D', 'REVIEW.FORCE.OTHERS']);
    expect(slices.reduce((sum, s) => sum + s.share, 0)).toBeCloseTo(100);
  });

  it('names the lifts close to the record, without numbers', async () => {
    const fixture = await create([lift('Squat', 4, true), lift('Snatch', 2)]);
    expect(fixture.nativeElement.querySelector('[data-slot="near-record"]')).not.toBeNull();
    expect(fixture.componentInstance.nearRecord()).toBe('REVIEW.FORCE.NEAR');
  });

  it('says when there is no strength set', async () => {
    const fixture = await create([]);
    expect(fixture.nativeElement.querySelector('[data-slot="force-empty"]')).not.toBeNull();
  });
});
