import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {SupportComponent, USER_PAGE_SIZE, USER_SEARCH_DEBOUNCE_MS} from './support.component';
import {AdminService} from '../../../api/antares/services/admin.service';
import {SupportSessionService} from '../../../core/services/support-session.service';
import {NotificationService} from '../../../core/services/notification.service';
import {APP_ICONS} from '../../../app.config';

describe('SupportComponent', () => {
  let component: SupportComponent;
  let fixture: ComponentFixture<SupportComponent>;
  let adminService: any;
  let supportSessionService: any;
  let notificationService: any;

  const athlete: any = {
    id: 4, firstName: 'Ath', lastName: 'Lete', email: 'athlete@sparta.com', platformRole: 'USER',
    memberships: [{gymId: 1, gymName: 'Spartacus CrossFit', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'BANNED', permissions: []}]
  };
  const coach: any = {
    id: 5, firstName: 'Co', lastName: 'Ach', email: 'coach@sparta.com', platformRole: 'USER',
    memberships: [{gymId: 1, gymName: 'Spartacus CrossFit', gymStatus: 'ACTIVE', gymRole: 'COACH', status: 'ACTIVE', permissions: ['WOD_WRITE']}]
  };
  const admin: any = {id: 1, firstName: 'Ad', lastName: 'Min', email: 'admin@apex.com', platformRole: 'ADMIN', memberships: []};

  const slice = (content: any[], last: boolean) => ({content, last, first: true, size: USER_PAGE_SIZE, number: 0, numberOfElements: content.length, empty: !content.length});
  const lastSearch = () => adminService.searchUsers.mock.lastCall as any[];

  beforeEach(async () => {
    vi.useFakeTimers();
    adminService = {
      searchUsers: vi.fn().mockReturnValue(of(slice([athlete, admin], false))),
      getRecentSupportTargets: vi.fn().mockReturnValue(of([coach])),
      searchGyms: vi.fn().mockReturnValue(of({
        content: [{id: 3, name: 'Pending Box'}, {id: 1, name: 'Spartacus CrossFit'}, {id: 2, name: 'Zeus Programming'}],
        last: true
      }))
    };
    supportSessionService = {start: vi.fn().mockReturnValue(of(undefined))};
    notificationService = {showSuccess: vi.fn(), showError: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [SupportComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AdminService, useValue: adminService},
        {provide: SupportSessionService, useValue: supportSessionService},
        {provide: NotificationService, useValue: notificationService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SupportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should load the first page, the recent targets and every gym on init', () => {
    expect(lastSearch()).toEqual([{search: undefined, gymId: undefined, role: undefined, status: undefined}, 0, USER_PAGE_SIZE]);
    expect(component.users()).toEqual([athlete, admin]);
    expect(component.hasMore()).toBe(true);
    expect(component.recentUsers()).toEqual([coach]);
    expect(component.gyms().map(g => g.name)).toEqual(['Pending Box', 'Spartacus CrossFit', 'Zeus Programming']);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('ADMIN.SUPPORT.RECENT');
    expect(text).toContain('athlete@sparta.com');
    expect(text).toContain('GYM.STATUS.BANNED');
    expect(text).toContain('GYM.PERMISSIONS.WOD_WRITE.LABEL');
  });

  it('should debounce the search and ignore a single character', () => {
    adminService.searchUsers.mockClear();

    component.onSearchChange('s');
    vi.advanceTimersByTime(USER_SEARCH_DEBOUNCE_MS);
    expect(component.isSearchTooShort()).toBe(true);
    // '' is the current search already: nothing new to fetch
    expect(adminService.searchUsers).not.toHaveBeenCalled();

    component.onSearchChange('spa');
    component.onSearchChange('sparta');
    vi.advanceTimersByTime(USER_SEARCH_DEBOUNCE_MS);

    expect(adminService.searchUsers).toHaveBeenCalledTimes(1);
    expect(lastSearch()[0]).toMatchObject({search: 'sparta'});
    expect(component.hasFilters()).toBe(true);
  });

  it('should filter by gym, role and status from the first page', () => {
    component.onGymChange('1');
    component.onRoleChange('COACH');
    component.onStatusChange('BANNED');

    expect(lastSearch()).toEqual([{search: undefined, gymId: 1, role: 'COACH', status: 'BANNED'}, 0, USER_PAGE_SIZE]);

    adminService.searchUsers.mockClear();
    component.onRoleChange('COACH');
    expect(adminService.searchUsers).not.toHaveBeenCalled();

    component.onGymChange('');
    expect(lastSearch()[0]).toMatchObject({gymId: undefined, role: 'COACH'});
  });

  it('should hide the recent targets once a filter is active', () => {
    component.onRoleChange('ATHLETE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('ADMIN.SUPPORT.RECENT');
  });

  it('should append the next page and stop at the last one', () => {
    adminService.searchUsers.mockReturnValue(of(slice([coach], true)));

    component.loadMore();

    expect(lastSearch()[1]).toBe(1);
    expect(component.users()).toEqual([athlete, admin, coach]);
    expect(component.hasMore()).toBe(false);

    adminService.searchUsers.mockClear();
    component.loadMore();
    expect(adminService.searchUsers).not.toHaveBeenCalled();
  });

  it('should highlight the selected user and never select an admin', () => {
    component.selectUser(admin);
    expect(component.selectedUser()).toBeNull();

    component.selectUser(athlete);
    fixture.detectChanges();

    expect(component.selectedUser()).toBe(athlete);
    const selected = fixture.nativeElement.querySelector('[data-slot="support-user"][aria-pressed="true"]');
    expect(selected.classList).toContain('ring-2');
    expect(selected.textContent).toContain('athlete@sparta.com');
  });

  it('should close the start panel by clicking the user again, the close button or Escape', () => {
    component.selectUser(athlete);
    component.reasonForm.setValue({reason: 'Draft'});
    component.selectUser(athlete);
    expect(component.selectedUser()).toBeNull();
    expect(component.reasonForm.getRawValue().reason).toBe('');

    component.selectUser(athlete);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[aria-label="GLOBAL.CLOSE"]') as HTMLButtonElement).click();
    expect(component.selectedUser()).toBeNull();

    component.selectUser(athlete);
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}));
    expect(component.selectedUser()).toBeNull();
  });

  it('should switch the panel to another user', () => {
    component.selectUser(athlete);
    component.selectUser(coach);
    expect(component.selectedUser()).toBe(coach);
  });

  it('should require a reason before starting', () => {
    component.selectUser(athlete);
    component.startSession();
    expect(supportSessionService.start).not.toHaveBeenCalled();
  });

  it('should start a session with the trimmed reason', () => {
    component.selectUser(athlete);
    component.reasonForm.setValue({reason: '  Bug on WOD list  '});

    component.startSession();

    expect(supportSessionService.start).toHaveBeenCalledWith(4, 'Bug on WOD list');
    expect(notificationService.showSuccess).toHaveBeenCalledWith('ADMIN.SUPPORT.STARTED');
  });

  it('should display the backend error when the session cannot start', () => {
    supportSessionService.start.mockReturnValue(throwError(() => ({status: 409, error: {detail: 'Already active'}})));
    component.selectUser(athlete);
    component.reasonForm.setValue({reason: 'Bug'});

    component.startSession();

    expect(notificationService.showError).toHaveBeenCalledWith('Already active');
    expect(component.isStarting()).toBe(false);
  });
});
