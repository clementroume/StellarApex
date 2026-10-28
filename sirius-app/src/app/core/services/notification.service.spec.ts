import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {toast} from '@spartan-ng/brain/sonner';
import {NotificationService} from './notification.service';


describe('NotificationService', () => {
  let service: NotificationService;

  afterEach(() => vi.restoreAllMocks());

  beforeEach(() => {
    vi.spyOn(toast, 'success').mockImplementation(() => 0);
    vi.spyOn(toast, 'error').mockImplementation(() => 0);
    TestBed.configureTestingModule({providers: [NotificationService]});
    service = TestBed.inject(NotificationService);
  });

  it('should show a success toast', async () => {
    service.showSuccess('Saved');
    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith('Saved'));
  });

  it('should show an error toast', async () => {
    service.showError('Failed');
    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('Failed'));
  });
});
