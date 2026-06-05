import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DetailStateComponent} from './detail-state.component';
import {Component} from '@angular/core';

@Component({
  standalone: true,
  imports: [DetailStateComponent],
  template: `
    <app-detail-state [isLoading]="loading">
      <div skeleton class="my-skeleton">Loading...</div>
      <div class="my-content">Real content!</div>
    </app-detail-state>
  `
})
class TestHostComponent {
  loading = true;
}

describe('DetailStateComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let hostComponent: TestHostComponent;
  let hostElement: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    hostComponent = fixture.componentInstance;
    hostElement = fixture.nativeElement;
  });

  it('should display only the skeleton when isLoading is true', () => {
    hostComponent.loading = true;
    fixture.detectChanges();

    const skeleton = hostElement.querySelector('.my-skeleton');
    const content = hostElement.querySelector('.my-content');

    expect(skeleton).toBeTruthy();
    expect(content).toBeFalsy();
  });

  it('should display only the content when isLoading is false', () => {
    hostComponent.loading = false;
    fixture.detectChanges();

    const skeleton = hostElement.querySelector('.my-skeleton');
    const content = hostElement.querySelector('.my-content');

    expect(skeleton).toBeFalsy();
    expect(content).toBeTruthy();
  });
});
