import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, Subject} from 'rxjs';
import {vi} from 'vitest';
import {editionYear, WOD_PAGE_SIZE, WOD_SEARCH_DEBOUNCE_MS, WodListComponent} from './wod-list.component';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {AccessService} from '../../../core/services/access.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
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
      countWods: vi.fn().mockName('WodService.countWods'),
      getFacets: vi.fn().mockName('WodService.getFacets'),
      refreshNeeded$: new Subject<void>(),
      savedSearchQuery: signal(''),
      savedTypeFilter: signal(''),
      savedStructureFilter: signal(''),
      savedCategoryFilter: signal(''),
      savedPage: signal(0)
    };
    mockWodService.getWods.mockReturnValue(of(slice([{...fran, movementNames: ['Thruster', 'Pull-up']}, cindy], false)));
    mockWodService.countWods.mockReturnValue(of(45));
    mockWodService.getFacets.mockReturnValue(of({
      wodTypes: ['AMRAP', 'FOR_TIME'], categories: ['GIRLS', 'OPEN'], gymIds: [], authorIds: [],
      categoryCounts: {GIRLS: 28, OPEN: 46}, total: 74, structures: []
    }));
    isAdmin = signal(false);
    mockExporterService = {
      exportWods: vi.fn().mockName('ExporterService.exportWods').mockReturnValue(of('ok')),
      executeCsvExport: vi.fn().mockName('ExporterService.executeCsvExport')
    };
  });

  afterEach(() => vi.useRealTimers());

  it('should show the families with their counts, the rows with a movement preview, and the range', async () => {
    await create();
    const el: HTMLElement = fixture.nativeElement;
    expect(lastFilters()).toEqual({scope: 'PUBLIC', search: undefined, type: undefined, structure: undefined, category: undefined});
    expect((mockWodService.getWods.mock.lastCall as any[])[4]).toBe('title,asc');
    const chips = [...el.querySelectorAll('.family-chip')].map(c => c.textContent?.trim());
    expect(chips).toEqual(['WOD.ACTIONS.ALL_CATEGORIES · 74', 'WOD.CATEGORIES.GIRLS · 28', 'WOD.CATEGORIES.OPEN · 46']);
    expect(el.querySelectorAll('.catalogue-row').length).toBe(2);
    expect(el.textContent).toContain('Thruster · Pull-up');
    expect(component.range()).toEqual({from: 1, to: 20, total: 45});
  });

  it('should number the pages and load the page asked', async () => {
    await create();
    expect(component.pageNumbers()).toEqual([0, 1, 2]);
    (fixture.nativeElement.querySelectorAll('.page-number')[2] as HTMLButtonElement).click();
    expect(component.page()).toBe(2);
    expect((mockWodService.getWods.mock.lastCall as any[])[1]).toBe(2);
    expect(component.range()).toEqual({from: 41, to: 45, total: 45});
  });

  it('should group a competition family by edition, latest first, events in order', async () => {
    mockWodService.getWods.mockReturnValue(of(slice([
      {...cindy, id: 10, title: 'Open 26.3', category: 'OPEN'}, {...cindy, id: 11, title: 'Open 26.1', category: 'OPEN'},
      {...cindy, id: 12, title: 'Open 25.2', category: 'OPEN'}, {...cindy, id: 13, title: 'Open 26.2', category: 'OPEN'}
    ], true)));
    await create();
    component.onCategoryChange('OPEN');
    fixture.detectChanges();

    expect((mockWodService.getWods.mock.lastCall as any[])[4]).toBe('title,desc');
    expect(component.groups().map(g => g.heading)).toEqual(['WOD.CATEGORIES.OPEN 2026', 'WOD.CATEGORIES.OPEN 2025']);
    expect(component.groups()[0].wods.map(w => w.title)).toEqual(['Open 26.1', 'Open 26.2', 'Open 26.3']);
    expect(editionYear('Games 2024 - Final 2421')).toBe(2024);
  });

  it('should offer the sub-categories of the chosen format and filter on one', async () => {
    await create();
    mockWodService.getFacets.mockReturnValue(of({
      wodTypes: ['AMRAP', 'FOR_TIME'], categories: ['GIRLS'], gymIds: [], authorIds: [], structures: ['ROUNDS', 'CHIPPER']
    }));
    component.onTypeChange('FOR_TIME');
    fixture.detectChanges();
    expect(mockWodService.getFacets).toHaveBeenLastCalledWith(expect.objectContaining({type: 'FOR_TIME'}), expect.anything());
    const chips = [...fixture.nativeElement.querySelectorAll('.structure-chip')] as HTMLButtonElement[];
    expect(chips.map(c => c.id)).toEqual(['structure-ROUNDS', 'structure-CHIPPER']);

    chips[1].click();
    expect(lastFilters()).toMatchObject({type: 'FOR_TIME', structure: 'CHIPPER'});
    expect(component.page()).toBe(0);
  });

  it('should debounce the search and go back to the first page', async () => {
    vi.useFakeTimers();
    await create();
    component.page.set(2);
    component.onSearchChange('fran');
    vi.advanceTimersByTime(WOD_SEARCH_DEBOUNCE_MS);
    expect(component.page()).toBe(0);
    expect(lastFilters()).toMatchObject({search: 'fran'});
  });

  it('should offer the CSV export and the creation to admins only', async () => {
    isAdmin.set(true);
    await create();
    expect(fixture.nativeElement.querySelector('a[href="/wods/new"]')).toBeTruthy();
    component.exportCsv();
    expect(mockExporterService.executeCsvExport).toHaveBeenCalled();
  });
});
