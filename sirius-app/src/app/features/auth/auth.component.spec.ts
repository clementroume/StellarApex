import {ComponentFixture, TestBed} from '@angular/core/testing';
import {AuthComponent} from './auth.component';
import {provideRouter} from '@angular/router';

describe('AuthComponent', () => {
  let component: AuthComponent;
  let fixture: ComponentFixture<AuthComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthComponent],
      providers: [
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should contain a <router-outlet> tag to display child pages', () => {
    const routerOutlet = fixture.nativeElement.querySelector('router-outlet');
    expect(routerOutlet).not.toBeNull();
  });
});
