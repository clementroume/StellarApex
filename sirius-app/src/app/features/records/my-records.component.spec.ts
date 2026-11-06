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
  const recent = new Date(Date.now() - 2 * 86_400_000).toISOString().slice(0, 10);
  const lift = (name: string, id: number, category: string, reps: number, kg: number, previous: number | null = null,
                estimate: number | null = null): MovementRecords => ({
    movementId: id, movementName: name, category,
    variants: [{variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: estimate, estimates: [],
      records: [{reps, weightKg: kg, previousWeightKg: previous, date: recent, scoreId: id, wodId: id, wodTitle: 'T'}]}]
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
        {provide: UnitService, useValue: {
          formatScoreLoad: (v: number) => `${v} kg`, formatPercentLoad: (v: number) => `${Math.round(v)} kg`, massUnit: () => 'KG'
        }}]
    }).compileComponents();
  });

  it('should show one tile per lift with its gain, the CrossFit Total and the lifts to test', () => {
    recordService.getMyRecords.mockReturnValue(of([
      lift('Back Squat', 1, 'SQUAT', 1, 145, 140), lift('Deadlift', 3, 'DEADLIFT', 1, 180),
      lift('Shoulder Press', 2, 'PRESS_AND_JERK', 1, 60), lift('Front Squat', 5, 'SQUAT', 3, 107, null, 118)
    ]));
    recordService.getCanonicalLifts.mockReturnValue(of([
      {wodId: 10, movementId: 1, movementName: 'Back Squat', repMax: 1},
      {wodId: 11, movementId: 4, movementName: 'Clean', repMax: 1}
    ]));
    init();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-slot="crossfit-total"]')?.textContent).toContain('385 kg');
    const tiles = Array.from(el.querySelectorAll('[data-slot="lift-tile"]')) as HTMLAnchorElement[];
    expect(tiles.length).toBe(4);
    expect(tiles[0].getAttribute('href')).toBe('/training/records/1');
    expect(tiles[0].querySelector('[data-slot="gain"]')?.textContent).toContain('+5');
    expect(tiles[3].textContent).toContain('≈ 118');
    expect(el.querySelector('[data-slot="untested"]')?.textContent).toContain('Clean');
  });

  it('should filter the tiles by family, offering only families with lifts', () => {
    recordService.getMyRecords.mockReturnValue(of([lift('Back Squat', 1, 'SQUAT', 1, 140), lift('Deadlift', 3, 'DEADLIFT', 1, 180)]));
    recordService.getCanonicalLifts.mockReturnValue(of([]));
    init();
    const chips = Array.from(fixture.nativeElement.querySelectorAll('[data-slot="families"] button')) as HTMLButtonElement[];
    expect(chips.map(c => c.textContent?.trim())).toEqual(['RECORDS.FAMILIES.ALL', 'RECORDS.FAMILIES.SQUAT', 'RECORDS.FAMILIES.HINGE']);
    chips[2].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-slot="lift-tile"]').length).toBe(1);
  });

  it('should show an empty state, and an error when the records cannot be loaded', () => {
    recordService.getMyRecords.mockReturnValue(of([]));
    recordService.getCanonicalLifts.mockReturnValue(of([]));
    init();
    expect(fixture.nativeElement.textContent).toContain('RECORDS.EMPTY');

    recordService.getMyRecords.mockReturnValue(throwError(() => new Error('down')));
    init();
    expect(fixture.nativeElement.textContent).toContain('RECORDS.LOAD_ERROR');
  });
});
