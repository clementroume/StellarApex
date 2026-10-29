import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {SessionSettingsCardComponent} from './session-settings-card.component';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {NotificationService} from '../../../core/services/notification.service';

describe('SessionSettingsCardComponent', () => {
  let fixture: ComponentFixture<SessionSettingsCardComponent>;
  const sessionService = {
    getSettings: vi.fn().mockReturnValue(of({defaultVisibility: 'AFTER_PARTICIPANTS', timeZone: 'Europe/Paris'})),
    updateSettings: vi.fn().mockReturnValue(of({defaultVisibility: 'PARTICIPANTS_ONLY', timeZone: 'Europe/Paris'}))
  };
  const notifications = {showSuccess: vi.fn(), showError: vi.fn()};

  const init = (programming = false) => {
    fixture = TestBed.createComponent(SessionSettingsCardComponent);
    fixture.componentRef.setInput('gymId', 4);
    fixture.componentRef.setInput('programming', programming);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionSettingsCardComponent],
      providers: [provideTranslateService(),
        {provide: SessionService, useValue: sessionService},
        {provide: NotificationService, useValue: notifications}]
    }).compileComponents();
  });

  it('should load the settings of the gym and save a new default policy', () => {
    init();
    expect(sessionService.getSettings).toHaveBeenCalledWith(4);
    const component = fixture.componentInstance;
    expect(component.form.getRawValue()).toEqual({defaultVisibility: 'AFTER_PARTICIPANTS', timeZone: 'Europe/Paris'});

    component.form.controls.defaultVisibility.setValue('PARTICIPANTS_ONLY');
    component.form.markAsDirty();
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    expect(sessionService.updateSettings).toHaveBeenCalledWith(4, {defaultVisibility: 'PARTICIPANTS_ONLY', timeZone: 'Europe/Paris'});
    expect(notifications.showSuccess).toHaveBeenCalledWith('SESSION.SETTINGS.SAVED');
  });

  it('should offer a program only the policies in advance and on the day', () => {
    init(true);
    const options = Array.from(fixture.nativeElement.querySelectorAll('option') as NodeListOf<HTMLOptionElement>)
      .filter(o => o.value === 'ADVANCE' || o.value === 'DAY_OF' || o.value === 'AFTER');
    expect(options.map(o => o.value)).toEqual(['ADVANCE', 'DAY_OF']);
  });
});
