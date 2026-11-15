import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {provideTranslateService} from '@ngx-translate/core';
import {APP_ICONS} from '../../app.config';
import {MuscleBalance} from '../../api/aldebaran/models/balance.model';
import {ReviewMusclesComponent} from './review-muscles.component';

const MUSCLES: MuscleBalance[] = [
  {muscleId: 1, code: 'quadriceps-femoris', region: 'THIGH_LEG', medicalName: 'Quadriceps Femoris', commonNameFr: 'Quadriceps', movingSets: 10, holdingSets: 0},
  {muscleId: 2, code: 'rectus-abdominis', region: 'ABDOMEN_PELVIS', medicalName: 'Rectus Abdominis', movingSets: 1, holdingSets: 8},
  {muscleId: 3, code: 'psoas-major', region: 'ABDOMEN_PELVIS', medicalName: 'Psoas Major', movingSets: 2, holdingSets: 0}
];

describe('ReviewMusclesComponent', () => {
  let fixture: ComponentFixture<ReviewMusclesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReviewMusclesComponent],
      providers: [provideRouter([]), provideIcons(APP_ICONS), provideTranslateService()]
    }).compileComponents();
    fixture = TestBed.createComponent(ReviewMusclesComponent);
    fixture.componentRef.setInput('muscles', MUSCLES);
    fixture.detectChanges();
  });

  const component = () => fixture.componentInstance;

  it('shades each tab on its own scale', () => {
    expect(component().heat()['quadriceps']).toBe(5);
    expect(component().heat()['abs']).toBe(1);
    component().pickTab('holding');
    expect(component().heat()['abs']).toBe(5);
    expect(component().heat()['quadriceps']).toBe(0);
  });

  it('shades the holding tab in the third colour', () => {
    expect(fixture.nativeElement.classList).not.toContain('holding');
    component().pickTab('holding');
    fixture.detectChanges();
    expect(fixture.nativeElement.classList).toContain('holding');
    expect(component().barColour()).toBe('bg-accent');
  });

  it('sorts the 7 region bars by the tab value', () => {
    const bars = component().regionBars();
    expect(bars).toHaveLength(7);
    expect(bars[0].key).toBe('THIGH_LEG');
    expect(bars[1].key).toBe('ABDOMEN_PELVIS');
  });

  it('lists the muscles of a region, undrawn ones included, under its bar', async () => {
    expect(component().musclesByRegion()['ABDOMEN_PELVIS'].map(m => m.label)).toEqual(['Psoas Major', 'Rectus Abdominis']);
    const triggers = [...fixture.nativeElement.querySelectorAll('[data-slot="accordion-trigger"]')] as HTMLButtonElement[];
    expect(triggers).toHaveLength(7);
    triggers[1].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(triggers[1].getAttribute('aria-expanded')).toBe('true');
    const list = fixture.nativeElement.querySelector('[data-slot="muscles-ABDOMEN_PELVIS"]') as HTMLElement;
    expect(list.textContent).toContain('Psoas Major');
  });

  it('keeps the drawing a picture: no click on it', () => {
    expect(fixture.nativeElement.querySelector('app-body-map path.heat')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-body-map path[style*="cursor"]')).toBeNull();
  });
});
