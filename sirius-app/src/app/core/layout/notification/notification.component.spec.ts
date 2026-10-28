import {ComponentFixture, DeferBlockState, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {NotificationComponent} from './notification.component';
import {ThemeService} from '../../services/theme.service';

describe('NotificationComponent', () => {
  let fixture: ComponentFixture<NotificationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationComponent],
      providers: [{provide: ThemeService, useValue: {currentTheme: signal('dark')}}]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationComponent);
    fixture.detectChanges();
  });

  it('should render the toaster once deferred content loads', async () => {
    const [block] = await fixture.getDeferBlocks();
    await block.render(DeferBlockState.Complete);
    expect(fixture.nativeElement.querySelector('hlm-toaster')).not.toBeNull();
  });
});
