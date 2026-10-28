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
import {AdminService} from '../../../api/antares/services/admin.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {APP_ICONS} from '../../../app.config';
import {WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {MembershipSummary} from '../../../api/antares/models/membership.model';
import {Slice} from '../../../core/models/pagination.model';

describe('WodListComponent', () => {
  let component: WodListComponent;
  let fixture: ComponentFixture<WodListComponent>;
  let mockWodService: any;
  let memberships: WritableSignal<MembershipSummary[]>;
  let isAdmin: WritableSignal<boolean>;
  let mockExporterService: any;
  let mockAdminService: any;
  let writableGymIds: number[];

  const fran: WodSummaryResponse = {id: 1, title: 'Fran', wodType: 'FOR_TIME', scoreType: 'TIME', repScheme: '21-15-9', timeCapSeconds: 600, gymId: 5, isPublic: false, competition: false, createdAt: ''};
  const cindy: WodSummaryResponse = {id: 2, title: 'Cindy', wodType: 'AMRAP', scoreType: 'ROUNDS_REPS', isPublic: true, competition: false, createdAt: ''};
  const grace: WodSummaryResponse = {id: 3, title: 'Grace', wodType: 'FOR_TIME', scoreType: 'TIME', authorId: 1, isPublic: false, competition: false, createdAt: ''};

  const box: MembershipSummary = {gymId: 5, gymName: 'Box', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: false, usable: true};
  const zeus: MembershipSummary = {gymId: 7, gymName: 'Zeus', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'ACTIVE', permissions: [], gymProgramming: false, usable: true};

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
          useValue: {memberships, isAdmin, hasGymPermission: (gymId: number) => writableGymIds.includes(gymId)}
        },
        {provide: ExporterService, useValue: mockExporterService},
        {provide: AdminService, useValue: mockAdminService}
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
      savedCategoryFilter: signal(''),
      savedScopeTab: signal(''),
      savedAdminGymId: signal<number | null>(null),
      savedAdminAuthorId: signal<number | null>(null)
    };
    mockWodService.getWods.mockReturnValue(of(slice([fran, cindy], false)));
    mockWodService.getFacets.mockReturnValue(of({wodTypes: ['AMRAP', 'FOR_TIME'], gymIds: [5, 9], authorIds: [77]}));
    memberships = signal([box, zeus]);
    isAdmin = signal(false);
    writableGymIds = [];
    mockAdminService = {
      searchGyms: vi.fn().mockReturnValue(of({content: [{id: 5, name: 'Box'}, {id: 9, name: 'Atlas'}], last: true})),
      lookupUsers: vi.fn().mockReturnValue(of([{id: 77, firstName: 'Ann', lastName: 'Thlete', email: 'ann@x.com'}]))
    };
    mockExporterService = {
      exportWods: vi.fn().mockName('ExporterService.exportWods').mockReturnValue(of('ok')),
      executeCsvExport: vi.fn().mockName('ExporterService.executeCsvExport')
    };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('with gym memberships', () => {
    beforeEach(async () => create());

    it('should load the first page of the first gym, bypassing the global loader', () => {
      expect(component.wodTypes()).toEqual(['AMRAP', 'FOR_TIME']);
      expect(mockWodService.getFacets).toHaveBeenCalledWith({scope: 'GYM', gymId: 5, search: undefined}, expect.anything());
      const args = mockWodService.getWods.mock.lastCall as any[];
      expect(args[0]).toEqual({search: undefined, type: undefined, scope: 'GYM', gymId: 5});
      expect(args.slice(1, 3)).toEqual([0, WOD_PAGE_SIZE]);
      expect((args[3] as HttpContext).get(BYPASS_LOADER)).toBe(true);
      expect(component.wods()).toEqual([fran, cindy]);
      expect(component.hasMore()).toBe(true);
    });

    it('should expose one tab per gym between "All" and the personal/public tabs', () => {
      expect(component.scopeTabs().map(t => t.key)).toEqual(['ALL', 'GYM:5', 'GYM:7', 'MINE', 'PUBLIC']);
      expect(component.scopeTabs()[1]).toMatchObject({label: 'Box', translate: false, gymId: 5});
    });

    it('should render WODs with their visibility', () => {
      const text = fixture.nativeElement.textContent;
      expect(text).toContain('Fran');
      expect(text).toContain('Box');
      expect(text).toContain('WOD.VISIBILITY.PUBLIC');
    });

    it('should reload with the selected scope and remember it', () => {
      component.onScopeChange('GYM:7');
      expect(lastFilters()).toMatchObject({scope: 'GYM', gymId: 7});
      expect(mockWodService.savedScopeTab()).toBe('GYM:7');

      component.onScopeChange('MINE');
      expect(lastFilters()).toMatchObject({scope: 'MINE'});
      expect(lastFilters().gymId).toBeUndefined();

      component.onScopeChange('PUBLIC');
      expect(lastFilters()).toMatchObject({scope: 'PUBLIC'});
    });

    it('should not reload when selecting the current scope', () => {
      mockWodService.getWods.mockClear();
      component.onScopeChange('GYM:5');
      expect(mockWodService.getWods).not.toHaveBeenCalled();
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

  describe('without membership', () => {
    beforeEach(async () => {
      memberships.set([]);
      await create();
    });

    it('should default to all WODs', () => {
      expect(component.scopeTabs().map(t => t.key)).toEqual(['ALL', 'MINE', 'PUBLIC']);
      expect(lastFilters()).toMatchObject({scope: 'ALL'});
      expect(lastFilters().gymId).toBeUndefined();
    });
  });

  it('should restore a remembered scope tab and fall back when it no longer exists', async () => {
    mockWodService.savedScopeTab.set('GYM:99');
    await create();
    expect(component.activeScopeTab().key).toBe('GYM:5');

    mockWodService.savedScopeTab.set('PUBLIC');
    expect(component.activeScopeTab().key).toBe('PUBLIC');
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

  describe('dynamic WOD types', () => {
    it('should drop a selected type that the new filters no longer return', async () => {
      await create();
      component.onTypeChange('AMRAP');
      mockWodService.getFacets.mockReturnValue(of({wodTypes: ['HERO'], gymIds: [], authorIds: []}));
      mockWodService.getWods.mockClear();

      component.onScopeChange('PUBLIC');

      expect(component.wodTypes()).toEqual(['HERO']);
      expect(component.typeFilter()).toBe('');
      expect(lastFilters()).toMatchObject({scope: 'PUBLIC'});
      expect(lastFilters().type).toBeUndefined();
    });
  });

  describe('contextual create button', () => {
    it('should create a gym WOD on a gym tab where the user programs WODs', async () => {
      writableGymIds = [5];
      await create();
      expect(component.createTarget()).toEqual({labelKey: 'WOD.ACTIONS.ADD_FOR_GYM', gymName: 'Box', queryParams: {gymId: 5}});
    });

    it('should create a personal WOD on a gym tab without programming rights and on "Mine"', async () => {
      await create();
      expect(component.createTarget()).toEqual({labelKey: 'WOD.ACTIONS.ADD_PERSONAL', queryParams: {visibility: 'PRIVATE'}});

      component.onScopeChange('MINE');
      expect(component.createTarget().labelKey).toBe('WOD.ACTIONS.ADD_PERSONAL');

      component.onScopeChange('ALL');
      expect(component.createTarget()).toEqual({labelKey: 'WOD.ACTIONS.ADD', queryParams: {}});
    });
  });

  describe('admin selectors', () => {
    beforeEach(async () => {
      isAdmin.set(true);
      memberships.set([]);
      await create();
    });

    it('should offer every gym and athlete from the facets, with their names', () => {
      expect(component.showAdminFilters()).toBe(true);
      expect(component.adminGyms()).toEqual([{id: 9, name: 'Atlas'}, {id: 5, name: 'Box'}]);
      expect(mockAdminService.lookupUsers).toHaveBeenCalledWith([77]);
      expect(component.adminAuthors().map(a => a.id)).toEqual([77]);
      expect(fixture.nativeElement.textContent).toContain('Ann Thlete');
    });

    it('should list a gym or an athlete, never both', () => {
      component.onAdminGymChange('9');
      expect(lastFilters()).toMatchObject({scope: 'GYM', gymId: 9});

      component.onAdminAuthorChange('77');
      expect(component.adminGymId()).toBeNull();
      expect(lastFilters()).toMatchObject({scope: 'ALL', authorId: 77});
      expect(lastFilters().gymId).toBeUndefined();

      component.onAdminAuthorChange('');
      expect(lastFilters()).toMatchObject({scope: 'ALL'});
      expect(lastFilters().authorId).toBeUndefined();
    });

    it('should hide the selectors outside the "All" tab', () => {
      component.onScopeChange('PUBLIC');
      expect(component.showAdminFilters()).toBe(false);
    });
  });

  it('should not query admin data for regular users', async () => {
    await create();
    expect(mockAdminService.searchGyms).not.toHaveBeenCalled();
    expect(mockAdminService.lookupUsers).not.toHaveBeenCalled();
  });
});
