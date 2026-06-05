import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DetailHeaderComponent} from './detail-header.component';
import {provideIcons} from '@ng-icons/core';
import {heroArrowLeft} from '@ng-icons/heroicons/outline';
import {vi} from 'vitest';

describe('DetailHeaderComponent', () => {
  let component: DetailHeaderComponent;
  let fixture: ComponentFixture<DetailHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailHeaderComponent],
      providers: [
        provideIcons({hugeCircleArrowLeft02: heroArrowLeft})
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DetailHeaderComponent);
    component = fixture.componentInstance;
  });

  it('should display the main title', () => {
    fixture.componentRef.setInput('title', 'Main Title');
    fixture.detectChanges();
    const h1 = fixture.nativeElement.querySelector('h1');
    expect(h1.textContent).toContain('Main Title');
  });

  it('should display the subtitle if provided', () => {
    fixture.componentRef.setInput('title', 'Title');
    fixture.componentRef.setInput('subtitle', 'Subtitle');
    fixture.detectChanges();
    const subtitleSpan = fixture.nativeElement.querySelector('h1 span');
    expect(subtitleSpan).toBeTruthy();
    expect(subtitleSpan.textContent).toContain('(Subtitle)');
  });

  it('should emit back event on button click', () => {
    vi.spyOn(component.back, 'emit');
    const button = fixture.nativeElement.querySelector('button');
    button.click();
    expect(component.back.emit).toHaveBeenCalled();
  });
});
