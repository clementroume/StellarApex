import * as heroicons from '@ng-icons/heroicons/outline';
import * as hugeicons from '@ng-icons/huge-icons';
import {LAYOUT_ICONS} from './layout-icons';

describe('LAYOUT_ICONS', () => {
  it('copies every icon exactly from its package', () => {
    const packages: Record<string, string> = {...heroicons, ...hugeicons} as unknown as Record<string, string>;
    for (const [name, svg] of Object.entries(LAYOUT_ICONS)) {
      expect(svg, name).toBe(packages[name]);
    }
  });

  it('holds the layout icons only', () => {
    expect(Object.keys(LAYOUT_ICONS).length).toBe(20);
  });
});
