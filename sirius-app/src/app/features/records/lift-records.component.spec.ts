import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {LiftRecordsComponent} from './lift-records.component';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {UnitService} from '../../core/services/unit.service';
import {MovementRecords} from '../../api/aldebaran/models/record.model';

describe('LiftRecordsComponent', () => {
  let fixture: ComponentFixture<LiftRecordsComponent>;
  const version = signal(0);
  const recordService = {
    getMyMovementRecords: vi.fn().mockName('RecordService.getMyMovementRecords'),
    getCanonicalLifts: vi.fn().mockName('RecordService.getCanonicalLifts'),
    getMyProgress: vi.fn().mockName('RecordService.getMyProgress'),
    version
  };
  const squat: MovementRecords = {
    movementId: 9, movementName: 'Back Squat', category: 'SQUAT',
    variants: [
      {variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: 134,
        records: [{reps: 1, weightKg: 140, date: '2026-10-12', scoreId: 1, wodId: 7, wodTitle: 'Back Squat 1RM'},
          {reps: 5, weightKg: 115, date: '2026-09-29', scoreId: 2, wodId: 8, wodTitle: '5x5'}],
        estimates: [{reps: 2, weightKg: 131.25}, {reps: 3, weightKg: 127.27}, {reps: 10, weightKg: 105}]},
      {variantKey: ';PAUSED', base: false, equipment: [], techniques: ['PAUSED'], estimates: [],
        records: [{reps: 3, weightKg: 105, date: '2026-10-01', scoreId: 3, wodId: 9, wodTitle: 'Pause'}]}
    ]
  };

  const init = (inputs: Record<string, unknown> = {}) => {
    fixture = TestBed.createComponent(LiftRecordsComponent);
    fixture.componentRef.setInput('movementId', 9);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    recordService.getMyMovementRecords.mockReturnValue(of([squat]));
    recordService.getCanonicalLifts.mockReturnValue(of([
      {wodId: 7, movementId: 9, movementName: 'Back Squat', repMax: 1}, {wodId: 20, movementId: 4, movementName: 'Clean', repMax: 1}
    ]));
    recordService.getMyProgress.mockReturnValue(of([
      {date: '2026-03-01', weightKg: 120}, {date: '2026-06-01', weightKg: 130}, {date: '2026-10-12', weightKg: 140}
    ]));
    await TestBed.configureTestingModule({
      imports: [LiftRecordsComponent],
      providers: [provideRouter([]), provideTranslateService(),
        {provide: RecordService, useValue: recordService},
        {provide: UnitService, useValue: {formatScoreLoad: (v: number) => `${v} kg`, formatPercentLoad: (v: number) => `${Math.round(v)} kg`}}]
    }).compileComponents();
  });

  it('should list records and estimates by N, the % table, the variants, the progress and the tests', () => {
    init();
    const el: HTMLElement = fixture.nativeElement;
    expect(recordService.getMyProgress).toHaveBeenCalledWith(9, 1, expect.anything());
    const rows = Array.from(el.querySelectorAll('[data-slot="rm-table"] tr')) as HTMLElement[];
    expect(rows.length).toBe(5);
    expect(rows[0].textContent).toContain('140 kg');
    expect(rows[1].querySelector('[data-slot="estimate"]')?.textContent).toContain('131 kg');
    expect(el.querySelector('[data-slot="percent-table"]')?.textContent).toContain('112 kg');
    expect(el.querySelector('[data-slot="variants"]')?.textContent).toContain('MOVEMENT.TECHNIQUES.PAUSED');
    expect(el.querySelectorAll('[data-slot="progress"] [title]').length).toBe(3);
    const tests = Array.from(el.querySelectorAll('[data-slot="tests"] a')) as HTMLAnchorElement[];
    expect(tests.map(a => a.getAttribute('href'))).toEqual(['/wods/7']);
  });

  it('should reload after a score change, and say when there is no record', () => {
    init();
    version.set(1);
    fixture.detectChanges();
    expect(recordService.getMyMovementRecords).toHaveBeenCalledTimes(2);

    recordService.getMyMovementRecords.mockReturnValue(of([]));
    init({page: true});
    expect(fixture.nativeElement.textContent).toContain('RECORDS.NO_BASE');
    expect(fixture.nativeElement.querySelector('app-page')).not.toBeNull();
  });
});
