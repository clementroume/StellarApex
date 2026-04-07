import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ListStateComponent} from './list-state.component';
import {Component} from '@angular/core';
import {By} from '@angular/platform-browser';

@Component({
  template: `
    <app-list-state
      [isLoading]="isLoading"
      [isRawEmpty]="isRawEmpty"
      [isFilteredEmpty]="isFilteredEmpty"
      [emptyMessage]="'Le catalogue est vide'"
      [emptySearchMessage]="'Aucun résultat pour cette recherche'">

      <div class="my-projected-content">La liste des éléments</div>

    </app-list-state>
  `,
  standalone: true,
  imports: [ListStateComponent]
})
class TestHostComponent {
  isLoading = false;
  isRawEmpty = false;
  isFilteredEmpty = false;
}

describe('ListStateComponent', () => {
  let hostComponent: TestHostComponent;
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    hostComponent = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should show the loading spinner when loading and raw list is empty', () => {
    hostComponent.isLoading = true;
    hostComponent.isRawEmpty = true;
    fixture.detectChanges();

    const spinner = fixture.debugElement.query(By.css('.loading-spinner'));
    const projectedContent = fixture.debugElement.query(By.css('.my-projected-content'));

    expect(spinner).toBeTruthy();
    expect(projectedContent).toBeNull();
  });

  it('should show the empty message when there is no data at all', () => {
    hostComponent.isRawEmpty = true;
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('Le catalogue est vide');
  });

  it('should show the empty search message when filtered list is empty', () => {
    hostComponent.isFilteredEmpty = true;
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('Aucun résultat pour cette recherche');
  });

  it('should project the actual content when there are items to display', () => {
    const projectedContent = fixture.debugElement.query(By.css('.my-projected-content'));
    const textContent = fixture.nativeElement.textContent;

    expect(projectedContent).toBeTruthy();
    expect(textContent).toContain('La liste des éléments');
  });
});
