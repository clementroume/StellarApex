import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {toggleVariant, VariantPickerComponent} from './variant-picker.component';

describe('toggleVariant', () => {
  const sets = [['POWER', 'SQUAT'], ['RUSSIAN', 'AMERICAN'], ['BACK', 'FRONT'], ['HIGH_BAR', 'FRONT']];

  it('should remove a value already selected', () => {
    expect(toggleVariant(['POWER', 'PAUSED'], 'POWER', sets)).toEqual(['PAUSED']);
  });

  it('should keep one value of an exclusive set, and leave the others alone', () => {
    expect(toggleVariant(['POWER', 'PAUSED'], 'SQUAT', sets)).toEqual(['PAUSED', 'SQUAT']);
    expect(toggleVariant(['RUSSIAN'], 'AMERICAN', sets)).toEqual(['AMERICAN']);
  });

  it('should apply every set a value belongs to', () => {
    expect(toggleVariant(['BACK', 'HIGH_BAR'], 'FRONT', sets)).toEqual(['FRONT']);
    expect(toggleVariant(['BACK'], 'HIGH_BAR', sets)).toEqual(['BACK', 'HIGH_BAR']);
  });

  it('should add freely outside the sets, or without reference data', () => {
    expect(toggleVariant(['HANG'], 'PAUSED', sets)).toEqual(['HANG', 'PAUSED']);
    expect(toggleVariant(['POWER'], 'SQUAT', null)).toEqual(['POWER', 'SQUAT']);
  });
});

describe('VariantPickerComponent', () => {
  let fixture: ComponentFixture<VariantPickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VariantPickerComponent],
      providers: [provideTranslateService()]
    }).compileComponents();
    fixture = TestBed.createComponent(VariantPickerComponent);
    fixture.componentRef.setInput('allowed', ['SQUAT', 'POWER', 'HANG']);
    fixture.componentRef.setInput('selected', ['POWER']);
    fixture.componentRef.setInput('prefix', 'MOVEMENT.TECHNIQUES.');
  });

  const chips = () => [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];

  it('should group the allowed values by type in the catalog order, without an empty type', () => {
    fixture.componentRef.setInput('groups', {RECEPTION: ['POWER', 'SQUAT', 'MUSCLE'], START: ['HANG'], TEMPO: ['PAUSED']});
    fixture.componentRef.setInput('typePrefix', 'MOVEMENT.TECHNIQUE_TYPES.');
    fixture.detectChanges();
    expect(chips().map(c => c.textContent?.trim())).toEqual(
      ['MOVEMENT.TECHNIQUES.POWER', 'MOVEMENT.TECHNIQUES.SQUAT', 'MOVEMENT.TECHNIQUES.HANG']);
    expect(fixture.nativeElement.querySelectorAll('[role="group"]').length).toBe(2);
  });

  it('should show one sorted list without groups, and say which chip is on', () => {
    fixture.detectChanges();
    expect(chips().map(c => c.getAttribute('aria-pressed'))).toEqual(['false', 'true', 'false']);
  });

  it('should emit the value tapped', () => {
    const toggled = vi.fn();
    fixture.componentInstance.toggled.subscribe(toggled);
    fixture.detectChanges();
    chips()[0].click();
    expect(toggled).toHaveBeenCalledWith('HANG');
  });
});
