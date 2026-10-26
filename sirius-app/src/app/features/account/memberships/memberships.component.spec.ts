import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {MembershipsComponent} from './memberships.component';
import {AuthService} from '../../../api/antares/services/auth.service';
import {APP_ICONS} from '../../../app.config';

describe('MembershipsComponent', () => {
  let fixture: ComponentFixture<MembershipsComponent>;
  let currentUser: WritableSignal<any>;

  beforeEach(async () => {
    currentUser = signal({id: 1, memberships: []});

    await TestBed.configureTestingModule({
      imports: [MembershipsComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MembershipsComponent);
  });

  it('should say when the user has no gym', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('PROFILE.GYMS.NONE');
  });

  it('should show role, status and each permission with its description', () => {
    currentUser.set({
      id: 1, memberships: [
        {gymId: 1, gymName: 'Spartacus CrossFit', gymStatus: 'ACTIVE', gymRole: 'COACH', status: 'ACTIVE', permissions: ['WOD_WRITE', 'SCORE_VERIFY']},
        {gymId: 2, gymName: 'Pending Box', gymStatus: 'PENDING_APPROVAL', gymRole: 'ATHLETE', status: 'BANNED', permissions: []}
      ]
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Spartacus CrossFit');
    expect(text).toContain('GYM.ROLES.COACH');
    expect(text).toContain('GYM.PERMISSIONS.WOD_WRITE.LABEL');
    expect(text).toContain('GYM.PERMISSIONS.SCORE_VERIFY.DESCRIPTION');
    expect(text).toContain('GYM.STATUS.BANNED');
    expect(text).toContain('GYM.GYM_STATUS.PENDING_APPROVAL');
    expect(text).toContain('PROFILE.GYMS.NO_PERMISSION');
  });
});
