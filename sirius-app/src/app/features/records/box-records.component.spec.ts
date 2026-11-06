import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of, Subject} from 'rxjs';
import {BoxRecordsComponent} from './box-records.component';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {UserService} from '../../api/antares/services/user.service';
import {AccessService} from '../../core/services/access.service';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {UnitService} from '../../core/services/unit.service';

describe('BoxRecordsComponent', () => {
  let fixture: ComponentFixture<BoxRecordsComponent>;
  let component: BoxRecordsComponent;
  const recordService = {
    getCanonicalLifts: vi.fn().mockName('RecordService.getCanonicalLifts'),
    getGymBoard: vi.fn().mockName('RecordService.getGymBoard')
  };
  const userService = {getDisplayNames: vi.fn().mockName('UserService.getDisplayNames')};
  const displayGymId = signal<number | null>(null);

  beforeEach(async () => {
    recordService.getCanonicalLifts.mockReturnValue(of([
      {wodId: 10, movementId: 2, movementName: 'Deadlift', repMax: 1},
      {wodId: 11, movementId: 1, movementName: 'Back Squat', repMax: 1},
      {wodId: 12, movementId: 1, movementName: 'Back Squat', repMax: 5}
    ]));
    recordService.getGymBoard.mockReturnValue(of([
      {userId: 7, reps: 1, weightKg: 150, date: '2026-11-01', division: 'MEN', ageGroup: 'AGE_35_39'},
      {userId: 8, reps: 1, weightKg: 100, date: '2026-11-01', division: 'WOMEN', ageGroup: 'AGE_18_34'},
      {userId: 8, reps: 3, weightKg: 90, date: '2026-11-01', division: 'WOMEN', ageGroup: 'AGE_18_34'}
    ]));
    userService.getDisplayNames.mockReturnValue(of([{id: 7, displayName: 'Anne L.'}, {id: 8, displayName: 'Bob M.'}]));
    displayGymId.set(null);
    await TestBed.configureTestingModule({
      imports: [BoxRecordsComponent],
      providers: [provideRouter([]), provideTranslateService(),
        {provide: RecordService, useValue: recordService},
        {provide: UserService, useValue: userService},
        {provide: AccessService, useValue: {
          memberships: signal([{gymId: 5, gymName: 'Sparta'}, {gymId: 6, gymName: 'Zeus'}]), userId: signal(8)
        }},
        {provide: DisplayGymService, useValue: {gymId: displayGymId}},
        {provide: UnitService, useValue: {formatScoreLoad: (v: number) => `${v} kg`}}]
    }).compileComponents();
    fixture = TestBed.createComponent(BoxRecordsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should open on the back squat 1RM of the first box, with a podium, names and my line', () => {
    expect(recordService.getGymBoard).toHaveBeenCalledWith(5, 1, expect.anything());
    const podium = Array.from(fixture.nativeElement.querySelectorAll('[data-slot="podium"] li')) as HTMLElement[];
    expect(podium.length).toBe(2);
    // Visual order: 2nd, then 1st
    expect(podium[0].textContent).toContain('RECORDS.BOARD.ME');
    expect(podium[1].textContent).toContain('Anne L.');
    expect(podium[1].textContent).toContain('150 kg');
    // On the podium: no pinned line
    expect(component.mine()).toMatchObject({rank: 2, gapKg: 50});
    expect(fixture.nativeElement.querySelector('[data-slot="mine"]')).toBeNull();
    expect(component.repsOptions()).toEqual([1, 3, 5]);
  });

  it('should pin my line under the board, with the gap to the athlete above, when off the podium', () => {
    recordService.getGymBoard.mockReturnValue(of([150, 140, 130, 120, 100].map((kg, i) => (
      {userId: i === 4 ? 8 : 20 + i, reps: 1, weightKg: kg, date: '2026-11-01', division: 'WOMEN', ageGroup: null}))));
    component.onGymChange('6');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-slot="board"] li').length).toBe(2);
    const mine = fixture.nativeElement.querySelector('[data-slot="mine"]') as HTMLElement;
    expect(mine.textContent).toContain('5');
    expect(mine.textContent).toContain('RECORDS.BOARD.GAP');
  });

  it('should drop the board of a lift picked before the current one', () => {
    const squat = new Subject<unknown[]>();
    const deadlift = new Subject<unknown[]>();
    recordService.getGymBoard.mockReturnValueOnce(squat).mockReturnValueOnce(deadlift);
    component.onMovementChange('3');
    fixture.detectChanges();
    component.onMovementChange('2');
    fixture.detectChanges();
    deadlift.next([{userId: 7, reps: 1, weightKg: 200, date: '2026-11-01', division: 'MEN', ageGroup: null}]);
    squat.next([{userId: 7, reps: 1, weightKg: 120, date: '2026-11-01', division: 'MEN', ageGroup: null}]);
    expect(component.rows().map(r => r.weightKg)).toEqual([200]);
  });

  it('should open the filters, with the gym picker when the navbar shows every box', () => {
    expect(fixture.nativeElement.querySelector('[data-slot="filters"]')).toBeNull();
    (fixture.nativeElement.querySelector('#board-filters') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#board-gym')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#board-age')).not.toBeNull();
  });

  it('should filter by reps, division and age group, and follow the gym picked', () => {
    component.onRepsChange(3);
    expect(component.rows().map(r => r.weightKg)).toEqual([90]);
    component.onRepsChange(1);
    component.onDivisionChange('MEN');
    expect(component.rows().map(r => r.userId)).toEqual([7]);
    component.onDivisionChange(null);
    component.onAgeGroupChange('AGE_18_34');
    expect(component.rows().map(r => r.userId)).toEqual([8]);

    component.onGymChange('6');
    fixture.detectChanges();
    expect(recordService.getGymBoard).toHaveBeenLastCalledWith(6, 1, expect.anything());
    component.onMovementChange('2');
    fixture.detectChanges();
    expect(recordService.getGymBoard).toHaveBeenLastCalledWith(6, 2, expect.anything());
  });

  it('should follow the box chosen in the navbar, without its own picker', () => {
    displayGymId.set(6);
    fixture.detectChanges();
    expect(recordService.getGymBoard).toHaveBeenLastCalledWith(6, 1, expect.anything());
    component.toggleFilters();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#board-gym')).toBeNull();
  });
});
