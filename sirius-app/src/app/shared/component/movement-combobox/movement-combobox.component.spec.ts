import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {MovementComboboxComponent} from './movement-combobox.component';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';

describe('MovementComboboxComponent', () => {
  let component: MovementComboboxComponent;
  let fixture: ComponentFixture<MovementComboboxComponent>;

  const movements: MovementSummaryResponse[] = [
    {id: 1, name: 'Clean and Jerk', nameAbbreviation: 'C&J', category: 'COMPLEXES'},
    {id: 2, name: 'Épaulé jeté', category: 'COMPLEXES'},
    {id: 3, name: 'Pull-up', category: 'PULLING'},
  ];

  /** Exposes the protected filter for direct checks. */
  const matches = (m: MovementSummaryResponse, search: string) =>
    (component as any).filter(m, search, new Intl.Collator(undefined, {usage: 'search', sensitivity: 'base'}));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MovementComboboxComponent],
      providers: [provideTranslateService()]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementComboboxComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('movements', movements);
    fixture.detectChanges();
  });

  it('should link a visually hidden label to the combobox input', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[role="combobox"]');
    const label: HTMLLabelElement = fixture.nativeElement.querySelector('label.sr-only');
    expect(label.htmlFor).toBe(input.id);
    expect(label.textContent?.trim()).toBe('SHARED.MOVEMENT_COMBOBOX.LABEL');
  });

  it('should use the given label as accessible name', () => {
    fixture.componentRef.setInput('label', 'Movement 2');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('label.sr-only').textContent.trim()).toBe('Movement 2');
  });

  it('should match names without accents or case and abbreviations', () => {
    expect(matches(movements[1], 'epaule')).toBe(true);
    expect(matches(movements[0], 'c&j')).toBe(true);
    expect(matches(movements[2], 'clean')).toBe(false);
  });

  it('should show the movement of the bound id and emit the id of a chosen movement', async () => {
    fixture.componentRef.setInput('value', 3);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((component as any).selected()).toEqual(movements[2]);

    (component as any).value.set(null);
    expect((component as any).selected()).toBeNull();
  });
});
