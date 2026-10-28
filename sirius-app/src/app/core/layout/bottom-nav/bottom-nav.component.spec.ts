import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {BottomNavComponent} from './bottom-nav.component';
import {NavSpace, SpacesService} from '../spaces.service';
import {APP_ICONS} from '../../../app.config';

describe('BottomNavComponent', () => {
  let fixture: ComponentFixture<BottomNavComponent>;
  const spaces: NavSpace[] = [
    {key: 'today', link: '/today', label: 'NAVBAR.SPACES.TODAY', icon: 'heroHome', paths: ['/today'], children: []},
    {key: 'catalog', link: '/wods', label: 'NAVBAR.SPACES.CATALOG', icon: 'heroBookOpen', paths: ['/wods'], children: []},
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BottomNavComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: SpacesService, useValue: {spaces: signal(spaces), current: signal(spaces[1])}}]
    }).compileComponents();
    fixture = TestBed.createComponent(BottomNavComponent);
    fixture.detectChanges();
  });

  it('should render one tab per space with the current one marked', () => {
    const tabs = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(tabs.map(t => t.textContent?.trim())).toEqual(['NAVBAR.SPACES.TODAY', 'NAVBAR.SPACES.CATALOG']);
    expect(tabs[1].getAttribute('aria-current')).toBe('page');
    expect(tabs[0].getAttribute('aria-current')).toBeNull();
  });
});
