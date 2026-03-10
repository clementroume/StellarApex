import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MovementModalComponent} from './movement-modal.component';
import {DialogService} from '../../../core/services/dialog.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {provideIcons} from '@ng-icons/core';

describe('MovementModalComponent', () => {
  let component: MovementModalComponent;
  let fixture: ComponentFixture<MovementModalComponent>;
  let mockDialogService: any;
  let mockAuthService: any;
  let mockMuscleService: any;

  beforeEach(async () => {
    mockDialogService = {
      movementToView: signal(null),
      closeMovement: jasmine.createSpy('closeMovement'),
      openMuscle: jasmine.createSpy('openMuscle')
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    mockMuscleService = {
      getMuscle: jasmine.createSpy('getMuscle').and.returnValue(of({id: 1, medicalName: 'Pectoralis'}))
    };

    await TestBed.configureTestingModule({
      imports: [MovementModalComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: DialogService, useValue: mockDialogService},
        {provide: AuthService, useValue: mockAuthService},
        {provide: MuscleService, useValue: mockMuscleService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MovementModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format localized description and cues properly', () => {
    const mockMovement: any = {
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN',
      coachingCuesFr: 'Cues FR',
      coachingCuesEn: 'Cues EN'
    };

    component.activeLang.set('fr');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc FR');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues FR');

    component.activeLang.set('en');
    expect(component.getLocalizedDescription(mockMovement)).toBe('Desc EN');
    expect(component.getLocalizedCues(mockMovement)).toBe('Cues EN');
  });

  it('should call dialogService.closeMovement on onClose', () => {
    component.onClose();
    expect(mockDialogService.closeMovement).toHaveBeenCalled();
  });

  it('should fetch muscle and call dialogService.openMuscle on openMuscle', () => {
    const mockEvent = {
      preventDefault: jasmine.createSpy('preventDefault'),
      stopPropagation: jasmine.createSpy('stopPropagation')
    } as unknown as Event;

    component.openMuscle(1, mockEvent);

    expect(mockEvent.preventDefault).toHaveBeenCalled();
    expect(mockEvent.stopPropagation).toHaveBeenCalled();
    expect(mockMuscleService.getMuscle).toHaveBeenCalledWith(1);
    expect(mockDialogService.openMuscle).toHaveBeenCalledWith({id: 1, medicalName: 'Pectoralis'});
  });
});
