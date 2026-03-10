import {TestBed} from '@angular/core/testing';
import {DialogService} from './dialog.service';

describe('DialogService', () => {
  let service: DialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should manage muscle modal state', () => {
    const mockMuscle: any = {id: 1, medicalName: 'Pectoralis'};

    service.openMuscle(mockMuscle);
    expect(service.muscleToView()).toEqual(mockMuscle);

    service.closeMuscle();
    expect(service.muscleToView()).toBeNull();
  });

  it('should manage movement modal state', () => {
    const mockMovement: any = {id: 1, name: 'Squat'};

    service.openMovement(mockMovement);
    expect(service.movementToView()).toEqual(mockMovement);

    service.closeMovement();
    expect(service.movementToView()).toBeNull();
  });
});
