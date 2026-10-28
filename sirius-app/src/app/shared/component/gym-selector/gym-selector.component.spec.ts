import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {vi} from 'vitest';
import {GymSelectorComponent} from './gym-selector.component';
import {AccessService} from '../../../core/services/access.service';
import {MembershipSummary} from '../../../api/antares/models/membership.model';

describe('GymSelectorComponent', () => {
  let component: GymSelectorComponent;
  let fixture: ComponentFixture<GymSelectorComponent>;

  const memberships: MembershipSummary[] = [
    {gymId: 4, gymName: 'Test Box', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: false},
    {gymId: 7, gymName: 'Test Prog', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: true},
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymSelectorComponent],
      providers: [
        provideTranslateService(),
        {provide: AccessService, useValue: {memberships: signal(memberships)}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GymSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should link a visually hidden label to the trigger button', () => {
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('button[role="combobox"]');
    const label: HTMLLabelElement = fixture.nativeElement.querySelector('label.sr-only');
    expect(label.htmlFor).toBe(trigger.id);
    expect(label.textContent?.trim()).toBe('SHARED.GYM_SELECTOR.LABEL');
  });

  it('should show "all" when no gym is selected and the gym name otherwise', () => {
    const toLabel = (component as any).toLabel;
    expect(toLabel((component as any).selected())).toBe('SHARED.GYM_SELECTOR.ALL');

    fixture.componentRef.setInput('value', 7);
    expect(toLabel((component as any).selected())).toBe('Test Prog');
  });

  it('should map the "all" option back to null', () => {
    const emitted = vi.fn();
    component.value.subscribe(emitted);

    (component as any).onChange(4);
    expect(component.value()).toBe(4);

    (component as any).onChange((component as any).ALL);
    expect(component.value()).toBeNull();
    expect(emitted).toHaveBeenLastCalledWith(null);
  });
});
