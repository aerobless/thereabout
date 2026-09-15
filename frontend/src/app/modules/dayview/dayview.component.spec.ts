import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayviewComponent } from './dayview.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { afterEach, Mock, vi } from 'vitest';
import { Message, MessageService, HealthService, HealthDataResponse, LocationService, LocationHistoryEntry } from '../../../../generated/backend-api/thereabout';

describe('DayviewComponent', () => {
  let component: DayviewComponent;
  let fixture: ComponentFixture<DayviewComponent>;
  let getLocations: Mock<(from: string, to: string) => Observable<LocationHistoryEntry[]>>;
  let getHealthData: Mock<(from: string, to: string) => Observable<HealthDataResponse>>;
  let getMessages: Mock<(date: string) => Observable<Message[]>>;

  function message(id: number, senderIdentityId?: number): Message {
    return {
      id, type: 'text', source: 'Telegram',
      sender: {name: 'Sender', identityId: senderIdentityId},
      receiver: {name: 'Group'},
      timestamp: `2026-09-15T10:0${id}:00Z`, body: `Message ${id}`
    };
  }

  afterEach(() => vi.restoreAllMocks());

  beforeEach(async () => {
    getMessages = vi.fn();
    getHealthData = vi.fn();
    getLocations = vi.fn();
    await TestBed.configureTestingModule({
      imports: [DayviewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), {provide: MessageService, useValue: {getMessages}},
        {provide: HealthService, useValue: {getHealthDataByDateRange: getHealthData}},
        {provide: LocationService, useValue: {getLocations}}]
    })
    .overrideComponent(DayviewComponent, {set: {template: ''}})
    .compileComponents();

    fixture = TestBed.createComponent(DayviewComponent);
    component = fixture.componentInstance;
    component.selectedDate = new Date(2026, 8, 15);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('counts Theo as sent and other or unlinked senders as received, including group messages', () => {
    const response = new Subject<Message[]>();
    getMessages.mockReturnValue(response);

    component.loadMessages();
    expect(getMessages).toHaveBeenCalledWith('2026-09-15');
    expect(component.messagesLoading).toBe(true);
    response.next([message(3, 2), message(1, 1), message(2)]);

    expect(component.sentMessageCount).toBe(1);
    expect(component.receivedMessageCount).toBe(2);
    expect(component.messages.map(item => item.id)).toEqual([1, 2, 3]);
    expect(component.messagesLoading).toBe(false);
    expect(component.messagesError).toBe(false);
  });

  it('shows zero counts for an empty day', () => {
    const response = new Subject<Message[]>();
    getMessages.mockReturnValue(response);
    component.loadMessages();
    response.next([]);

    expect(component.sentMessageCount).toBe(0);
    expect(component.receivedMessageCount).toBe(0);
    expect(component.messagesLoading).toBe(false);
    expect(component.messagesError).toBe(false);
  });

  it('clears old data and distinguishes a failed request from an empty day', () => {
    const response = new Subject<Message[]>();
    getMessages.mockReturnValueOnce(response).mockReturnValue(new Subject<Message[]>());
    vi.spyOn(console, 'error').mockImplementation(() => {});
    component.messages = [message(1, 1)];
    component.messagesDialogVisible = true;
    component.loadMessages();

    expect(component.messages).toEqual([]);
    expect(component.messagesDialogVisible).toBe(false);
    response.error(new Error('Unavailable'));
    expect(component.messagesLoading).toBe(false);
    expect(component.messagesError).toBe(true);

    component.loadMessages();
    expect(component.messagesError).toBe(false);
    expect(component.messagesLoading).toBe(true);
  });

  it('ignores an earlier day response that arrives after the selected day response', () => {
    const oldDay = new Subject<Message[]>();
    const newDay = new Subject<Message[]>();
    getMessages
      .mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
    component.loadMessages();
    component.selectedDate = new Date(2026, 8, 16);
    component.loadMessages();
    newDay.next([message(2, 2)]);
    component.messagesDialogVisible = true;
    oldDay.next([message(1, 1)]);

    expect(component.messages.map(item => item.id)).toEqual([2]);
    expect(component.sentMessageCount).toBe(0);
    expect(component.receivedMessageCount).toBe(1);
    expect(component.messagesDialogVisible).toBe(true);
  });

  it('ignores stale failures while the selected day is loading', () => {
    const oldDay = new Subject<Message[]>();
    const newDay = new Subject<Message[]>();
    getMessages
      .mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
    component.loadMessages();
    component.selectedDate = new Date(2026, 8, 16);
    component.loadMessages();
    oldDay.error(new Error('Stale failure'));

    expect(component.messagesLoading).toBe(true);
    expect(component.messagesError).toBe(false);
    newDay.next([]);
    expect(component.messagesLoading).toBe(false);
  });
  it('loads 60 days for steps independently of weight history', () => {
    const response = new Subject<HealthDataResponse>();
    getHealthData.mockReturnValue(response);
    component.loadHealthData();
    expect(getHealthData).toHaveBeenCalledWith('2026-07-18', '2026-09-15');
    response.next({metrics: {
      step_count: [{date: '2026-09-14', qty: 5000}, {date: '2026-09-15', qty: 5500}],
      weight_body_mass: [{date: '2026-09-15', qty: 78}]
    }});
    expect(component.selectedDaySteps).toBe(5500);
    expect(component.selectedStepProgress?.level).toBe('bonus');
    expect(component.selectedStepProgress?.baseline).toBe(5000);
  });

  it('clears steps, closes the modal and ignores outdated health responses on date changes', () => {
    const oldDay = new Subject<HealthDataResponse>();
    const currentDay = new Subject<HealthDataResponse>();
    getHealthData.mockReturnValueOnce(oldDay).mockReturnValueOnce(currentDay);
    component.loadHealthData();
    oldDay.next({metrics: {step_count: [{date: '2026-09-15', qty: 1000}]}});
    component.stepsDialogVisible = true;
    component.selectedDate = new Date(2026, 8, 16);
    component.loadHealthData();
    expect(component.stepsDialogVisible).toBe(false);
    expect(component.selectedDaySteps).toBeNull();
    expect(component.stepsChartData).toBeNull();
    expect(component.stepsLoading).toBe(true);
    currentDay.next({metrics: {step_count: [{date: '2026-09-16', qty: 8000}]}});
    oldDay.next({metrics: {step_count: [{date: '2026-09-15', qty: 9999}]}});
    oldDay.error(new Error('Stale failure'));
    expect(component.selectedDaySteps).toBe(8000);
    expect(component.stepsError).toBe(false);
  });

  it('distinguishes an empty history from failed health loading', () => {
    const empty = new Subject<HealthDataResponse>();
    const failed = new Subject<HealthDataResponse>();
    getHealthData.mockReturnValueOnce(empty).mockReturnValueOnce(failed);
    vi.spyOn(console, 'error').mockImplementation(() => {});
    component.loadHealthData();
    empty.next({metrics: {}});
    expect(component.selectedStepProgress?.label).toBe('No step data');
    expect(component.stepsError).toBe(false);
    component.loadHealthData();
    failed.error(new Error('Unavailable'));
    expect(component.stepsLoading).toBe(false);
    expect(component.stepsError).toBe(true);
    expect(component.selectedStepProgress).toBeNull();
  });

  it('clears location data and closes the map when dates change, ignoring stale responses', () => {
    const oldDay = new Subject<LocationHistoryEntry[]>();
    const newDay = new Subject<LocationHistoryEntry[]>();
    getLocations.mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
    component.loadDayViewData();
    const point = {id: 1, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z'};
    oldDay.next([point]);
    component.locationDialogVisible = true;
    component.selectedDate = new Date(2026, 8, 16);
    component.loadDayViewData();
    expect(getLocations).toHaveBeenLastCalledWith('2026-09-16', '2026-09-16');
    expect(component.locationDialogVisible).toBe(false);
    expect(component.dayViewDataFull).toEqual([]);
    expect(component.locationsLoading).toBe(true);
    newDay.next([]);
    oldDay.next([point]);
    oldDay.error(new Error('Stale failure'));
    expect(component.dayViewDataFull).toEqual([]);
    expect(component.locationsError).toBe(false);
    expect(component.zoom).toBe(4);
  });

  it('shows location load failures separately from an empty day', () => {
    const failed = new Subject<LocationHistoryEntry[]>();
    getLocations.mockReturnValue(failed);
    component.loadDayViewData();
    failed.error(new Error('Unavailable'));
    expect(component.locationsError).toBe(true);
    expect(component.locationsLoading).toBe(false);
  });

  it('centres a single location at a useful zoom without fitting an empty bounds', () => {
    const map = {setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn()};
    component.dayViewDataFull = [{id: 1, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z'}];
    component.onExpandedMapInitialized(map as unknown as google.maps.Map);
    expect(map.setCenter).toHaveBeenCalledWith({lat: 47.4, lng: 8.5});
    expect(map.setZoom).toHaveBeenCalledWith(15);
    expect(map.fitBounds).not.toHaveBeenCalled();
  });

});
