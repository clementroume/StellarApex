import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotificationService]
    });
    service = TestBed.inject(NotificationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have a null notification initially', () => {
    expect(service.notification()).toBeNull();
  });

  it('should set a success notification correctly', () => {
    // Act
    service.showSuccess('Success!');
    const notification = service.notification();

    // Assert
    expect(notification).not.toBeNull();
    expect(notification?.message).toBe('Success!');
    expect(notification?.type).toBe('success');
  });

  it('should set an error notification correctly', () => {
    // Act
    service.showError('Error!');
    const notification = service.notification();

    // Assert
    expect(notification).not.toBeNull();
    expect(notification?.message).toBe('Error!');
    expect(notification?.type).toBe('error');
  });

  it('should clear the notification automatically after 5 seconds', fakeAsync(() => {
    // Act
    service.showSuccess('A temporary message');

    // Assert initial state
    expect(service.notification()).not.toBeNull();

    // Advance the clock by 5000 milliseconds
    tick(5000);

    // Assert final state
    expect(service.notification()).toBeNull();
  }));
});
