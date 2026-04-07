import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ListItemComponent} from './list-item.component';
import {provideRouter} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../../app.config';
import {By} from '@angular/platform-browser';

describe('ListItemComponent', () => {
  let component: ListItemComponent;
  let fixture: ComponentFixture<ListItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListItemComponent],
      providers: [
        provideRouter([]), // Nécessaire pour [routerLink]
        provideIcons(APP_ICONS) // Nécessaire pour <ng-icon>
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ListItemComponent);
    component = fixture.componentInstance;

    // On fournit les inputs obligatoires par défaut pour éviter les erreurs d'initialisation
    component.title = 'Test Title';
    component.link = ['/test', 1];
  });

  it('should create the component', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display the title and the first letter as avatar when no image is provided', () => {
    component.title = 'Biceps';
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    // Vérifie le titre
    expect(textContent).toContain('Biceps');
    // Vérifie la lettre majuscule de l'avatar (générée via title.charAt(0) | uppercase)
    expect(textContent).toContain('B');

    // Vérifie qu'il n'y a pas de balise image
    const imgElement = fixture.debugElement.query(By.css('img'));
    expect(imgElement).toBeNull();
  });

  it('should display an image when imageUrl is provided', () => {
    component.imageUrl = 'https://fake-image.url/muscle.png';
    fixture.detectChanges();

    const imgElement = fixture.debugElement.query(By.css('img'));
    expect(imgElement).toBeTruthy();
    // NgOptimizedImage transforme [ngSrc] en attribut src natif sur l'élément DOM
    expect(imgElement.nativeElement.src).toContain('fake-image.url/muscle.png');
  });

  it('should display the badge when provided', () => {
    component.title = 'Back Squat';
    component.badge = 'BS';
    fixture.detectChanges();

    const badgeElement = fixture.debugElement.query(By.css('.badge'));
    expect(badgeElement).toBeTruthy();
    expect(badgeElement.nativeElement.textContent.trim()).toBe('BS');
  });

  it('should not display the badge if not provided', () => {
    component.badge = undefined;
    fixture.detectChanges();

    const badgeElement = fixture.debugElement.query(By.css('.badge'));
    expect(badgeElement).toBeNull();
  });

  it('should display the subtitle when provided', () => {
    component.title = 'Pectoraux';
    component.subtitle = 'Pectoralis major';
    fixture.detectChanges();

    // On cherche l'élément avec la classe opacité-60 que l'on a mise pour le sous-titre
    const subtitleElement = fixture.debugElement.query(By.css('.opacity-60'));
    expect(subtitleElement).toBeTruthy();
    expect(subtitleElement.nativeElement.textContent.trim()).toBe('Pectoralis major');
  });

  it('should not display the subtitle if not provided', () => {
    component.subtitle = undefined;
    fixture.detectChanges();

    const subtitleElement = fixture.debugElement.query(By.css('.opacity-60'));
    expect(subtitleElement).toBeNull();
  });

  it('should bind the correct routerLink', () => {
    component.link = ['/movements', 42];
    fixture.detectChanges();

    const linkElement = fixture.debugElement.query(By.css('a'));
    // Angular attache l'attribut href via la directive routerLink
    expect(linkElement.nativeElement.getAttribute('href')).toBe('/movements/42');
  });
});
