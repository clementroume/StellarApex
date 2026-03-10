import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleModalComponent} from './muscle-modal.component';
import {DialogService} from '../../../core/services/dialog.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';

describe('MuscleModalComponent', () => {
  let component: MuscleModalComponent;
  let fixture: ComponentFixture<MuscleModalComponent>;
  let mockDialogService: any;
  let mockAuthService: any;

  beforeEach(async () => {
    mockDialogService = {
      muscleToView: signal(null),
      closeMuscle: jasmine.createSpy('closeMuscle')
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'ADMIN'})
    };

    await TestBed.configureTestingModule({
      imports: [MuscleModalComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {provide: DialogService, useValue: mockDialogService},
        {provide: AuthService, useValue: mockAuthService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call dialogService.closeMuscle on onClose', () => {
    component.onClose();
    expect(mockDialogService.closeMuscle).toHaveBeenCalled();
  });

  it('should format localized name and description properly', () => {
    const mockMuscle: any = {
      medicalName: 'Pectoralis',
      commonNameFr: 'Pectoraux',
      commonNameEn: 'Chest',
      descriptionFr: 'Desc FR',
      descriptionEn: 'Desc EN'
    };

    component.activeLang.set('fr');
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc FR');

    component.activeLang.set('en');
    expect(component.getLocalizedName(mockMuscle)).toBe('Chest');
    expect(component.getLocalizedDescription(mockMuscle)).toBe('Desc EN');
  });

  it('should fallback to medical name if common name is missing', () => {
    const mockMuscle: any = {medicalName: 'Pectoralis'};
    expect(component.getLocalizedName(mockMuscle)).toBe('Pectoralis');
  });
});
