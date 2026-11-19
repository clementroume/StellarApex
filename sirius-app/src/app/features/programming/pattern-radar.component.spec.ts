import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {PatternRadarComponent} from './pattern-radar.component';
import {profileOf} from './programming.fixture';

describe('PatternRadarComponent', () => {
  let fixture: ComponentFixture<PatternRadarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [PatternRadarComponent], providers: [provideTranslateService()]}).compileComponents();
    fixture = TestBed.createComponent(PatternRadarComponent);
    const p = profileOf(10);
    fixture.componentRef.setInput('axes', p.axes);
    fixture.componentRef.setInput('details', p.details);
    fixture.detectChanges();
  });

  const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;

  it('draws the full week only, then the profile when compared, with its alerts marked', () => {
    expect(el('[data-slot="radar-full"]')).not.toBeNull();
    expect(el('[data-slot="radar-profile"]')).toBeNull();
    fixture.componentRef.setInput('compare', true);
    fixture.componentRef.setInput('alerted', ['SQUAT']);
    fixture.detectChanges();
    expect(el('[data-slot="radar-profile"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(1);
  });

  it('asks to open an axis and shows its details, the profile against the full week', () => {
    const emitted: unknown[] = [];
    fixture.componentInstance.selectedChange.subscribe(v => emitted.push(v));
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('[role="group"] button')) as HTMLButtonElement[];
    expect(buttons.map(b => b.textContent!.trim())).toContain('MOVEMENT.PATTERNS.AXES.CORE');
    buttons.find(b => b.textContent!.includes('CORE'))!.click();
    expect(emitted).toEqual(['CORE']);

    fixture.componentRef.setInput('compare', true);
    fixture.componentRef.setInput('selected', 'CORE');
    fixture.detectChanges();
    const panel = el('[data-slot="radar-panel"]')!;
    expect(panel.textContent).toContain('MOVEMENT.PATTERNS.CORE_FLEXION');
    expect(panel.textContent).toContain('MOVEMENT.PATTERNS.CORE_LATERAL_FLEXION');
    // Core is not judged for this profile: said in the panel
    expect(el('[data-slot="not-judged"]')).not.toBeNull();
  });

  it('gives examples for an axis without details', () => {
    fixture.componentRef.setInput('selected', 'SQUAT');
    fixture.detectChanges();
    expect(el('[data-slot="radar-panel"]')!.textContent).toContain('PROGRAMMING.RADAR.EXAMPLES.SQUAT');
  });
});
