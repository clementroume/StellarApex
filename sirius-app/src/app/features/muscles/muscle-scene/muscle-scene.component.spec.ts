import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {APP_ICONS} from '../../../app.config';
import {ThemeService} from '../../../core/services/theme.service';
import catalog from '../muscle-model/catalog.json';
import {MuscleSceneComponent} from './muscle-scene.component';

const KNOWN = Object.keys(catalog.muscles)[0];

describe('MuscleSceneComponent (no WebGL in jsdom)', () => {
  let fixture: ComponentFixture<MuscleSceneComponent>;
  const theme = signal<'dark' | 'light'>('dark');

  const show = async (code: string | undefined, imageUrl?: string) => {
    fixture.componentRef.setInput('code', code);
    fixture.componentRef.setInput('imageUrl', imageUrl);
    fixture.componentRef.setInput('alt', 'Muscle');
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    theme.set('dark');
    TestBed.configureTestingModule({
      imports: [MuscleSceneComponent],
      providers: [provideTranslateService(), provideIcons(APP_ICONS), {provide: ThemeService, useValue: {currentTheme: theme}}]
    });
    fixture = TestBed.createComponent(MuscleSceneComponent);
  });

  it('keeps the image of a modelled muscle as the fallback, in the active theme', async () => {
    const element = await show(KNOWN);
    expect(element.querySelector('img')?.getAttribute('src')).toBe(`/muscle-model/images/dark/${KNOWN}.webp`);
    expect(element.querySelector('app-muscle-viewer')).toBeNull();

    theme.set('light');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element.querySelector('img')?.getAttribute('src')).toBe(`/muscle-model/images/light/${KNOWN}.webp`);
  });

  it('shows the image of a region, in the active theme', async () => {
    fixture.componentRef.setInput('region', 'THIGH_LEG');
    fixture.componentRef.setInput('alt', 'Legs');
    await fixture.whenStable();
    fixture.detectChanges();
    const image = (fixture.nativeElement as HTMLElement).querySelector('img');
    expect(image?.getAttribute('src')).toBe('/muscle-model/regions/dark/thigh_leg.webp');
    expect(image?.getAttribute('alt')).toBe('Legs');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-slot="muscle-credit"]')).not.toBeNull();
  });

  it('shows the catalog image of a muscle added before the model was regenerated', async () => {
    const element = await show('added-yesterday', 'https://example.com/m.png');
    expect(element.querySelector('img')?.getAttribute('src')).toBe('https://example.com/m.png');
    expect(element.querySelector('app-muscle-viewer')).toBeNull();
  });

  it('shows the empty state when such a muscle has no image at all', async () => {
    const element = await show('added-yesterday');
    expect(element.querySelector('img')).toBeNull();
    expect(element.querySelector('[data-slot="muscle-scene-empty"]')).not.toBeNull();
  });

  it('falls back to the empty state when the image cannot be loaded', async () => {
    const element = await show(KNOWN);
    element.querySelector('img')!.dispatchEvent(new Event('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element.querySelector('img')).toBeNull();
    expect(element.querySelector('[data-slot="muscle-scene-empty"]')).not.toBeNull();
  });

  it('does not throw without a code (older cached response)', async () => {
    const element = await show(undefined);
    expect(element.querySelector('[data-slot="muscle-scene-empty"]')).not.toBeNull();
  });
});
