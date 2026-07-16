import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LocationhistoryComponent } from './locationhistory.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Subject, of } from 'rxjs';
import { LocationService } from '../../../../generated/backend-api/thereabout';
import { vi } from 'vitest';

describe('LocationhistoryComponent', () => {
  let component: LocationhistoryComponent;
  let fixture: ComponentFixture<LocationhistoryComponent>;
  let queryParams: Subject<Params>;
  let locationService: LocationService;

  beforeEach(async () => {
    queryParams = new Subject<Params>();

    await TestBed.configureTestingModule({
      imports: [LocationhistoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        {provide: ActivatedRoute, useValue: {queryParams}}
      ]
    })
    .overrideComponent(LocationhistoryComponent, {set: {template: ''}})
    .compileComponents();
    
    fixture = TestBed.createComponent(LocationhistoryComponent);
    component = fixture.componentInstance;
    locationService = TestBed.inject(LocationService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads an inclusive date range and selects its first day', () => {
    vi.spyOn(component, 'loadHeatmapData').mockImplementation(() => undefined);
    vi.spyOn(component as any, 'loadLocationListData').mockImplementation(() => undefined);
    const loadDayViewData = vi.spyOn(component, 'loadDayViewData').mockImplementation(() => undefined);
    const loadDateRangeViewData = vi.spyOn(component, 'loadDateRangeViewData').mockImplementation(() => undefined);

    component.ngOnInit();
    queryParams.next({fromDate: '2026-07-14', toDate: '2026-07-16'});

    expect(component.dateRangeFrom).toBe('2026-07-14');
    expect(component.dateRangeTo).toBe('2026-07-16');
    expect(component.dateToString(component.exactDate)).toBe('2026-07-14');
    expect(loadDateRangeViewData).toHaveBeenCalledOnce();
    expect(loadDayViewData).toHaveBeenCalledOnce();
  });

  it('uses a valid date parameter as the selected blue day', () => {
    vi.spyOn(component, 'loadHeatmapData').mockImplementation(() => undefined);
    vi.spyOn(component as any, 'loadLocationListData').mockImplementation(() => undefined);
    vi.spyOn(component, 'loadDayViewData').mockImplementation(() => undefined);
    vi.spyOn(component, 'loadDateRangeViewData').mockImplementation(() => undefined);

    component.ngOnInit();
    queryParams.next({fromDate: '2026-07-14', toDate: '2026-07-16', date: '2026-07-15'});

    expect(component.dateToString(component.exactDate)).toBe('2026-07-15');
  });

  it('passes the inclusive range to the location API', () => {
    const getLocations = vi.spyOn(locationService, 'getLocations').mockReturnValue(of([]) as any);
    component.dateRangeFrom = '2026-07-14';
    component.dateRangeTo = '2026-07-16';

    component.loadDateRangeViewData();

    expect(getLocations).toHaveBeenCalledWith('2026-07-14', '2026-07-16');
  });

  it('ignores incomplete, malformed, and reversed date ranges', () => {
    vi.spyOn(component, 'loadHeatmapData').mockImplementation(() => undefined);
    vi.spyOn(component as any, 'loadLocationListData').mockImplementation(() => undefined);
    vi.spyOn(component, 'loadDayViewData').mockImplementation(() => undefined);
    const loadDateRangeViewData = vi.spyOn(component, 'loadDateRangeViewData').mockImplementation(() => undefined);

    component.ngOnInit();
    const invalidRanges = [
      {fromDate: '2026-07-14'},
      {fromDate: 'not-a-date', toDate: '2026-07-16'},
      {fromDate: '2026-07-17', toDate: '2026-07-16'}
    ];

    for (const params of invalidRanges) {
      queryParams.next(params);
      expect(component.dateRangeFrom).toBeUndefined();
      expect(component.dateRangeTo).toBeUndefined();
      expect(component.dateRangeViewDataFull).toEqual([]);
    }
    expect(loadDateRangeViewData).not.toHaveBeenCalled();
  });

  it('enables embed mode, selects the first day, and skips standard view data', () => {
    const loadHeatmapData = vi.spyOn(component, 'loadHeatmapData').mockImplementation(() => undefined);
    const loadLocationListData = vi.spyOn(component as any, 'loadLocationListData').mockImplementation(() => undefined);
    const getLocations = vi.spyOn(locationService, 'getLocations').mockReturnValue(of([]) as any);

    component.ngOnInit();
    queryParams.next({embed: 'true', fromDate: '2026-06-13', toDate: '2026-06-20'});

    expect(component.embedMode).toBe(true);
    expect(component.embedRangeValid).toBe(true);
    expect(component.dateToString(component.exactDate)).toBe('2026-06-13');
    expect(loadHeatmapData).not.toHaveBeenCalled();
    expect(loadLocationListData).not.toHaveBeenCalled();
    expect(getLocations).toHaveBeenCalledTimes(2);
    expect(getLocations).toHaveBeenCalledWith('2026-06-13', '2026-06-20');
    expect(getLocations).toHaveBeenCalledWith('2026-06-13', '2026-06-13');
  });

  it('uses an in-range date parameter and falls back for an out-of-range date in embed mode', () => {
    vi.spyOn(locationService, 'getLocations').mockReturnValue(of([]) as any);
    component.ngOnInit();

    queryParams.next({embed: 'true', fromDate: '2026-06-13', toDate: '2026-06-20', date: '2026-06-16'});
    expect(component.dateToString(component.exactDate)).toBe('2026-06-16');

    queryParams.next({embed: 'true', fromDate: '2026-06-13', toDate: '2026-06-20', date: '2026-06-21'});
    expect(component.dateToString(component.exactDate)).toBe('2026-06-13');
  });

  it('does not load location data for an invalid embed range', () => {
    const getLocations = vi.spyOn(locationService, 'getLocations').mockReturnValue(of([]) as any);

    component.ngOnInit();
    queryParams.next({embed: 'true', fromDate: '2026-06-20', toDate: '2026-06-13'});

    expect(component.embedRangeValid).toBe(false);
    expect(getLocations).not.toHaveBeenCalled();
  });

  it('moves the embed date within the range and stops at its boundaries', () => {
    component.embedRangeValid = true;
    component.embedFromDate = new Date(2026, 5, 13);
    component.embedToDate = new Date(2026, 5, 15);
    component.exactDate = new Date(2026, 5, 13);
    const loadDayViewData = vi.spyOn(component, 'loadDayViewData').mockImplementation(() => undefined);

    component.decrementEmbedDate();
    expect(component.dateToString(component.exactDate)).toBe('2026-06-13');
    expect(loadDayViewData).not.toHaveBeenCalled();

    component.incrementEmbedDate();
    component.incrementEmbedDate();
    component.incrementEmbedDate();
    expect(component.dateToString(component.exactDate)).toBe('2026-06-15');
    expect(loadDayViewData).toHaveBeenCalledTimes(2);
    expect(component.canIncrementEmbedDate()).toBe(false);
    expect(component.canDecrementEmbedDate()).toBe(true);
  });

  it('reloads only the selected day when the embed date picker changes', () => {
    component.embedRangeValid = true;
    component.embedFromDate = new Date(2026, 5, 13);
    component.embedToDate = new Date(2026, 5, 20);
    component.exactDate = new Date(2026, 5, 17);
    const loadDayViewData = vi.spyOn(component, 'loadDayViewData').mockImplementation(() => undefined);
    const loadDateRangeViewData = vi.spyOn(component, 'loadDateRangeViewData').mockImplementation(() => undefined);

    component.onEmbedDateChanged();

    expect(loadDayViewData).toHaveBeenCalledOnce();
    expect(loadDateRangeViewData).not.toHaveBeenCalled();
  });

  it('focuses the map on the selected day after switching dates', () => {
    const extend = vi.fn();
    vi.stubGlobal('google', {
      maps: {
        LatLngBounds: class {
          extend = extend;
        }
      }
    });
    const map = {
      fitBounds: vi.fn(),
      setCenter: vi.fn(),
      setZoom: vi.fn()
    };
    vi.spyOn(locationService, 'getLocations').mockReturnValue(of([
      {latitude: 47.37, longitude: 8.54},
      {latitude: 46.95, longitude: 7.45}
    ]) as any);
    component.embedMode = true;
    component.exactDate = new Date(2026, 5, 14);
    component.onEmbedMapInitialized(map as any);

    component.loadDayViewData(undefined, true);

    expect(extend).toHaveBeenCalledTimes(2);
    expect(map.fitBounds).toHaveBeenCalledWith(expect.anything(), 48);
  });

  it('fits the map to all trip locations once map and range data are available', () => {
    const extend = vi.fn();
    vi.stubGlobal('google', {
      maps: {
        LatLngBounds: class {
          extend = extend;
        }
      }
    });
    const map = {
      fitBounds: vi.fn(),
      setCenter: vi.fn(),
      setZoom: vi.fn()
    };
    component.embedMode = true;
    component.dateRangeViewDataFull = [
      {latitude: 47.37, longitude: 8.54},
      {latitude: 46.95, longitude: 7.45}
    ] as any;

    component.onEmbedMapInitialized(map as any);

    expect(extend).toHaveBeenCalledTimes(2);
    expect(map.fitBounds).toHaveBeenCalledWith(expect.anything(), 24);
  });

  it('opens the non-embed date range in a new tab', () => {
    component.dateRangeFrom = '2026-06-13';
    component.dateRangeTo = '2026-06-20';
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);

    component.openInThereabout();

    expect(open).toHaveBeenCalledWith(
      `${window.location.origin}/locationhistory?fromDate=2026-06-13&toDate=2026-06-20`,
      '_blank',
      'noopener'
    );
  });
});
