import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DetailAdminActionsComponent} from './detail-admin-actions.component';
import {provideRouter} from '@angular/router';
import {provideIcons} from '@ng-icons/core';
import {vi} from 'vitest';
import {APP_ICONS} from '../../../../app.config';
import {provideTranslateService} from '@ngx-translate/core';

describe('DetailAdminActionsComponent', () => {
  let component: DetailAdminActionsComponent;
  let fixture: ComponentFixture<DetailAdminActionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        DetailAdminActionsComponent
      ],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DetailAdminActionsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('editLink', ['/edit', '123']);
    fixture.detectChanges();
  });

  it('should have a valid edit link', () => {
    const editLinkEl = fixture.nativeElement.querySelector('a');
    expect(editLinkEl.getAttribute('href')).toBe('/edit/123');
  });

  it('should emit delete event on button click', () => {
    vi.spyOn(component.delete, 'emit');
    const deleteBtn = fixture.nativeElement.querySelector('button.btn-error');
    deleteBtn.click();
    expect(component.delete.emit).toHaveBeenCalled();
  });
});
