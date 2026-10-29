import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {HttpContext} from '@angular/common/http';
import {of, Subject, throwError} from 'rxjs';
import {vi} from 'vitest';
import {WOD_PAGE_SIZE, WOD_SEARCH_DEBOUNCE_MS, WodListComponent} from './wod-list.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {AccessService} from '../../../core/services/access.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {APP_ICONS} from '../../../app.config';
import {WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {Slice} from '../../../core/models/pagination.model';

describe('WodListComponent', () => {
  let component: WodListComponent;
  let fixture: ComponentFixture<WodListComponent>;
  let mockWodService: any;
  let isAdmin: WritableSignal<boolean>;
  let mockExporterService: any;

  const fran: WodSummaryResponse = {id: 1, title: 'Fran', wodType: 'FOR_TIME', scoreType: 'TIME', repScheme: '21-15-9', timeCapSeconds: 600, gymId: 5, isPublic: false, competition: false, createdAt: ''};
  const cindy: WodSummaryResponse = {id: 2, title: 'Cindy', wodType: 'AMRAP', scoreType: 'ROUNDS_REPS', isPublic: true, competition: false, createdAt: ''};
  const grace: WodSummaryResponse = {id: 3, title: 'Grace', wodType: 'FOR_TIME', scoreType: 'TIME', authorId: 1, isPublic: false, competition: false, createdAt: ''};


  const slice = (content: WodSummaryResponse[], last: boolean, number = 0): Slice<WodSummaryResponse> => ({
    content, last, first: number === 0, size: WOD_PAGE_SIZE, number, numberOfElements: content.length, empty: content.length === 0
  });

  const lastFilters = () => (mockWodService.getWods.mock.lastCall as any[])[0];

  const create = async () => {
    await TestBed.configureTestingModule({
      imports: [WodListComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: WodService, useValue: mockWodService},
        {
          provide: AccessService,
          useValue: {isAdmin}
        },
        {provide: ExporterService, useValue: mockExporterService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WodListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    mockWodService = {
      getWods: vi.fn().mockName('WodService.getWods'),
      getFacets: vi.fn().mockName('WodService.getFacets'),
      refreshNeeded$: new Subject<void>(),
      savedSearchQuery: signal(''),
      savedTypeFilter: signal(''),
      savedCategoryFilter: signal('')
    };
    mockWodService.getWods.mockReturnValue(of(slice([fran, cindy], false)));
    mockWodService.getFacets.mockReturnValue(of({wodTypes: ['AMRAP', 'FOR_TIME'], gymIds: [5, 9], authorIds: [77]}));
    isAdmin = signal(false);
    mockExporterService = {
      exportWods: vi.fn().mockName('ExporterService.exportWods').mockReturnValue(of('ok')),
      executeCsvExport: vi.fn().mockName('ExporterService.executeCsvExport')
    };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('public catalogue', () => {
    beforeEach(async () => {
      await create();
    });

    it('should load the first page of the public WODs, bypassing the global loader', () => {
      expect(lastFilters()).toMatchObject({scope: 'PUBLIC'});
      const context = (mockWodService.getWods.mock.lastCall as any[])[3] as HttpContext;
      expect(context.get(BYPASS_LOADER)).toBe(true);
      expect(mockWodService.getFacets.mock.lastCall[0]).toMatchObject({scope: 'PUBLIC'});
      expect(component.wods()).toEqual([fran, cindy]);
    });

    it('should append the next page on loadMore and stop when the last page is reached', () => {
      mockWodService.getWods.mockReturnValue(of(slice([grace], true, 1)));

      component.loadMore();

      expect((mockWodService.getWods.mock.lastCall as any[])[1]).toBe(1);
      expect(component.wods()).toEqual([fran, cindy, grace]);
      expect(component.hasMore()).toBe(false);

      mockWodService.getWods.mockClear();
      component.loadMore();
      expect(mockWodService.getWods).not.toHaveBeenCalled();
    });

    it('should debounce the search and combine it with the type filter', () => {
      vi.useFakeTimers();
      component.onTypeChange('FOR_TIME');
      mockWodService.getWods.mockClear();
      mockWodService.getWods.mockReturnValue(of(slice([fran], true)));

      component.onSearchChange('fr');
      component.onSearchChange('fran');
      expect(mockWodService.getWods).not.toHaveBeenCalled();

      vi.advanceTimersByTime(WOD_SEARCH_DEBOUNCE_MS);

      expect(mockWodService.getWods).toHaveBeenCalledTimes(1);
      expect(lastFilters()).toMatchObject({search: 'fran', type: 'FOR_TIME'});
      expect((mockWodService.getWods.mock.lastCall as any[])[1]).toBe(0);
      expect(component.wods()).toEqual([fran]);
    });

    it('should not reload when selecting the same type twice', () => {
      component.onTypeChange('AMRAP');
      mockWodService.getWods.mockClear();
      component.onTypeChange('AMRAP');
      expect(mockWodService.getWods).not.toHaveBeenCalled();
    });

    it('should filter by category and format together, and label a WOD "category / format"', () => {
      component.onCategoryChange('GIRLS');
      component.onTypeChange('FOR_TIME');

      expect(mockWodService.getWods.mock.lastCall![0]).toMatchObject({category: 'GIRLS', type: 'FOR_TIME'});
      expect(component.typeLabel({wodType: 'FOR_TIME', category: 'GIRLS'}))
        .toBe('WOD.CATEGORIES.GIRLS / WOD.WOD_TYPES.FOR_TIME');
      expect(component.typeLabel({wodType: 'AMRAP', category: null})).toBe('WOD.WOD_TYPES.AMRAP');
    });

    it('should reload when a refresh is requested', () => {
      mockWodService.getWods.mockClear();
      mockWodService.refreshNeeded$.next();
      expect(mockWodService.getWods).toHaveBeenCalledTimes(1);
    });

    it('should empty the list on error', () => {
      mockWodService.getWods.mockReturnValue(throwError(() => new Error('boom')));
      component.loadWods();
      expect(component.wods()).toEqual([]);
      expect(component.hasMore()).toBe(false);
      expect(component.isLoading()).toBe(false);
    });
  });

  describe('dynamic WOD types', () => {
    it('should drop a selected type that the new filters no longer return', async () => {
      await create();
      component.onTypeChange('AMRAP');
      mockWodService.getFacets.mockReturnValue(of({wodTypes: ['HERO'], gymIds: [], authorIds: []}));
      mockWodService.getWods.mockClear();

      component.onSearchChange('x');
      component.reload();

      expect(component.wodTypes()).toEqual(['HERO']);
      expect(component.typeFilter()).toBe('');
      expect(lastFilters().type).toBeUndefined();
    });
  });

  describe('CSV export', () => {
    const exportButton = () => Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.includes('EXPORT.EXPORT_CSV'));

    it('should hide the export button from non-admins', async () => {
      await create();
      expect(exportButton()).toBeUndefined();
    });

    it('should let an admin export the public WODs', async () => {
      isAdmin.set(true);
      await create();

      exportButton()!.click();

      expect(mockExporterService.exportWods).toHaveBeenCalled();
      expect(mockExporterService.executeCsvExport).toHaveBeenCalledWith(expect.anything(), component.isExporting);
    });
  });

  it('should let only admins create a public WOD', async () => {
    await create();
    expect(fixture.nativeElement.textContent).not.toContain('WOD.ACTIONS.ADD_PUBLIC');
    isAdmin.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('WOD.ACTIONS.ADD_PUBLIC');
  });
});
