import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationComponent } from './notification.component';
import { NotificationService, Notification } from '../../services/notification.service';
import { signal, WritableSignal } from '@angular/core';

describe('NotificationComponent', () => {
  let component: NotificationComponent;
  let fixture: ComponentFixture<NotificationComponent>;
  let mockNotification: WritableSignal<Notification | null>;

  beforeEach(async () => {
    mockNotification = signal<Notification | null>(null);

    await TestBed.configureTestingModule({
      imports: [NotificationComponent],
      providers: [
        {
          provide: NotificationService,
          useValue: { notification: mockNotification.asReadonly() }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not display an alert when notification is null', () => {
    mockNotification.set(null);
    fixture.detectChanges();
    const alertElement = fixture.nativeElement.querySelector('.alert');
    expect(alertElement).toBeNull();
  });

  it('should display a success alert for a success notification', () => {
    // Arrange
    mockNotification.set({ message: 'Success!', type: 'success' });
    fixture.detectChanges();

    // Act
    const alertElement = fixture.nativeElement.querySelector('.alert');

    // Assert
    expect(alertElement).not.toBeNull();
    expect(alertElement.classList).toContain('alert-success');
    expect(alertElement.textContent).toContain('Success!');
  });

  it('should display an error alert for an error notification', () => {
    // Arrange
    mockNotification.set({ message: 'Error!', type: 'error' });
    fixture.detectChanges();

    // Act
    const alertElement = fixture.nativeElement.querySelector('.alert');

    // Assert
    expect(alertElement).not.toBeNull();
    expect(alertElement.classList).toContain('alert-error');
    expect(alertElement.textContent).toContain('Error!');
  });
});
