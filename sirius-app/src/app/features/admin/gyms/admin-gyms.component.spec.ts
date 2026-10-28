import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {ADMIN_GYM_PAGE_SIZE, ADMIN_GYM_SEARCH_DEBOUNCE_MS, AdminGymsComponent} from './admin-gyms.component';
import {AdminService} from '../../../api/antares/services/admin.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AdminGymResponse} from '../../../api/antares/models/gym.model';
import {APP_ICONS} from '../../../app.config';

describe('AdminGymsComponent', () => {
  let fixture: ComponentFixture<AdminGymsComponent>;
  let component: AdminGymsComponent;
  let adminService: any;
  let gymService: any;

  const gym = (id: number, status: AdminGymResponse['status']): AdminGymResponse => ({
    id, name: `Gym ${id}`, isProgramming: false, status, createdAt: '2026-10-01T10:00:00', memberCount: 2,
    holder: {id: 7, firstName: 'Wait', lastName: 'User', email: 'wait@box.com', platformRole: 'USER'}
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    adminService = {searchGyms: vi.fn().mockReturnValue(of({content: [gym(1, 'PENDING_APPROVAL')], last: true}))};
    gymService = {updateStatus: vi.fn().mockReturnValue(of({}))};
    await TestBed.configureTestingModule({
      imports: [AdminGymsComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AdminService, useValue: adminService},
        {provide: GymService, useValue: gymService},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AdminGymsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('should open on the approval queue with the holder', () => {
    expect(adminService.searchGyms).toHaveBeenCalledWith('', 'PENDING_APPROVAL', 0, ADMIN_GYM_PAGE_SIZE);
    const row = fixture.nativeElement.querySelector('[data-slot="admin-gym"]');
    expect(row.textContent).toContain('wait@box.com');
    expect(row.textContent).toContain('ADMIN.GYMS.APPROVE');
  });

  it('should approve at once and drop the gym from the queue', () => {
    component.setStatus(component.gyms()[0], 'ACTIVE');
    expect(gymService.updateStatus).toHaveBeenCalledWith(1, 'ACTIVE');
    expect(component.gyms()).toEqual([]);
  });

  it('should ask before rejecting', () => {
    component.setStatus(component.gyms()[0], 'REJECTED');
    expect(gymService.updateStatus).not.toHaveBeenCalled();
    component.confirmAction();
    expect(gymService.updateStatus).toHaveBeenCalledWith(1, 'REJECTED');
  });

  it('should search by name and change the status filter', () => {
    component.onSearchChange('spar');
    vi.advanceTimersByTime(ADMIN_GYM_SEARCH_DEBOUNCE_MS);
    expect(adminService.searchGyms).toHaveBeenLastCalledWith('spar', 'PENDING_APPROVAL', 0, ADMIN_GYM_PAGE_SIZE);
    component.onStatusFilter(null);
    expect(adminService.searchGyms).toHaveBeenLastCalledWith('spar', null, 0, ADMIN_GYM_PAGE_SIZE);
  });
});
