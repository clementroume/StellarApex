import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component, signal} from '@angular/core';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {FormFieldComponent} from './form-field.component';

@Component({
  standalone: true,
  imports: [FormFieldComponent, HlmInputGroupImports],
  template: `
    <app-form-field for="email" label="Email" icon="heroEnvelope" [error]="error()" hint="We never share it">
      <input hlmInputGroupInput id="email" type="email"/>
    </app-form-field>
  `
})
class HostComponent {
  error = signal<string | null>(null);
}

describe('FormFieldComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideIcons(APP_ICONS)]
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('should label the projected control', () => {
    const label = el().querySelector('label')!;
    expect(label.htmlFor).toBe('email');
    expect(label.textContent?.trim()).toBe('Email');
    expect(el().querySelector('input#email')).not.toBeNull();
  });

  it('should show the hint, then the error in its place', () => {
    expect(el().querySelector('#email-message')?.textContent?.trim()).toBe('We never share it');

    fixture.componentInstance.error.set('Enter a valid email address.');
    fixture.detectChanges();

    const message = el().querySelector('#email-message')!;
    expect(message.textContent?.trim()).toBe('Enter a valid email address.');
    expect(message.getAttribute('role')).toBe('alert');
    expect(el().querySelector('[data-invalid="true"]')).not.toBeNull();
  });
});
