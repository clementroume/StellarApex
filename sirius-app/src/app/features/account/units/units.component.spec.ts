import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {vi} from 'vitest';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {UnitsComponent} from './units.component';
import {UnitService} from '../../../core/services/unit.service';
import {UnitSystem} from '../../../api/antares/models/user.model';
import {APP_ICONS} from '../../../app.config';

describe('UnitsComponent', () => {
  let fixture: ComponentFixture<UnitsComponent>;
  let unitServiceMock: {unitSystem: ReturnType<typeof signal<UnitSystem>>; setUnitSystem: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    unitServiceMock = {
      unitSystem: signal<UnitSystem>('METRIC'),
      setUnitSystem: vi.fn().mockName('UnitService.setUnitSystem')
    };
    await TestBed.configureTestingModule({
      imports: [UnitsComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: UnitService, useValue: unitServiceMock}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UnitsComponent);
    fixture.detectChanges();
  });

  it('should check the current system', () => {
    const metric: HTMLInputElement = fixture.nativeElement.querySelector('#unit-METRIC');
    const imperial: HTMLInputElement = fixture.nativeElement.querySelector('#unit-IMPERIAL');
    expect(metric.checked).toBe(true);
    expect(imperial.checked).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('UNITS.METRIC_EXAMPLE');
  });

  it('should save the chosen system', () => {
    const imperial: HTMLInputElement = fixture.nativeElement.querySelector('#unit-IMPERIAL');
    imperial.click();

    expect(unitServiceMock.setUnitSystem).toHaveBeenCalledWith('IMPERIAL');
  });
});
