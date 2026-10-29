import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Component, input, output, signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MyWodFormComponent} from './my-wod-form.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {WodQuickFormComponent} from '../../wods/wod-quick-form/wod-quick-form.component';
import {WodRequest} from '../../../api/aldebaran/models/wod.model';

@Component({selector: 'app-wod-quick-form', standalone: true, template: ''})
class FakeQuickFormComponent {
  readonly initial = input<any>(null);
  readonly submitted = output<WodRequest>();
  readonly cancelled = output<void>();
}

describe('MyWodFormComponent', () => {
  let fixture: ComponentFixture<MyWodFormComponent>;
  const wodService = {getWod: vi.fn(), createWod: vi.fn(), updateWod: vi.fn(), notifyRefresh: vi.fn()};
  const request = {title: 'Cindy', wodType: 'AMRAP', scoreType: 'REPS', isPublic: false, competition: false, movements: []} as WodRequest;

  const init = (id?: string) => {
    fixture = TestBed.createComponent(MyWodFormComponent);
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MyWodFormComponent],
      providers: [provideRouter([]), provideTranslateService(),
        {provide: WodService, useValue: wodService},
        {provide: AccessService, useValue: {userId: signal(2)}},
        {provide: NotificationService, useValue: {showSuccess: vi.fn(), showError: vi.fn()}}]
    }).overrideComponent(MyWodFormComponent, {
      remove: {imports: [WodQuickFormComponent]}, add: {imports: [FakeQuickFormComponent]}
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('should save a new personal WOD of the user and open it', () => {
    wodService.createWod.mockReturnValue(of({id: 9}));
    init();
    (fixture.componentInstance as any).save(request);
    expect(wodService.createWod).toHaveBeenCalledWith({...request, gymId: undefined, authorId: 2});
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/wods', 9]);
  });

  it('should update an editable WOD and send a locked one back to its page', () => {
    wodService.getWod.mockReturnValue(of({id: 4, capabilities: {canModify: true, locked: false}}));
    wodService.updateWod.mockReturnValue(of({id: 4}));
    init('4');
    (fixture.componentInstance as any).save(request);
    expect(wodService.updateWod).toHaveBeenCalledWith(4, expect.objectContaining({authorId: 2}));

    wodService.getWod.mockReturnValue(of({id: 5, capabilities: {canModify: true, locked: true}}));
    init('5');
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/wods', 5]);
  });
});
