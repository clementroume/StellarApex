import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
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

  it('should open on the back squat 1RM of the first box, with names', () => {
    expect(recordService.getGymBoard).toHaveBeenCalledWith(5, 1, expect.anything());
    const rows = fixture.nativeElement.querySelectorAll('[data-slot="board"] li');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Anne L.');
    expect(rows[0].textContent).toContain('150 kg');
    expect(component.repsOptions()).toEqual([1, 3, 5]);
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
    expect(fixture.nativeElement.querySelector('#board-gym')).toBeNull();
  });
});
