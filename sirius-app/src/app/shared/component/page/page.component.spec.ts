import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component} from '@angular/core';
import {PageComponent} from './page.component';

@Component({
  standalone: true,
  imports: [PageComponent],
  template: `
    <app-page title="Today" subtitle="Your day">
      <button pageActions>Log</button>
      <p class="content">Body</p>
    </app-page>
  `
})
class HostComponent {
}

describe('PageComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [HostComponent]}).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('should render the title, subtitle, actions and content', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Today');
    expect(el.querySelector('header p')?.textContent?.trim()).toBe('Your day');
    expect(el.querySelector('header button')?.textContent).toContain('Log');
    expect(el.querySelector('.content')).not.toBeNull();
  });
});
