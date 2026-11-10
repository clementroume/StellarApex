import {afterEach, describe, expect, it, vi} from 'vitest';
import catalog from './catalog.json';
import {canShow3d, MODEL_URL, muscleImageUrl, muscleModelEntry, muscleRegionEntry, muscleRegionUrl, muscleThumbUrl} from './muscle-model';

const KNOWN = Object.keys(catalog.muscles)[0];

describe('muscle-model', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('points to the generated model file', () => {
    expect(MODEL_URL).toBe(`/muscle-model/${catalog.model}`);
  });

  it('describes every muscle of the catalog completely', () => {
    for (const [code, entry] of Object.entries(catalog.muscles)) {
      expect(entry.parts.length, code).toBeGreaterThan(0);
      expect(entry.center, code).toHaveLength(3);
      expect(entry.size, code).toBeGreaterThan(0);
    }
  });

  it('gives the theme image of a muscle of the model', () => {
    expect(muscleImageUrl(KNOWN, 'dark')).toBe(`/muscle-model/images/dark/${KNOWN}.webp`);
    expect(muscleThumbUrl(KNOWN, 'light')).toBe(`/muscle-model/thumbs/light/${KNOWN}.webp`);
    expect(muscleRegionUrl('THIGH_LEG', 'light')).toBe('/muscle-model/regions/light/thigh_leg.webp');
    expect(muscleRegionEntry('THIGH_LEG')?.parts.length).toBeGreaterThan(0);
    expect(muscleRegionEntry('NOWHERE')).toBeUndefined();
    expect(muscleRegionUrl('NOWHERE', 'light')).toBeUndefined();
  });

  it('knows nothing of a muscle added after the last export', () => {
    for (const code of ['not-in-the-model', 'constructor', '', null, undefined]) {
      expect(muscleModelEntry(code)).toBeUndefined();
      expect(muscleImageUrl(code, 'dark')).toBeUndefined();
      expect(muscleThumbUrl(code, 'dark')).toBeUndefined();
    }
  });

  describe('canShow3d', () => {
    const withWebGl = (saveData: boolean) => {
      vi.stubGlobal('WebGL2RenderingContext', class {});
      vi.stubGlobal('navigator', {connection: {saveData}});
      vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({getExtension: () => null} as never);
    };

    it('is false without WebGL', () => {
      expect(canShow3d()).toBe(false);
    });

    it('is true with WebGL 2', () => {
      withWebGl(false);
      expect(canShow3d()).toBe(true);
    });

    it('is false in data saver mode', () => {
      withWebGl(true);
      expect(canShow3d()).toBe(false);
    });
  });
});
