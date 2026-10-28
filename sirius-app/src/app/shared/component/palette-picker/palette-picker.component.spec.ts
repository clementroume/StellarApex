import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {vi} from 'vitest';
import {PalettePickerComponent} from './palette-picker.component';
import {ThemeService} from '../../../core/services/theme.service';

describe('PalettePickerComponent', () => {
  let fixture: ComponentFixture<PalettePickerComponent>;
  const themeService = {
    currentPalette: signal('volt'),
    currentTheme: signal('light'),
    setPalette: vi.fn().mockName('ThemeService.setPalette')
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PalettePickerComponent],
      providers: [provideTranslateService(), {provide: ThemeService, useValue: themeService}]
    }).compileComponents();

    fixture = TestBed.createComponent(PalettePickerComponent);
    fixture.detectChanges();
  });

  const radios = (): HTMLInputElement[] => Array.from(fixture.nativeElement.querySelectorAll('input[type="radio"]'));

  it('should offer the four palettes in a named radio group, current one checked', () => {
    expect(radios().map(r => r.value)).toEqual(['ocean', 'chalk', 'volt', 'rig']);
    expect(radios().find(r => r.checked)?.value).toBe('volt');
    expect(fixture.nativeElement.querySelector('legend').textContent.trim()).toBe('SETTINGS.PALETTE_LABEL');
  });

  it('should preview each palette in the current theme', () => {
    const previews = fixture.nativeElement.querySelectorAll('[data-palette]');
    expect(previews[1].dataset.palette).toBe('chalk');
    expect(previews[1].dataset.theme).toBe('light');
  });

  it('should apply the chosen palette', () => {
    radios()[3].dispatchEvent(new Event('change'));
    expect(themeService.setPalette).toHaveBeenCalledWith('rig');
  });
});
