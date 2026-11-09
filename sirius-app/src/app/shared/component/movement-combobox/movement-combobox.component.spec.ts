import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {MovementComboboxComponent, usualNameOf} from './movement-combobox.component';
import {UsualNameOption} from '../../../api/aldebaran/models/movement.model';

describe('MovementComboboxComponent', () => {
  let component: MovementComboboxComponent;
  let fixture: ComponentFixture<MovementComboboxComponent>;

  const option = (movementId: number, nameEn: string, techniques: string[] = [], equipment: string[] = [],
                  extra: Partial<UsualNameOption> = {}): UsualNameOption =>
    ({movementId, movementName: nameEn, category: 'CLEAN', nameEn, techniques, equipment, ...extra});
  const clean = option(1, 'Clean', [], [], {nameFr: 'Épaulé'});
  const powerClean = option(1, 'Power Clean', ['POWER']);
  const chain = option(2, 'Clean and Jerk', [], [], {searchTerms: 'C&J'});
  const powerChain = option(2, 'Power Clean and Jerk', ['1:POWER']);
  const swing = option(3, 'Kettlebell Swing', [], ['KETTLEBELL'], {impliedEquipment: 'KETTLEBELL'});
  const options = [clean, powerClean, chain, powerChain, swing];

  /** Exposes the protected filter for direct checks. */
  const matches = (o: UsualNameOption, search: string) =>
    (component as any).filter(o, search, new Intl.Collator(undefined, {usage: 'search', sensitivity: 'base'}));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MovementComboboxComponent],
      providers: [provideTranslateService()]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementComboboxComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('options', options);
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

  it('should match names without accents or case, and the search terms', () => {
    expect(matches(clean, 'epaule')).toBe(true);
    expect(matches(chain, 'c&j')).toBe(true);
    expect(matches(swing, 'clean')).toBe(false);
  });

  it('should show the most precise name the line contains', () => {
    fixture.componentRef.setInput('value', 1);
    fixture.componentRef.setInput('techniques', ['POWER', 'HANG']);
    fixture.detectChanges();
    expect((component as any).selected()).toEqual(powerClean);

    fixture.componentRef.setInput('value', null);
    fixture.detectChanges();
    expect((component as any).selected()).toBeNull();
  });

  it('should find a chain by its component techniques and accept an implied equipment left out', () => {
    expect(usualNameOf(options, 2, ['1:POWER'], [])).toEqual(powerChain);
    expect(usualNameOf(options, 2, [], [])).toEqual(chain);
    expect(usualNameOf(options, 3, [], [])).toEqual(swing);
    expect(usualNameOf(options, 9, [], [])).toBeNull();
  });
});
