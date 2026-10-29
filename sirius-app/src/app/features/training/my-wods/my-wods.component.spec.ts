import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MyWodsComponent} from './my-wods.component';
import {APP_ICONS} from '../../../app.config';
import {WodService} from '../../../api/aldebaran/services/wod.service';

describe('MyWodsComponent', () => {
  let fixture: ComponentFixture<MyWodsComponent>;
  const wodService = {getWods: vi.fn()};

  beforeEach(async () => {
    wodService.getWods.mockReturnValue(of({content: [
      {id: 3, title: 'Back Squat 5x5', wodType: 'STRENGTH', scoreType: 'WEIGHT', repScheme: '5x5', isPublic: false, competition: false, createdAt: ''}
    ], last: true}));
    await TestBed.configureTestingModule({
      imports: [MyWodsComponent],
      providers: [provideRouter([]), provideTranslateService(), provideIcons(APP_ICONS), {provide: WodService, useValue: wodService}]
    }).compileComponents();
    fixture = TestBed.createComponent(MyWodsComponent);
    fixture.detectChanges();
  });

  it('should list the personal WODs and offer the quick editor', () => {
    expect(wodService.getWods).toHaveBeenCalledWith({scope: 'MINE'}, 0, 20, expect.anything());
    expect(fixture.nativeElement.textContent).toContain('Back Squat 5x5');
    expect(fixture.nativeElement.querySelector('#new-wod').getAttribute('href')).toBe('/training/wods/new');
  });
});
