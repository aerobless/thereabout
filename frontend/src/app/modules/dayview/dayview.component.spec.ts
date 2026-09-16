import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayviewComponent } from './dayview.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import {MessageService as ToastService} from 'primeng/api';
import {MapMarker} from '@angular/google-maps';
import { afterEach, Mock, vi } from 'vitest';
import { Message, MessageService, HealthService, HealthDataResponse, LocationService, LocationHistoryEntry } from '../../../../generated/backend-api/thereabout';

describe('DayviewComponent', () => {
  let component: DayviewComponent;
  let fixture: ComponentFixture<DayviewComponent>;
  let getLocations: Mock<(from: string, to: string) => Observable<LocationHistoryEntry[]>>;
  let getHealthData: Mock<(from: string, to: string) => Observable<HealthDataResponse>>;
  const addLocation = vi.fn();
  const updateLocation = vi.fn();
  const deleteLocations = vi.fn();
  const toast = {add: vi.fn()};
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
    addLocation.mockReset();
    updateLocation.mockReset();
    deleteLocations.mockReset();
    toast.add.mockReset();
    getMessages = vi.fn();
    getHealthData = vi.fn();
    getLocations = vi.fn();
    await TestBed.configureTestingModule({
      imports: [DayviewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), {provide: ToastService, useValue: toast}, {provide: MessageService, useValue: {getMessages}},
        {provide: HealthService, useValue: {getHealthDataByDateRange: getHealthData}},
        {provide: LocationService, useValue: {getLocations, addLocation, updateLocation, deleteLocations}}]
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

  it('clears location data while keeping the modal open on date changes, ignoring stale responses', () => {
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
    expect(component.locationDialogVisible).toBe(true);
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

  function point(id = 1): LocationHistoryEntry {
    return {id, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z', altitude: 400, note: 'Original'};
  }

  function selectPoint() {
    const entry = point();
    component.dayViewDataFull = [entry];
    component.selectedLocationEntries = [entry];
    return entry;
  }

  it('creates at the actual map centre and local noon without mutating the selected date', () => {
    const originalDate = component.selectedDate.getTime();
    const map = {setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn(), getCenter: () => ({toJSON: () => ({lat: 10, lng: 20})})};
    component.onExpandedMapInitialized(map as any);
    const created = {...point(2), latitude: 10, longitude: 20};
    addLocation.mockReturnValue(of(created));
    getLocations.mockReturnValue(of([created]));
    component.locationDialogVisible = true;
    component.createLocation();
    const noon = new Date(2026, 8, 15, 12).toISOString();
    expect(addLocation).toHaveBeenCalledWith({id: 0, latitude: 10, longitude: 20, timestamp: noon, altitude: 0});
    expect(component.selectedDate.getTime()).toBe(originalDate);
    expect(component.selectedLocationEntries).toEqual([created]);
    expect(component.locationDialogVisible).toBe(true);
  });

  it('copies the selected position and timestamp, but blocks New with multiple selections', () => {
    const selected = selectPoint();
    addLocation.mockReturnValue(of(point(2)));
    getLocations.mockReturnValue(of([selected, point(2)]));
    component.createLocation();
    expect(addLocation).toHaveBeenCalledWith(expect.objectContaining({latitude: selected.latitude, longitude: selected.longitude, timestamp: new Date(selected.timestamp).toISOString(), altitude: 0}));
    component.selectedLocationEntries = [selected, point(2)];
    component.createLocation();
    expect(addLocation).toHaveBeenCalledOnce();
  });

  it('edits a detached draft, retaining it on failure and discarding it on cancel', () => {
    const selected = selectPoint();
    component.editLocation();
    const draft = component.locationEditDraft!;
    draft.entry.note = 'Changed';
    draft.date = new Date(2026, 8, 15, 15);
    expect(selected.note).toBe('Original');
    expect(selected.timestamp).toBe('2026-09-15T08:00:00Z');
    updateLocation.mockReturnValue(throwError(() => new Error('Offline')));
    component.saveLocation();
    expect(component.locationEditDraft).toBe(draft);
    expect(component.dayViewDataFull).toEqual([selected]);
    expect(component.locationSaving).toBe(false);
    expect(toast.add).toHaveBeenLastCalledWith(expect.objectContaining({severity: 'error'}));
    component.locationEditDraft = null;
    expect(selected.note).toBe('Original');
  });

  it('rejects missing edit dates and removes entries moved to another day after saving', () => {
    selectPoint();
    component.editLocation();
    component.locationEditDraft!.date = null;
    component.saveLocation();
    expect(updateLocation).not.toHaveBeenCalled();
    component.locationEditDraft!.date = new Date(2026, 8, 16, 12);
    updateLocation.mockReturnValue(of({...point(), timestamp: new Date(2026, 8, 16, 12).toISOString()}));
    getLocations.mockReturnValue(of([]));
    component.saveLocation();
    expect(component.locationEditDraft).toBeNull();
    expect(component.dayViewDataFull).toEqual([]);
    expect(component.selectedLocationEntries).toEqual([]);
  });

  it('deletes selected IDs only after success and blocks overlapping writes', () => {
    const entries = [point(), point(2)];
    component.dayViewDataFull = entries;
    component.selectedLocationEntries = entries;
    const response = new Subject<void>();
    deleteLocations.mockReturnValue(response);
    getLocations.mockReturnValue(of([]));
    component.deleteLocations();
    component.deleteLocations();
    expect(deleteLocations).toHaveBeenCalledExactlyOnceWith([1, 2]);
    expect(component.selectedLocationEntries).toEqual(entries);
    expect(component.locationSaving).toBe(true);
    response.next();
    response.complete();
    expect(component.selectedLocationEntries).toEqual([]);
    expect(component.dayViewDataFull).toEqual([]);
    expect(component.locationSaving).toBe(false);
  });

  it('retains selections after a failed delete and supports retry', () => {
    const entry = selectPoint();
    deleteLocations.mockReturnValue(throwError(() => new Error('Offline')));
    component.deleteLocations();
    expect(component.selectedLocationEntries).toEqual([entry]);
    expect(component.locationSaving).toBe(false);
  });

  it('restores failed marker moves and rejects mobile or missing-coordinate drag events', () => {
    const entry = selectPoint();
    const restore = vi.fn();
    const marker = {marker: {setPosition: restore}} as unknown as MapMarker;
    const event = {latLng: {lat: () => 12, lng: () => 34}} as google.maps.MapMouseEvent;
    updateLocation.mockReturnValue(throwError(() => new Error('Offline')));
    component.markerDragged(entry, event, marker);
    expect(updateLocation).toHaveBeenCalledWith(entry.id, {...entry, latitude: 12, longitude: 34});
    expect(restore).toHaveBeenCalledWith({lat: 47.4, lng: 8.5});
    expect(entry.latitude).toBe(47.4);
    component.mobileLocationView = true;
    expect(component.canDragLocations).toBe(false);
    component.markerDragged(entry, event, marker);
    component.mobileLocationView = false;
    component.markerDragged(entry, {latLng: null} as google.maps.MapMouseEvent, marker);
    expect(updateLocation).toHaveBeenCalledOnce();
  });

  it('updates the route and selection after a move without changing the viewport', () => {
    const entry = selectPoint();
    const map = {setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn()};
    component.onExpandedMapInitialized(map as any);
    map.setCenter.mockClear(); map.setZoom.mockClear();
    const moved = {...entry, latitude: 12, longitude: 34, horizontalAccuracy: 0};
    updateLocation.mockReturnValue(of(moved));
    getLocations.mockReturnValue(of([moved]));
    component.markerDragged(entry, {latLng: {lat: () => 12, lng: () => 34}} as google.maps.MapMouseEvent, {marker: {setPosition: vi.fn()}} as any);
    expect(component.dayViewDataFull).toEqual([moved]);
    expect(component.selectedLocationEntries).toEqual([moved]);
    expect(map.setCenter).not.toHaveBeenCalled();
    expect(map.setZoom).not.toHaveBeenCalled();
  });

  it('ignores a mutation result after navigation to another day', () => {
    selectPoint();
    const response = new Subject<LocationHistoryEntry>();
    updateLocation.mockReturnValue(response);
    component.editLocation(); component.saveLocation();
    component.selectedDate = new Date(2026, 8, 16);
    getLocations.mockReturnValue(of([]));
    component.loadDayViewData();
    response.next({...point(), note: 'Old result'}); response.complete();
    expect(component.dayViewDataFull).toEqual([]);
    expect(component.selectedLocationEntries).toEqual([]);
    expect(getLocations).toHaveBeenCalledOnce();
  });

  it('locates repeatedly at the selected point and falls back to the first entry', () => {
    selectPoint();
    const map = {setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn()};
    component.onExpandedMapInitialized(map as any);
    map.setCenter.mockClear(); map.setZoom.mockClear();
    component.locateLocation(); component.locateLocation();
    expect(map.setCenter).toHaveBeenCalledTimes(2);
    expect(map.setZoom).toHaveBeenLastCalledWith(16);
    component.selectedLocationEntries = [];
    component.locateLocation();
    expect(map.setZoom).toHaveBeenLastCalledWith(11);
    component.dayViewDataFull = [];
    component.locateLocation();
    expect(map.setCenter).toHaveBeenCalledTimes(3);
  });

  it('selects the nearest route point by ID and toggles it off', () => {
    const first = point();
    const second = {...point(2), latitude: 48, longitude: 9};
    component.dayViewDataFull = [first, second];
    const event = {latLng: {lat: () => 48.001, lng: () => 9}} as google.maps.PolyMouseEvent;
    component.dayLineClick(event);
    expect(component.selectedLocationEntries).toEqual([second]);
    component.selectedLocationEntries = [{...second}];
    component.dayLineClick(event);
    expect(component.selectedLocationEntries).toEqual([]);
  });

  it('updates drag availability on viewport changes and removes the listener on destruction', () => {
    const listeners: ((event: MediaQueryListEvent) => void)[] = [];
    const removeEventListener = vi.fn();
    vi.stubGlobal('matchMedia', () => ({matches: false, addEventListener: (_: string, callback: any) => listeners.push(callback), removeEventListener}));
    const responsiveFixture = TestBed.createComponent(DayviewComponent);
    const responsive = responsiveFixture.componentInstance;
    expect(responsive.canDragLocations).toBe(true);
    responsive.locationEditDraft = {entry: point(), date: new Date()};
    listeners[0]({matches: true} as MediaQueryListEvent);
    expect(responsive.canDragLocations).toBe(false);
    expect(responsive.locationEditDraft).toBeNull();
    listeners[0]({matches: false} as MediaQueryListEvent);
    expect(responsive.canDragLocations).toBe(true);
    responsiveFixture.destroy();
    expect(removeEventListener).toHaveBeenCalledWith('change', listeners[0]);
    vi.unstubAllGlobals();
  });

});
