import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {RecordService} from './record.service';
import {environment} from '../../../../environments/environment';
import {AccessService} from '../../../core/services/access.service';

describe('RecordService', () => {
  let service: RecordService;
  let httpMock: HttpTestingController;
  const base = `${environment.trainingUrl}/records`;
  const userId = signal<number | null>(1);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr()), provideHttpClientTesting(),
        {provide: AccessService, useValue: {userId}}
      ]
    });
    service = TestBed.inject(RecordService);
    httpMock = TestBed.inject(HttpTestingController);
    userId.set(1);
  });

  afterEach(() => httpMock.verify());

  it('should cache my records until invalidated or another user signs in', () => {
    service.getMyRecords().subscribe();
    httpMock.expectOne(`${base}/me`).flush([]);
    service.getMyRecords().subscribe();
    httpMock.expectNone(`${base}/me`);

    service.invalidate();
    expect(service.version()).toBe(1);
    service.getMyRecords().subscribe();
    httpMock.expectOne(`${base}/me`).flush([]);

    userId.set(2);
    service.getMyRecords().subscribe();
    httpMock.expectOne(`${base}/me`).flush([]);
  });

  it('should not keep a failed load', () => {
    service.getMyRecords().subscribe({error: () => undefined});
    httpMock.expectOne(`${base}/me`).flush('down', {status: 503, statusText: 'Unavailable'});
    service.getMyRecords().subscribe();
    httpMock.expectOne(`${base}/me`).flush([]);
  });

  it('should ask the records of one movement, the lifts and a box board', () => {
    service.getMyMovementRecords(4).subscribe();
    httpMock.expectOne(`${base}/me?movementId=4`).flush([]);
    service.getCanonicalLifts().subscribe();
    httpMock.expectOne(`${base}/lifts`).flush([]);
    service.getGymBoard(12, 4).subscribe();
    httpMock.expectOne(`${base}/gyms/12?movementId=4&variantKey=`).flush([]);

    // A raw `;` would be dropped by the proxy
    service.getGymBoard(12, 4, undefined, ';BACK_RACK').subscribe();
    httpMock.expectOne(`${base}/gyms/12?movementId=4&variantKey=%3BBACK_RACK`).flush([]);
    service.getMyProgress(4, 1, undefined, ';ZERCHER').subscribe();
    httpMock.expectOne(`${base}/me/progress?movementId=4&reps=1&variantKey=%3BZERCHER`).flush([]);
  });
});
