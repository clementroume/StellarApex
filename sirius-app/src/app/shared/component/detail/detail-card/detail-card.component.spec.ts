import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DetailCardComponent} from './detail-card.component';
import {Component} from '@angular/core';

@Component({
  standalone: true,
  imports: [DetailCardComponent],
  template: `
    <app-detail-card title="Test Title" paddingClass="p-4" gapClass="gap-2">
      <p class="projected-content">Projected content</p>
    </app-detail-card>
  `
})
class TestHostComponent {
}

describe('DetailCardComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let hostElement: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    hostElement = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('should display the title if provided', () => {
    const titleEl = hostElement.querySelector('h3');
    expect(titleEl).toBeTruthy();
    expect(titleEl?.textContent?.trim()).toBe('Test Title');
  });

  it('should project the content', () => {
    const projectedEl = hostElement.querySelector('.projected-content');
    expect(projectedEl).toBeTruthy();
    expect(projectedEl?.textContent).toBe('Projected content');
  });

  it('should apply padding and gap classes', () => {
    const cardEl = hostElement.querySelector('.bg-base-100');
    expect(cardEl?.classList.contains('p-4')).toBeTrue();
    expect(cardEl?.classList.contains('gap-2')).toBeTrue();
  });
});
