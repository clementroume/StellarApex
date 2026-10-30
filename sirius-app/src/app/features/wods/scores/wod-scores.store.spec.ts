import type {MockedObject} from 'vitest';
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {WodScoresStore} from './wod-scores.store';
import {ScoreService} from '../../../api/aldebaran/services/score.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ScoreRequest, ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';

describe('WodScoresStore', () => {
  let store: WodScoresStore;
  let scoreService: MockedObject<ScoreService>;
  let notifications: MockedObject<NotificationService>;

  const wod = {id: 7, scoreType: 'TIME'} as WodResponse;
  const score = (id: number, personalRecord = false): ScoreResponse => ({
    id, userId: 1, date: '2026-10-26', wodSummary: {} as any, scaling: 'RX', timeCapped: false,
    personalRecord, loggedAt: '', capabilities: {canModify: true, canVerify: false}
  });
  const slice = (content: ScoreResponse[]) => of({content} as any);
  const request = {wodId: 7, date: '2026-10-26', scaling: 'RX', timeCapped: false} as ScoreRequest;

  beforeEach(() => {
    scoreService = {
      getMyScores: vi.fn().mockName('ScoreService.getMyScores'),
      compareScore: vi.fn().mockName('ScoreService.compareScore'),
      logScore: vi.fn().mockName('ScoreService.logScore'),
      updateScore: vi.fn().mockName('ScoreService.updateScore'),
      deleteScore: vi.fn().mockName('ScoreService.deleteScore'),
      verifyScore: vi.fn().mockName('ScoreService.verifyScore'),
      unverifyScore: vi.fn().mockName('ScoreService.unverifyScore')
    } as any;
    notifications = {
      showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
      showError: vi.fn().mockName('NotificationService.showError')
    } as any;
    scoreService.getMyScores.mockReturnValue(slice([score(1), score(2, true)]));
    scoreService.compareScore.mockReturnValue(of({rank: 3, totalScores: 10, percentile: 77}));

    TestBed.configureTestingModule({
      providers: [
        WodScoresStore,
        provideTranslateService(),
        {provide: ScoreService, useValue: scoreService},
        {provide: NotificationService, useValue: notifications}
      ]
    });
    store = TestBed.inject(WodScoresStore);
  });

  afterEach(() => vi.restoreAllMocks());

  it('should load the history, take the PR flagged by Aldebaran and rank it', () => {
    store.load(wod);

    expect(scoreService.getMyScores).toHaveBeenCalledWith(7, 0, 50, undefined, null);
    expect(store.best()?.id).toBe(2);
    expect(scoreService.compareScore).toHaveBeenCalledWith(2);
    expect(store.comparison()?.rank).toBe(3);
  });

  it('should not rank a WOD without measured result, nor a user without score', () => {
    store.load({...wod, scoreType: 'NONE'});
    expect(scoreService.compareScore).not.toHaveBeenCalled();

    scoreService.getMyScores.mockReturnValue(slice([]));
    store.load(wod);
    expect(store.best()).toBeNull();
    expect(scoreService.compareScore).not.toHaveBeenCalled();
  });

  it('should log a new score, then close the form, reload and signal the change', () => {
    const changed = vi.fn();
    store.changed$.subscribe(changed);
    store.load(wod);
    scoreService.logScore.mockReturnValue(of(score(3)));

    store.openNew();
    store.save(request);

    expect(scoreService.logScore).toHaveBeenCalledWith(request);
    expect(store.editing()).toBeNull();
    expect(scoreService.getMyScores).toHaveBeenCalledTimes(2);
    expect(changed).toHaveBeenCalled();
    expect(notifications.showSuccess).toHaveBeenCalledWith('SCORE.MESSAGES.SAVED');
  });

  it('should link a new score to the session journal of the block', () => {
    store.load(wod);
    store.sessionLogId.set(12);
    scoreService.logScore.mockReturnValue(of(score(3)));

    store.openNew();
    store.save(request);

    expect(scoreService.logScore).toHaveBeenCalledWith({...request, sessionLogId: 12});
  });

  it('should update the score being edited', () => {
    store.load(wod);
    scoreService.updateScore.mockReturnValue(of(score(1)));

    store.openEdit(score(1));
    store.save(request);

    expect(scoreService.updateScore).toHaveBeenCalledWith(1, request);
    expect(scoreService.logScore).not.toHaveBeenCalled();
  });

  it('should keep the form open and show the backend message on error', () => {
    store.load(wod);
    scoreService.logScore.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: {detail: 'Enter the reps completed at the time cap.'}
    })));

    store.openNew();
    store.save(request);

    expect(store.editing()).toBe('new');
    expect(store.isSaving()).toBe(false);
    expect(notifications.showError).toHaveBeenCalledWith('Enter the reps completed at the time cap.');
  });

  it('should prefer the field message of a validation error, and a translated 403', () => {
    store.load(wod);
    store.openNew();
    scoreService.logScore.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: {detail: 'Validation failed', errors: [{field: 'date', message: 'Date in the future.'}]}
    })));
    store.save(request);
    expect(notifications.showError).toHaveBeenLastCalledWith('Date in the future.');

    scoreService.logScore.mockReturnValue(throwError(() => new HttpErrorResponse({status: 403})));
    store.save(request);
    expect(notifications.showError).toHaveBeenLastCalledWith('SCORE.MESSAGES.FORBIDDEN');
  });

  it('should delete a score after confirmation only', () => {
    store.load(wod);
    scoreService.deleteScore.mockReturnValue(of(undefined));
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    store.delete(score(1));
    expect(scoreService.deleteScore).not.toHaveBeenCalled();

    confirmSpy.mockReturnValue(true);
    store.delete(score(1));
    expect(scoreService.deleteScore).toHaveBeenCalledWith(1);
    expect(scoreService.getMyScores).toHaveBeenCalledTimes(2);
  });

  it('should verify a score or withdraw its verification, then reload', () => {
    store.load(wod);
    scoreService.verifyScore.mockReturnValue(of(score(1)));
    scoreService.unverifyScore.mockReturnValue(of(score(1)));
    let changes = 0;
    store.changed$.subscribe(() => changes++);

    store.setVerified(score(1), true);
    store.setVerified(score(1), false);

    expect(scoreService.verifyScore).toHaveBeenCalledWith(1);
    expect(scoreService.unverifyScore).toHaveBeenCalledWith(1);
    expect(notifications.showSuccess).toHaveBeenCalledTimes(2);
    expect(changes).toBe(2);
  });

  it('should explain a refused verification', () => {
    store.load(wod);
    scoreService.verifyScore.mockReturnValue(throwError(() => new HttpErrorResponse({status: 403})));

    store.setVerified(score(1), true);

    expect(notifications.showError).toHaveBeenCalledWith('SCORE.MESSAGES.FORBIDDEN');
  });
});
