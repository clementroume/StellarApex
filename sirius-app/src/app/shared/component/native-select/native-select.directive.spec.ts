import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component} from '@angular/core';
import {NativeSelectDirective} from './native-select.directive';

@Component({
  standalone: true,
  imports: [NativeSelectDirective],
  template: `<select appNativeSelect class="max-w-xs"><option>A</option></select>`
})
class HostComponent {
}

describe('NativeSelectDirective', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [HostComponent]}).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('should style the native select and keep the classes set by the template', () => {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    expect(select.classList).toContain('border-input');
    expect(select.classList).toContain('max-w-xs');
  });
});
