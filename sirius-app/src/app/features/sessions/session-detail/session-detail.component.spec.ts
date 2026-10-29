import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {Location} from '@angular/common';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {EMPTY, of} from 'rxjs';
import {vi} from 'vitest';
import {SessionDetailComponent} from './session-detail.component';
import {APP_ICONS} from '../../../app.config';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {SessionResponse} from '../../../api/aldebaran/models/session.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {SessionBlockComponent} from '../session-block/session-block.component';
import {Component, input} from '@angular/core';

/** Blocks are tested apart (scores, WOD loading). */
@Component({selector: 'app-session-block', standalone: true, template: '<p>block {{ block().id }}</p>'})
class FakeBlockComponent {
  readonly block = input<any>();
  readonly log = input<any>();
}

describe('SessionDetailComponent', () => {
  let fixture: ComponentFixture<SessionDetailComponent>;
  const sessionService = {
    getSession: vi.fn().mockName('SessionService.getSession'),
    logSession: vi.fn().mockName('SessionService.logSession'),
    updateVisibility: vi.fn().mockName('SessionService.updateVisibility'),
    deleteSession: vi.fn().mockName('SessionService.deleteSession'),
    setBlockDone: vi.fn(), deleteLog: vi.fn(), notifyRefresh: vi.fn(), refreshNeeded$: EMPTY
  };
  const notifications = {showSuccess: vi.fn(), showError: vi.fn()};

  const session = (overrides: Partial<SessionResponse> = {}): SessionResponse => ({
    id: 5, type: 'WOD', discipline: 'CROSSFIT', date: '2026-11-03', endDate: '2026-11-03', title: 'Tuesday', gymId: 4,
    authorId: 1, visibilityPolicy: 'AFTER_PARTICIPANTS', contentVisibleFrom: '2026-11-03T23:00:00Z', createdAt: '', updatedAt: '',
    descriptionEn: null, descriptionFr: null,
    blocks: [{id: 11, orderIndex: 1, kind: 'SKILL', optional: false, themeEn: 'Heavy squat', borrowed: false, competition: false}],
    capabilities: {canModify: false, locked: false, contentVisible: false, canLog: true},
    myLog: null,
    ...overrides
  });

  const init = (s: SessionResponse) => {
    sessionService.getSession.mockReturnValue(of(s));
    fixture = TestBed.createComponent(SessionDetailComponent);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [SessionDetailComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: SessionService, useValue: sessionService},
        {provide: AccessService, useValue: {memberships: signal([{gymId: 4, gymName: 'Spartacus', gymProgramming: false}])}},
        {provide: NotificationService, useValue: notifications},
        {provide: Location, useValue: {back: vi.fn()}}]
    }).overrideComponent(SessionDetailComponent, {
      remove: {imports: [SessionBlockComponent]}, add: {imports: [FakeBlockComponent]}
    }).compileComponents();
  });

  it('should show the frame only and "Séance faite" to an athlete before participation', () => {
    init(session());
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Tuesday');
    expect(text).toContain('Heavy squat');
    expect(fixture.nativeElement.querySelector('#content-hidden').textContent).toContain('SESSION.POLICIES.AFTER_PARTICIPANTS.HIDDEN');
    expect(fixture.nativeElement.querySelector('app-session-block')).toBeNull();
    expect(fixture.nativeElement.querySelector('#edit-session')).toBeNull();

    sessionService.logSession.mockReturnValue(of({id: 9}));
    (fixture.nativeElement.querySelector('#log-session') as HTMLButtonElement).click();
    expect(sessionService.logSession).toHaveBeenCalledWith(5, expect.any(String));
    expect(notifications.showSuccess).toHaveBeenCalledWith('SESSION.MESSAGES.LOGGED');
    expect(sessionService.getSession).toHaveBeenCalledTimes(2);
  });

  it('should show the blocks and the journal once the content is visible', () => {
    init(session({
      capabilities: {canModify: false, locked: true, contentVisible: true, canLog: false},
      myLog: {id: 9, userId: 2, sessionId: 5, sessionType: 'WOD', discipline: 'CROSSFIT', sessionDate: '2026-11-03', gymId: 4,
        date: '2026-11-03', source: 'MANUAL', blocks: [{blockId: 11, kind: 'SKILL', done: true}], scores: []}
    }));
    expect(fixture.nativeElement.querySelectorAll('app-session-block').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('SESSION.LOGGED_ON');
    expect(fixture.nativeElement.querySelector('#log-session')).toBeNull();
    expect(fixture.nativeElement.querySelector('#unlog')).toBeTruthy();
  });

  it('should let staff edit, delete and change the visibility of an unlocked session', () => {
    init(session({capabilities: {canModify: true, locked: false, contentVisible: true, canLog: true}}));
    expect(fixture.nativeElement.querySelector('#edit-session').getAttribute('href')).toBe('/box/sessions/5/edit');
    expect(fixture.nativeElement.querySelector('#visibility-badge').textContent).toContain('SESSION.POLICIES.AFTER_PARTICIPANTS.LABEL');

    sessionService.updateVisibility.mockReturnValue(of(session({visibilityPolicy: 'DAY_OF'})));
    (fixture.nativeElement.querySelector('#edit-visibility') as HTMLButtonElement).click();
    const component = fixture.componentInstance as any;
    component.setDraftPolicy('DAY_OF');
    component.setDraftAt('2026-11-03T18:00');
    component.saveVisibility();
    expect(sessionService.updateVisibility).toHaveBeenCalledWith(5, {visibilityPolicy: 'DAY_OF', contentVisibleAt: '2026-11-03T18:00'});

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    sessionService.deleteSession.mockReturnValue(of(undefined));
    component.deleteSession();
    expect(sessionService.deleteSession).toHaveBeenCalledWith(5);
    expect(router.navigate).toHaveBeenCalledWith(['/box/sessions']);
  });

  it('should hide edit and delete once someone logged the session', () => {
    init(session({capabilities: {canModify: true, locked: true, contentVisible: true, canLog: false}}));
    expect(fixture.nativeElement.querySelector('#edit-session')).toBeNull();
    expect(fixture.nativeElement.querySelector('#delete-session')).toBeNull();
    expect(fixture.nativeElement.querySelector('#edit-visibility')).toBeTruthy();
  });
});
