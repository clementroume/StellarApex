import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component, input, output, signal} from '@angular/core';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {SessionFormComponent} from './session-form.component';
import {APP_ICONS} from '../../../app.config';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {SessionResponse} from '../../../api/aldebaran/models/session.model';
import {WodRequest} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {NotificationService} from '../../../core/services/notification.service';
import {WodQuickFormComponent} from '../../wods/wod-quick-form/wod-quick-form.component';

/** The quick WOD editor is tested apart. */
@Component({selector: 'app-wod-quick-form', standalone: true, template: ''})
class FakeWodQuickFormComponent {
  readonly defaultFormat = input('FOR_TIME');
  readonly initial = input<any>(null);
  readonly submitted = output<WodRequest>();
  readonly cancelled = output<void>();
}

describe('SessionFormComponent', () => {
  let fixture: ComponentFixture<SessionFormComponent>;
  let component: SessionFormComponent;
  const sessionService = {
    getSession: vi.fn().mockName('SessionService.getSession'),
    createSession: vi.fn().mockName('SessionService.createSession'),
    updateSession: vi.fn().mockName('SessionService.updateSession'),
    getSettings: vi.fn().mockName('SessionService.getSettings'),
    notifyRefresh: vi.fn()
  };
  const wodService = {getWods: vi.fn().mockReturnValue(of({content: [
    {id: 7, title: 'Fran', wodType: 'FOR_TIME', scoreType: 'TIME', isPublic: true, competition: false, createdAt: ''},
    {id: 8, title: '26.1 (box)', wodType: 'AMRAP', scoreType: 'REPS', isPublic: false, competition: true, createdAt: ''}
  ]}))};
  const notifications = {showSuccess: vi.fn(), showError: vi.fn()};
  const memberships = [{gymId: 4, gymName: 'Spartacus', gymProgramming: false, permissions: ['WOD_WRITE']},
    {gymId: 6, gymName: 'Zeus', gymProgramming: true, permissions: ['WOD_WRITE']}];
  const squat: WodRequest = {title: 'Back Squat 5x5', wodType: 'STRENGTH', scoreType: 'WEIGHT', isPublic: false, competition: false,
    movements: [{movementId: 3, orderIndex: 1, sets: 5, repsScheme: '5'}]};

  const init = (inputs: Record<string, unknown> = {}) => {
    fixture = TestBed.createComponent(SessionFormComponent);
    component = fixture.componentInstance;
    for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    sessionService.getSettings.mockReturnValue(of({defaultVisibility: 'AFTER_PARTICIPANTS', timeZone: 'Europe/Paris'}));
    await TestBed.configureTestingModule({
      imports: [SessionFormComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS),
        {provide: SessionService, useValue: sessionService},
        {provide: WodService, useValue: wodService},
        {provide: NotificationService, useValue: notifications},
        {provide: DisplayGymService, useValue: {gymId: signal(null)}},
        {provide: AccessService, useValue: {
          memberships: signal(memberships),
          gymsWithPermission: () => memberships
        }}]
    }).overrideComponent(SessionFormComponent, {
      remove: {imports: [WodQuickFormComponent]}, add: {imports: [FakeWodQuickFormComponent]}
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('should start a WOD session with a Skill and a WOD block for the managed box', () => {
    init();
    expect(component.form.controls.blocks.length).toBe(2);
    expect(component.form.controls.blocks.at(0).controls.kind.value).toBe('SKILL');
    expect(component.form.controls.blocks.at(1).controls.kind.value).toBe('WOD');
    expect(fixture.nativeElement.querySelectorAll('.session-block-editor').length).toBe(2);
    expect(fixture.nativeElement.querySelector('#session-visibility')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('SESSION.POLICIES.AFTER_PARTICIPANTS.LABEL');
    (fixture.nativeElement.querySelector('#edit-visibility') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#session-visibility')).toBeTruthy();
  });

  it('should write a WOD inline, borrow another, and send the session', () => {
    init();
    (component as any).setOwnWod(0, squat);
    (component as any).openBorrow(1);
    fixture.detectChanges();
    const results = fixture.nativeElement.querySelectorAll('[aria-labelledby="borrow-title"] li button');
    expect(results.length).toBe(1); // competition WODs are never borrowed as is
    (results[0] as HTMLButtonElement).click();
    (component as any).addBlock('WARM_UP');
    component.form.controls.blocks.at(0).patchValue({textEn: 'Hip mobility', themeEn: 'ignored'});
    component.form.patchValue({title: ' Tuesday ', visibilityPolicy: 'DAY_OF', contentVisibleAt: '2026-11-03T18:00'});

    const request = component.buildRequest();
    expect(request).toMatchObject({type: 'WOD', discipline: 'CROSSFIT', gymId: 4, title: 'Tuesday',
      visibilityPolicy: 'DAY_OF', contentVisibleAt: '2026-11-03T18:00'});
    expect(request.blocks.map(b => b.kind)).toEqual(['WARM_UP', 'SKILL', 'WOD']);
    expect(request.blocks[0]).toMatchObject({textEn: 'Hip mobility', themeEn: null, wod: null, wodId: null});
    expect(request.blocks[1]).toMatchObject({wod: squat, wodId: null, label: null, stimulus: null, plannedMinutes: null});
    expect(request.blocks[2]).toMatchObject({wodId: 7, wod: null});

    sessionService.createSession.mockReturnValue(of({id: 12, gymId: 4}));
    (component as any).onSubmit();
    expect(sessionService.createSession).toHaveBeenCalled();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/box/sessions', 12]);
  });

  it('should refuse to send a scored block without WOD', () => {
    init();
    (component as any).onSubmit();
    expect(sessionService.createSession).not.toHaveBeenCalled();
    expect(notifications.showError).toHaveBeenCalledWith('SESSION.MESSAGES.WOD_MISSING');
  });

  it('should create a personal specialty without gym nor visibility', () => {
    init({personal: true});
    component.form.patchValue({type: 'SPECIALTY'});
    fixture.detectChanges();
    expect(component.form.controls.discipline.value).toBe('WEIGHTLIFTING');
    expect(fixture.nativeElement.querySelector('#edit-visibility')).toBeNull();
    (component as any).setOwnWod(0, squat);
    (component as any).removeBlock(1);

    expect(component.buildRequest()).toMatchObject({type: 'SPECIALTY', discipline: 'WEIGHTLIFTING', gymId: null, visibilityPolicy: null});
  });

  it('should load a session, keep its own WOD unless rewritten, and show backend errors', () => {
    const existing = {
      id: 5, type: 'WOD', discipline: 'CROSSFIT', date: '2026-11-03', endDate: '2026-11-03', gymId: 4, authorId: 1,
      visibilityPolicy: 'AFTER', createdAt: '', updatedAt: '',
      blocks: [
        {id: 11, orderIndex: 1, kind: 'SKILL', optional: false, borrowed: false, competition: false,
          wod: {id: 30, title: 'Back Squat 5x5'}},
        {id: 12, orderIndex: 2, kind: 'WOD', optional: false, borrowed: true, competition: false, wod: {id: 7, title: 'Fran'}}
      ],
      capabilities: {canModify: true, locked: false, contentVisible: true, canLog: false}
    } as unknown as SessionResponse;
    sessionService.getSession.mockReturnValue(of(existing));
    init({id: '5'});

    const request = component.buildRequest();
    expect(request.visibilityPolicy).toBe('AFTER');
    expect(request.blocks[0]).toMatchObject({id: 11, wodId: 30, wod: null});
    expect(request.blocks[1]).toMatchObject({id: 12, wodId: 7});

    sessionService.updateSession.mockReturnValue(throwError(() => ({status: 409, error: {detail: 'Locked'}})));
    (component as any).onSubmit();
    expect(sessionService.updateSession).toHaveBeenCalledWith(5, expect.anything());
    expect(notifications.showError).toHaveBeenCalledWith('Locked');
  });

  it('should offer only in advance / on the day for a program, and competition blocks for a box only', () => {
    init();
    expect((component as any).canCompete()).toBe(true);
    (component as any).staffGym.select(6);
    fixture.detectChanges();
    expect((component as any).policies()).toEqual(['ADVANCE', 'DAY_OF']);
    expect((component as any).canCompete()).toBe(false);
  });

  it('should keep the warm-up first and the cool down last, protect Skill and WOD', () => {
    init();
    const c = component as any;
    c.addBlock('COOL_DOWN');
    c.addBlock('ACCESSORY');
    c.addBlock('WARM_UP');
    fixture.detectChanges();

    const kinds = () => component.form.controls.blocks.controls.map(b => b.controls.kind.value);
    expect(kinds()).toEqual(['WARM_UP', 'SKILL', 'WOD', 'ACCESSORY', 'COOL_DOWN']);
    expect(c.addableKinds()).toEqual(['SKILL', 'WOD', 'ACCESSORY', 'FINISHER']);
    expect(c.canMove(1, -1)).toBe(false); // never above the warm-up
    expect(c.canMove(3, 1)).toBe(false); // never below the cool down
    expect(c.canMove(0, 1)).toBe(false); // the warm-up stays first
    expect(c.canRemove(1)).toBe(false);
    expect(c.canRemove(2)).toBe(false);
    expect(c.canRemove(3)).toBe(true);
    expect(component.form.controls.blocks.at(3).controls.optional.value).toBe(true);

    c.addBlock('SKILL');
    expect(kinds()).toEqual(['WARM_UP', 'SKILL', 'WOD', 'ACCESSORY', 'SKILL', 'COOL_DOWN']);
    expect(c.canRemove(1)).toBe(true);
    expect(fixture.nativeElement.querySelector('#show-more')).toBeTruthy();
  });

  it('should borrow the WOD programmed from its page, as a competition block for the Open', () => {
    const route = TestBed.inject(ActivatedRoute);
    vi.spyOn(route.snapshot.queryParamMap, 'get').mockImplementation(key => key === 'borrow' ? '40' : null);
    (wodService as any).getWod = vi.fn().mockReturnValue(of({id: 40, title: '26.1', category: 'OPEN'}));
    init();

    const wodBlock = component.form.controls.blocks.at(1).getRawValue();
    expect(wodBlock.wod).toEqual({mode: 'borrowed', title: '26.1', wodId: 40});
    expect(wodBlock.competition).toBe(true);
  });
});
