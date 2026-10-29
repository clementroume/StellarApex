import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {SessionCardComponent} from './session-card.component';
import {APP_ICONS} from '../../../app.config';
import {AccessService} from '../../../core/services/access.service';
import {SessionSummaryResponse} from '../../../api/aldebaran/models/session.model';

describe('SessionCardComponent', () => {
  let fixture: ComponentFixture<SessionCardComponent>;
  const memberships = signal<any[]>([]);

  const summary = (overrides: Partial<SessionSummaryResponse> = {}): SessionSummaryResponse => ({
    id: 3, type: 'WOD', discipline: 'CROSSFIT', date: '2026-11-03', endDate: '2026-11-03', title: null, gymId: 4,
    authorId: 1, contentVisible: false, loggedByMe: false,
    blocks: [
      {kind: 'WARM_UP', optional: false},
      {kind: 'SKILL', optional: false, themeEn: 'Heavy squat', themeFr: 'Squat lourd'},
      {kind: 'WOD', optional: false},
      {kind: 'ACCESSORY', optional: true, themeEn: 'Core'}
    ],
    ...overrides
  });

  const init = (session: SessionSummaryResponse) => {
    fixture = TestBed.createComponent(SessionCardComponent);
    fixture.componentRef.setInput('session', session);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionCardComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: AccessService, useValue: {memberships}}]
    }).compileComponents();
  });

  it('should show the frame: type, blocks with their themes, hidden content', () => {
    init(summary());
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('SESSION.TYPES.WOD');
    expect(text).toContain('Heavy squat');
    expect(text).toContain('SESSION.HIDDEN');
    const kinds = Array.from(fixture.nativeElement.querySelectorAll('li') as NodeListOf<HTMLElement>).map(li => li.textContent!.trim());
    expect(kinds[0]).toBe('SESSION.KINDS.WARM_UP');
    expect(kinds[1]).toContain('SESSION.KINDS.SKILL');
    expect(kinds[3]).toContain('SESSION.KINDS.ACCESSORY');
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/box/sessions/3');
  });

  it('should show the title, the done badge, and link a personal session to My training', () => {
    init(summary({title: 'Murph day', gymId: null, loggedByMe: true, contentVisible: true}));
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Murph day');
    expect(text).toContain('SESSION.DONE');
    expect(text).not.toContain('SESSION.HIDDEN');
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/training/sessions/3');
  });
});
