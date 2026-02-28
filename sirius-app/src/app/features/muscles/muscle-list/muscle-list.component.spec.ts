import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MuscleListComponent} from './muscle-list.component';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {of} from 'rxjs';
import {signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';

describe('MuscleListComponent', () => {
  let component: MuscleListComponent;
  let fixture: ComponentFixture<MuscleListComponent>;
  let mockMuscleService: any;
  let mockAuthService: any;

  beforeEach(async () => {
    mockMuscleService = {
      getMuscles: jasmine.createSpy('getMuscles').and.returnValue(of([
        {
          id: 1,
          medicalName: 'Pectoralis',
          commonNameFr: 'Pectoraux',
          commonNameEn: 'Chest',
          descriptionFr: 'Desc FR',
          descriptionEn: 'Desc EN',
          muscleGroup: 'CHEST'
        }
      ]))
    };

    mockAuthService = {
      currentUser: signal({platformRole: 'USER'}) // User de base par défaut
    };

    await TestBed.configureTestingModule({
      imports: [MuscleListComponent,
        TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {provide: MuscleService, useValue: mockMuscleService},
        {provide: AuthService, useValue: mockAuthService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MuscleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('devrait créer le composant et charger les muscles', () => {
    expect(component).toBeTruthy();
    expect(mockMuscleService.getMuscles).toHaveBeenCalled();
    expect(component.sortedMuscles().length).toBe(1);
  });

  it('devrait cacher les boutons d\'édition pour un utilisateur standard', () => {
    mockAuthService.currentUser.set({platformRole: 'USER'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeFalse();
  });

  it('devrait afficher les boutons d\'édition pour un admin', () => {
    mockAuthService.currentUser.set({platformRole: 'ADMIN'});
    fixture.detectChanges();
    expect(component.isAdmin()).toBeTrue();
  });

  it('devrait trier les muscles correctement', () => {
    component.sortBy('medicalName');
    expect(component.sortColumn()).toBe('medicalName');
    expect(component.sortDirection()).toBe('asc');

    // Clic inverse
    component.sortBy('medicalName');
    expect(component.sortDirection()).toBe('desc');
  });

  it('devrait retourner le nom localisé correct', () => {
    const muscle = component.sortedMuscles()[0];

    component.activeLang.set('fr');
    expect(component.getLocalizedName(muscle)).toBe('Pectoraux');
    expect(component.getLocalizedDescription(muscle)).toBe('Desc FR');

    component.activeLang.set('en');
    expect(component.getLocalizedName(muscle)).toBe('Chest');
    expect(component.getLocalizedDescription(muscle)).toBe('Desc EN');
  });

  it('devrait trier correctement sur le nom dynamique', () => {
    component.sortBy('commonName');
    expect(component.sortColumn()).toBe('commonName');
    expect(component.sortDirection()).toBe('asc');
  });
});
