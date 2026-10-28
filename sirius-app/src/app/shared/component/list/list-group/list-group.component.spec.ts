import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideIcons} from '@ng-icons/core';
import {vi} from 'vitest';
import {APP_ICONS} from '../../../../app.config';
import {ListGroupComponent} from './list-group.component';

describe('ListGroupComponent', () => {
  let fixture: ComponentFixture<ListGroupComponent>;
  const trigger = (): HTMLButtonElement => fixture.nativeElement.querySelector('button');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListGroupComponent],
      providers: [provideIcons(APP_ICONS)]
    }).compileComponents();
    fixture = TestBed.createComponent(ListGroupComponent);
    fixture.componentRef.setInput('title', 'Squat');
    fixture.componentRef.setInput('count', 7);
    fixture.detectChanges();
  });

  it('should show the title and the count on a collapsed trigger', () => {
    expect(trigger().textContent).toContain('Squat');
    expect(trigger().textContent).toContain('7');
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
  });

  it('should follow the expanded input and report toggles', () => {
    const emitted = vi.fn();
    fixture.componentInstance.expandedChange.subscribe(emitted);
    fixture.componentRef.setInput('expanded', true);
    fixture.detectChanges();
    expect(trigger().getAttribute('aria-expanded')).toBe('true');

    trigger().click();
    fixture.detectChanges();

    expect(emitted).toHaveBeenCalledWith(false);
  });
});
