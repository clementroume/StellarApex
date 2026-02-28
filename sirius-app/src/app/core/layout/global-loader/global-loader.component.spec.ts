import {ComponentFixture, TestBed} from '@angular/core/testing';
import {GlobalLoaderComponent} from './global-loader.component';
import {LoadingService} from '../../services/loading.service';
import {signal, WritableSignal} from '@angular/core';

describe('GlobalLoaderComponent', () => {
  let component: GlobalLoaderComponent;
  let fixture: ComponentFixture<GlobalLoaderComponent>;
  let mockIsLoading: WritableSignal<boolean>;

  beforeEach(async () => {
    // Create a writable signal to control in tests
    mockIsLoading = signal(false);

    await TestBed.configureTestingModule({
      imports: [GlobalLoaderComponent],
      providers: [
        // Provide a mock LoadingService that uses our controllable signal
        {
          provide: LoadingService,
          useValue: {isLoading: mockIsLoading.asReadonly()}
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GlobalLoaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not display the loader when isLoading is false', () => {
    mockIsLoading.set(false);
    fixture.detectChanges();
    const loaderElement = fixture.nativeElement.querySelector('.loading-spinner');
    expect(loaderElement).toBeNull();
  });

  it('should display the loader when isLoading is true', () => {
    mockIsLoading.set(true);
    fixture.detectChanges();
    const loaderElement = fixture.nativeElement.querySelector('.loading-spinner');
    expect(loaderElement).not.toBeNull();
  });
});
