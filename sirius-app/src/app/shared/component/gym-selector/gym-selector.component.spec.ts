import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {GymSelectorComponent} from './gym-selector.component';
import {AccessService} from '../../../core/services/access.service';
import {MembershipSummary} from '../../../api/antares/models/membership.model';

describe('GymSelectorComponent', () => {
  let component: GymSelectorComponent;
  let fixture: ComponentFixture<GymSelectorComponent>;

  const memberships: MembershipSummary[] = [
    {gymId: 4, gymName: 'Test Box', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: false, usable: true},
    {gymId: 7, gymName: 'Test Prog', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: true, usable: true},
  ];
  const select = (): HTMLSelectElement => fixture.nativeElement.querySelector('select');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GymSelectorComponent],
      providers: [provideTranslateService(), {provide: AccessService, useValue: {memberships: signal(memberships)}}]
    }).compileComponents();

    fixture = TestBed.createComponent(GymSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should label the select and offer all then each box/prog', () => {
    const label: HTMLLabelElement = fixture.nativeElement.querySelector('label.sr-only');
    expect(label.htmlFor).toBe(select().id);
    expect(Array.from(select().options).map(o => o.textContent?.trim()))
      .toEqual(['SHARED.GYM_SELECTOR.ALL', 'Test Box · SHARED.GYM_SELECTOR.BOX', 'Test Prog · SHARED.GYM_SELECTOR.PROG']);
  });

  it('should select the bound gym and map "all" back to null', () => {
    fixture.componentRef.setInput('value', 7);
    fixture.detectChanges();
    expect(select().value).toBe('7');

    select().value = '0';
    select().dispatchEvent(new Event('change'));
    expect(component.value()).toBeNull();

    select().value = '4';
    select().dispatchEvent(new Event('change'));
    expect(component.value()).toBe(4);
  });
});
