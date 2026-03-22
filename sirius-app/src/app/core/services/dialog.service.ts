import {Injectable, signal} from '@angular/core';
import {MuscleResponse} from '../../api/aldebaran/models/muscle.model';
import {MovementResponse} from '../../api/aldebaran/models/movement.model';

@Injectable({providedIn: 'root'})
export class DialogService {
  muscleToView = signal<MuscleResponse | null>(null);
  movementToView = signal<MovementResponse | null>(null);

  openMuscle(muscle: MuscleResponse) {
    this.muscleToView.set(muscle);
  }

  closeMuscle() {
    this.muscleToView.set(null);
  }

  openMovement(movement: MovementResponse) {
    this.movementToView.set(movement);
  }

  closeMovement() {
    this.movementToView.set(null);
  }

  closeAll() {
    this.muscleToView.set(null);
    this.movementToView.set(null);
  }
}
