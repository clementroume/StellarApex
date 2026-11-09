import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {BodyMapComponent} from './body-map.component';

describe('BodyMapComponent', () => {
  let fixture: ComponentFixture<BodyMapComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [BodyMapComponent], providers: [provideTranslateService()]}).compileComponents();
    fixture = TestBed.createComponent(BodyMapComponent);
    fixture.componentRef.setInput('selected', 'CHEST');
    fixture.detectChanges();
  });

  const paths = (selector: string) => [...fixture.nativeElement.querySelectorAll(selector)] as SVGPathElement[];

  it('should draw the front and the back, with the region shown lit', () => {
    expect(fixture.nativeElement.querySelectorAll('svg').length).toBe(2);
    expect(paths('path.region.on').length).toBeGreaterThan(0);
    expect(paths('path.other').length).toBeGreaterThan(0);
  });

  it('should emit the region of a muscle, nothing for the rest of the body', () => {
    const picked = vi.fn();
    fixture.componentInstance.regionPicked.subscribe(picked);
    paths('path.other')[0].dispatchEvent(new MouseEvent('click'));
    expect(picked).not.toHaveBeenCalled();
    paths('path.region.on')[0].dispatchEvent(new MouseEvent('click'));
    expect(picked).toHaveBeenCalledWith('CHEST');
  });
});
