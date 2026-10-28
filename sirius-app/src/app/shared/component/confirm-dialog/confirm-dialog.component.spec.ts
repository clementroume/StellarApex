import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {ConfirmDialogComponent} from './confirm-dialog.component';
import {APP_ICONS} from '../../../app.config';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [provideIcons(APP_ICONS), provideTranslateService()]
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Leave Box?');
  });

  afterEach(() => document.body.querySelectorAll('.cdk-overlay-container').forEach(e => e.remove()));

  it('should render nothing while closed', () => {
    fixture.detectChanges();
    expect(document.body.textContent).not.toContain('Leave Box?');
  });

  it('should show the title and emit confirmed on the action', async () => {
    const confirmed = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);
    fixture.componentRef.setInput('description', 'History is kept.');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.body.textContent).toContain('Leave Box?');
    expect(document.body.textContent).toContain('History is kept.');
    const buttons = Array.from(document.body.querySelectorAll('button'));
    buttons.find(b => b.textContent?.includes('GLOBAL.CONFIRM'))!.click();
    expect(confirmed).toHaveBeenCalled();
  });
});
