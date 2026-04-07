import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component} from '@angular/core';
import {By} from '@angular/platform-browser';
import {ListGridComponent} from './list-grid.component';

// 1. Faux composant parent pour tester la projection du template
@Component({
  template: `
    <app-list-grid [items]="items">

      <!-- Le template qu'on projette, avec let-item pour récupérer l'élément -->
      <ng-template let-item>
        <div class="test-item">{{ item }}</div>
      </ng-template>

    </app-list-grid>
  `,
  standalone: true,
  imports: [ListGridComponent]
})
class TestHostComponent {
  // On lui donne une liste de 4 éléments pour tester nos index pairs/impairs
  items = ['Item 0', 'Item 1', 'Item 2', 'Item 3'];
}

describe('ListGridComponent', () => {
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

  it('should create the grid component', () => {
    const gridElement = fixture.debugElement.query(By.directive(ListGridComponent));
    expect(gridElement).toBeTruthy();
  });

  it('should render all items sequentially in the mobile view (md:hidden)', () => {
    // On cible le conteneur mobile grâce à sa classe
    const mobileContainer = fixture.debugElement.query(By.css('.md\\:hidden'));
    const mobileItems = mobileContainer.queryAll(By.css('.test-item'));

    expect(mobileItems.length).toBe(4);
    expect(mobileItems[0].nativeElement.textContent.trim()).toBe('Item 0');
    expect(mobileItems[1].nativeElement.textContent.trim()).toBe('Item 1');
    expect(mobileItems[2].nativeElement.textContent.trim()).toBe('Item 2');
    expect(mobileItems[3].nativeElement.textContent.trim()).toBe('Item 3');
  });

  it('should split items into two columns (even/odd) in the desktop view (hidden md:flex)', () => {
    // On cible le conteneur desktop
    const desktopContainer = fixture.debugElement.query(By.css('.hidden.md\\:flex'));

    // Il doit contenir 2 colonnes (flex-1)
    const columns = desktopContainer.queryAll(By.css('.flex-1'));
    expect(columns.length).toBe(2);

    const leftColumnItems = columns[0].queryAll(By.css('.test-item'));
    const rightColumnItems = columns[1].queryAll(By.css('.test-item'));

    // Vérification de la colonne de GAUCHE (Index pairs : 0 et 2)
    expect(leftColumnItems.length).toBe(2);
    expect(leftColumnItems[0].nativeElement.textContent.trim()).toBe('Item 0');
    expect(leftColumnItems[1].nativeElement.textContent.trim()).toBe('Item 2');

    // Vérification de la colonne de DROITE (Index impairs : 1 et 3)
    expect(rightColumnItems.length).toBe(2);
    expect(rightColumnItems[0].nativeElement.textContent.trim()).toBe('Item 1');
    expect(rightColumnItems[1].nativeElement.textContent.trim()).toBe('Item 3');
  });

  it('should update the grid when input items change', () => {
    // On simule un filtrage qui ne laisserait qu'un seul élément
    hostComponent.items = ['Single Item'];
    fixture.detectChanges();

    const desktopContainer = fixture.debugElement.query(By.css('.hidden.md\\:flex'));
    const columns = desktopContainer.queryAll(By.css('.flex-1'));

    const leftColumnItems = columns[0].queryAll(By.css('.test-item'));
    const rightColumnItems = columns[1].queryAll(By.css('.test-item'));

    // L'élément doit être à gauche (index 0) et la droite doit être vide
    expect(leftColumnItems.length).toBe(1);
    expect(leftColumnItems[0].nativeElement.textContent.trim()).toBe('Single Item');
    expect(rightColumnItems.length).toBe(0);
  });
});
