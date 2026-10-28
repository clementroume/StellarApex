import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {SpaceNavComponent} from './space-nav.component';
import {NavSpace, SpacesService} from '../spaces.service';
import {APP_ICONS} from '../../../app.config';

describe('SpaceNavComponent', () => {
  let fixture: ComponentFixture<SpaceNavComponent>;
  const current = signal<NavSpace | null>(null);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpaceNavComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: SpacesService, useValue: {current}}]
    }).compileComponents();
    fixture = TestBed.createComponent(SpaceNavComponent);
  });

  it('should show the sub-navigation of a space with several pages only', () => {
    current.set({key: 'today', link: '/today', label: 'T', icon: 'heroHome', paths: ['/today'], children: []});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav')).toBeNull();

    current.set({
      key: 'catalog', link: '/wods', label: 'C', icon: 'heroBookOpen', paths: ['/wods'],
      children: [{link: '/wods', label: 'W', icon: 'hugeKettlebell'}, {link: '/movements', label: 'M', icon: 'hugeWorkoutStretching'}]
    });
    fixture.detectChanges();
    const links = Array.from(fixture.nativeElement.querySelectorAll('nav a')) as HTMLAnchorElement[];
    expect(links.map(a => a.getAttribute('href'))).toEqual(['/wods', '/movements']);
  });
});
