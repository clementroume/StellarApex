import {ComponentFixture, TestBed} from '@angular/core/testing';
import {computed, signal, WritableSignal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {SupportBannerComponent} from './support-banner.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {SupportSessionService} from '../../services/support-session.service';
import {APP_ICONS} from '../../../app.config';

describe('SupportBannerComponent', () => {
  let fixture: ComponentFixture<SupportBannerComponent>;
  let session: WritableSignal<any>;
  let supportSessionService: any;

  const NOW = new Date('2026-10-25T12:00:00Z').getTime();

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    session = signal(null);
    supportSessionService = {
      session,
      isActive: computed(() => session() !== null),
      end: vi.fn().mockReturnValue(of(undefined)),
      handleExpired: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [SupportBannerComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser: signal({firstName: 'Ath', lastName: 'Lete'})}},
        {provide: SupportSessionService, useValue: supportSessionService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SupportBannerComponent);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should stay hidden outside a support session', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('should show the target and the remaining time', () => {
    session.set({id: 1, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: new Date(NOW + 90_000).toISOString()});
    fixture.detectChanges();

    expect(fixture.componentInstance.targetName()).toBe('Ath Lete');
    expect(fixture.componentInstance.remaining()).toBe('1:30');
    expect(fixture.nativeElement.textContent).toContain('ADMIN.SUPPORT.BANNER');
  });

  it('should hand back to the admin when the countdown reaches zero', () => {
    session.set({id: 1, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: new Date(NOW + 2_000).toISOString()});
    fixture.detectChanges();
    expect(supportSessionService.handleExpired).not.toHaveBeenCalled();

    vi.advanceTimersByTime(2_000);
    fixture.detectChanges();

    expect(supportSessionService.handleExpired).toHaveBeenCalled();
  });

  it('should end the session on demand', () => {
    session.set({id: 1, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: new Date(NOW + 60_000).toISOString()});
    fixture.detectChanges();

    fixture.componentInstance.end();

    expect(supportSessionService.end).toHaveBeenCalled();
    expect(fixture.componentInstance.isEnding()).toBe(false);
  });
});
