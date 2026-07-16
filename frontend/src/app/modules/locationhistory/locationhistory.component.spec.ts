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
});
