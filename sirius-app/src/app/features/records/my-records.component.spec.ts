import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {MyRecordsComponent} from './my-records.component';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {UnitService} from '../../core/services/unit.service';
import {MovementRecords} from '../../api/aldebaran/models/record.model';

describe('MyRecordsComponent', () => {
  let fixture: ComponentFixture<MyRecordsComponent>;
  let recordService: {getMyRecords: ReturnType<typeof vi.fn>; getCanonicalLifts: ReturnType<typeof vi.fn>};
  const oneRm = (name: string, id: number, kg: number): MovementRecords => ({
    movementId: id, movementName: name,
    variants: [{variantKey: '', base: true, equipment: [], techniques: [],
      records: [{reps: 1, weightKg: kg, date: '2026-11-01', scoreId: id, wodId: id, wodTitle: 'T'}]}]
  });

  const init = () => {
    fixture = TestBed.createComponent(MyRecordsComponent);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    recordService = {
      getMyRecords: vi.fn().mockName('RecordService.getMyRecords'),
      getCanonicalLifts: vi.fn().mockName('RecordService.getCanonicalLifts')
    };
    await TestBed.configureTestingModule({
      imports: [MyRecordsComponent],
      providers: [provideRouter([]), provideTranslateService(),
        {provide: RecordService, useValue: recordService},
        {provide: UnitService, useValue: {formatScoreLoad: (v: number) => `${v} kg`, formatPercentLoad: (v: number) => `${v} kg`}}]
    }).compileComponents();
  });

  it('should list my records, the CrossFit Total and the tests not done yet', () => {
    recordService.getMyRecords.mockReturnValue(of([oneRm('Back Squat', 1, 140), oneRm('Deadlift', 3, 180), oneRm('Shoulder Press', 2, 60)]));
    recordService.getCanonicalLifts.mockReturnValue(of([
      {wodId: 10, movementId: 1, movementName: 'Back Squat', repMax: 1},
      {wodId: 11, movementId: 4, movementName: 'Clean', repMax: 1}
    ]));
    init();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-slot="crossfit-total"]')?.textContent).toContain('380 kg');
    expect(el.querySelectorAll('app-movement-records-card').length).toBe(4);
    expect(fixture.componentInstance.toTest().map(r => r.movementName)).toEqual(['Clean']);
  });

  it('should show an empty state, and an error when the records cannot be loaded', () => {
    recordService.getMyRecords.mockReturnValue(of([]));
    recordService.getCanonicalLifts.mockReturnValue(of([]));
    init();
    expect(fixture.nativeElement.textContent).toContain('RECORDS.EMPTY');
    expect(fixture.nativeElement.querySelector('[data-slot="crossfit-total"]')).toBeNull();

    recordService.getMyRecords.mockReturnValue(throwError(() => new Error('down')));
    init();
    expect(fixture.nativeElement.textContent).toContain('RECORDS.LOAD_ERROR');
  });
});
