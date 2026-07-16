import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayPanelComponent } from './day-panel.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { LocationService } from '../../../../../generated/backend-api/thereabout';
import { of } from 'rxjs';
import { vi } from 'vitest';

describe('DayPanelComponent', () => {
  let component: DayPanelComponent;
  let fixture: ComponentFixture<DayPanelComponent>;
  let locationService: LocationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DayPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), MessageService]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DayPanelComponent);
    component = fixture.componentInstance;
    locationService = TestBed.inject(LocationService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render location rows', () => {
    component.dayViewDataFull = [{
      id: 1,
      timestamp: '2026-07-16T08:00:00Z',
      latitude: 47.37818,
      longitude: 8.54019,
      altitude: 408,
      horizontalAccuracy: 6
    }];

    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.p-datatable-tbody > tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('47.37818');
    expect(rows[0].textContent).toContain('8.54019');
  });

  it('refreshes the selected day and date range after adding a location', () => {
    const location = {
      id: 1,
      timestamp: '2026-07-16T12:00:00Z',
      latitude: 47.37818,
      longitude: 8.54019,
      altitude: 408
    };
    vi.spyOn(locationService, 'addLocation').mockReturnValue(of(location) as any);
    const loadDayViewData = vi.spyOn(component.loadDayViewData, 'emit');
    const loadDateRangeViewData = vi.spyOn(component.loadDateRangeViewData, 'emit');
    component.selectedLocationEntries = [];
    component.exactDate = new Date(2026, 6, 16);

    component.createNewLocationEntry();

    expect(loadDayViewData).toHaveBeenCalledWith(1);
    expect(loadDateRangeViewData).toHaveBeenCalledOnce();
  });

  it('refreshes the selected day and date range after editing a location', () => {
    const location = {
      id: 1,
      timestamp: '2026-07-16T12:00:00Z',
      latitude: 47.37818,
      longitude: 8.54019,
      altitude: 408
    };
    vi.spyOn(locationService, 'updateLocation').mockReturnValue(of(location) as any);
    const loadDayViewData = vi.spyOn(component.loadDayViewData, 'emit');
    const loadDateRangeViewData = vi.spyOn(component.loadDateRangeViewData, 'emit');
    component.selectedLocationEntries = [location];

    component.saveEdit();

    expect(loadDayViewData).toHaveBeenCalledOnce();
    expect(loadDateRangeViewData).toHaveBeenCalledOnce();
  });
});
