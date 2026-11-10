import {TestBed} from '@angular/core/testing';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {describe, expect, it, vi} from 'vitest';
import {APP_ICONS} from '../../../app.config';
import {MuscleModelEntry} from '../muscle-model/muscle-model';
import {cameraPositionOf, MuscleViewerComponent} from './muscle-viewer.component';

const ENTRY: MuscleModelEntry = {parts: ['a'], view: 'FRONT', zoom: 2, center: [1, 2, 3], size: 0.5};

describe('MuscleViewerComponent', () => {
  it('stands the camera at size x zoom from the centre, in the direction of the view', () => {
    const [x, y, z] = cameraPositionOf(ENTRY);
    expect(Math.hypot(x - 1, y - 2, z - 3)).toBeCloseTo(1, 5);
    expect(z).toBeGreaterThan(3);
    expect(x).toBeCloseTo(1, 5);
  });

  it('reports a failure instead of throwing when WebGL is unavailable', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      imports: [MuscleViewerComponent],
      providers: [provideTranslateService(), provideIcons(APP_ICONS)]
    });
    const fixture = TestBed.createComponent(MuscleViewerComponent);
    fixture.componentRef.setInput('entry', ENTRY);
    fixture.componentRef.setInput('theme', 'dark');
    const failed = vi.fn();
    fixture.componentInstance.failed.subscribe(failed);

    await fixture.whenStable();

    expect(failed).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.loaded()).toBe(false);
    fixture.destroy();
    vi.restoreAllMocks();
  });
});
