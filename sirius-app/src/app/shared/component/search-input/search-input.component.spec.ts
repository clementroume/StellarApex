import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SearchInputComponent} from './search-input.component';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';
import {vi} from 'vitest';

describe('SearchInputComponent', () => {
  let component: SearchInputComponent;
  let fixture: ComponentFixture<SearchInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchInputComponent],
      providers: [provideIcons(APP_ICONS)]
    }).compileComponents();

    fixture = TestBed.createComponent(SearchInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should display the correct placeholder and value', () => {
    fixture.componentRef.setInput('placeholder', 'Rechercher un muscle...');
    fixture.componentRef.setInput('value', 'Biceps');
    fixture.detectChanges();

    const inputElement: HTMLInputElement = fixture.nativeElement.querySelector('input');
    expect(inputElement.placeholder).toBe('Rechercher un muscle...');
    expect(inputElement.value).toBe('Biceps');
  });

  it('should drop its bottom margin when compact', () => {
    const wrapper = () => fixture.nativeElement.firstElementChild as HTMLElement;
    expect(wrapper().classList.contains('mb-8')).toBe(true);
    fixture.componentRef.setInput('compact', true);
    fixture.detectChanges();
    expect(wrapper().classList.contains('mb-8')).toBe(false);
  });

  it('should emit the new value when the user types in the input', () => {
    vi.spyOn(component.valueChange, 'emit');

    const inputElement: HTMLInputElement = fixture.nativeElement.querySelector('input');

    inputElement.value = 'Triceps';
    inputElement.dispatchEvent(new Event('input'));

    expect(component.valueChange.emit).toHaveBeenCalledWith('Triceps');
  });
});
