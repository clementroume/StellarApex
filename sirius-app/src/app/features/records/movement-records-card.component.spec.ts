import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {MovementRecordsCardComponent} from './movement-records-card.component';
import {UnitService} from '../../core/services/unit.service';
import {MovementRecords} from '../../api/aldebaran/models/record.model';

describe('MovementRecordsCardComponent', () => {
  let fixture: ComponentFixture<MovementRecordsCardComponent>;
  const records: MovementRecords = {
    movementId: 1, movementName: 'Back Squat',
    variants: [
      {variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: 132,
        records: [{reps: 1, weightKg: 140, date: '2026-11-01', scoreId: 1, wodId: 9, wodTitle: 'Back Squat 1RM'},
          {reps: 5, weightKg: 110, date: '2026-10-01', scoreId: 2, wodId: 8, wodTitle: '5x5'}]},
      {variantKey: ';PAUSED', base: false, equipment: [], techniques: ['PAUSED'],
        records: [{reps: 3, weightKg: 100, date: '2026-10-01', scoreId: 3, wodId: 7, wodTitle: 'Pause'}]}
    ]
  };

  const init = (inputs: Record<string, unknown>) => {
    fixture = TestBed.createComponent(MovementRecordsCardComponent);
    fixture.componentRef.setInput('movementId', 1);
    fixture.componentRef.setInput('movementName', 'Back Squat');
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MovementRecordsCardComponent],
      providers: [provideRouter([]), provideTranslateService(), {
        provide: UnitService,
        useValue: {formatScoreLoad: (v: number) => `${v} kg`, formatPercentLoad: (v: number) => `${v} kg`}
      }]
    }).compileComponents();
  });

  it('should show the base records, the estimate, the folded variants and the tests', () => {
    init({records, lifts: [{wodId: 9, movementId: 1, movementName: 'Back Squat', repMax: 1}]});
    const el: HTMLElement = fixture.nativeElement;
    const base = el.querySelectorAll('[data-slot="base-records"] li');
    expect(base.length).toBe(2);
    expect(base[0].textContent).toContain('140 kg');
    expect(el.querySelector('[data-slot="estimate"]')?.textContent).toContain('132 kg');
    expect(el.querySelector('[data-slot="variants"] summary')?.textContent).toContain('RECORDS.VARIANTS');
    expect(el.querySelector('[data-slot="variants"]')?.textContent).toContain('MOVEMENT.TECHNIQUES.PAUSED');
    expect(el.querySelector('[data-slot="tests"] a')?.getAttribute('href')).toBe('/wods/9');
  });

  it('should say when the base version has no record, unless told not to', () => {
    init({});
    expect(fixture.nativeElement.textContent).toContain('RECORDS.NO_BASE');
    init({showEmpty: false});
    expect(fixture.nativeElement.textContent).not.toContain('RECORDS.NO_BASE');
  });
});
