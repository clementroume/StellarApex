import {TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {MuscleCreditComponent} from './muscle-credit.component';
import {LICENSE_URL} from '../muscle-model/muscle-model';

describe('MuscleCreditComponent', () => {
  it('credits the sources and links to the licence file', () => {
    TestBed.configureTestingModule({imports: [MuscleCreditComponent], providers: [provideTranslateService()]});
    const fixture = TestBed.createComponent(MuscleCreditComponent);
    fixture.detectChanges();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    expect(link.getAttribute('href')).toBe(LICENSE_URL);
    expect(link.textContent).toContain('Z-Anatomy');
    expect(link.textContent).toContain('BodyParts3D');
    expect(link.textContent).toContain('CC BY-SA 4.0');
  });
});
