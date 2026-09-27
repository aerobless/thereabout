import { CurrentUserService } from '../../shared/current-user/current-user.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DayviewComponent } from './dayview.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Params, Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MessageService as ToastService } from 'primeng/api';
import { MapMarker } from '@angular/google-maps';
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
    const toast = { add: vi.fn() };
    let getMessages: Mock<(date: string) => Observable<Message[]>>;
    let queryParams: Subject<Params>;
    function message(id: number, senderIdentityId?: number): Message {
        return {
            id, type: 'text', source: 'Telegram',
            sender: { name: 'Sender', identityId: senderIdentityId },
            receiver: { name: 'Receiver', identityId: 1 },
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
        queryParams = new Subject<Params>();
        await TestBed.configureTestingModule({
            imports: [DayviewComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ToastService, useValue: toast }, { provide: MessageService, useValue: { getMessages } },
                { provide: ActivatedRoute, useValue: { queryParams } },
                { provide: HealthService, useValue: { getHealthDataByDateRange: getHealthData } },
                { provide: LocationService, useValue: { getLocations, addLocation, updateLocation, deleteLocations } }]
        })
            .overrideComponent(DayviewComponent, { set: { template: '' } })
            .compileComponents();
        fixture = TestBed.createComponent(DayviewComponent);
        component = fixture.componentInstance;
        TestBed.inject(CurrentUserService).verifiedState.set({ status: 'resolved', identityId: 1 });
        component.selectedDate = new Date(2026, 8, 15);
    });
    it('should create', () => {
        expect(component).toBeTruthy();
    });
    it.each([
        [new Date(2026, 8, 23), '2026-09-23'],
        [new Date(2024, 1, 29), '2024-02-29'],
        [new Date(2026, 0, 1, 0, 5), '2026-01-01'],
        [new Date(2026, 2, 29, 23, 55), '2026-03-29']
    ])('builds service links from the selected local date %s', (date, iso) => {
        component.selectedDate = date as Date;
        expect(component.dayLinks.map(link => [link.label, link.url])).toEqual([
            ['Photos', `https://photos.google.com/search/${iso}`],
            ['Expenses', `https://firefly.w1nter.com/transactions/all/${iso}/${iso}`]
        ]);
    });
    it('clears energy on date changes and ignores superseded responses and failures', () => {
        const oldDay = new Subject<HealthDataResponse>();
        const currentDay = new Subject<HealthDataResponse>();
        getHealthData.mockReturnValueOnce(oldDay).mockReturnValueOnce(currentDay);
        component.health.loadHealthData();
        oldDay.next({ metrics: { active_energy: [{ date: '2026-09-15', qty: 500 }], basal_energy_burned: [{ date: '2026-09-15', qty: 1500 }] } });
        expect(component.health.activeEnergyRecords()[0].qty).toBe(500);
        expect(component.health.basalEnergyRecords()[0].qty).toBe(1500);
        component.selectedDate = new Date(2026, 8, 16);
        component.health.loadHealthData();
        expect(component.health.activeEnergyRecords()).toEqual([]);
        expect(component.health.basalEnergyRecords()).toEqual([]);
        currentDay.next({ metrics: { active_energy: [{ date: '2026-09-16', qty: 600 }], basal_energy_burned: [{ date: '2026-09-16', qty: 1600 }] } });
        oldDay.next({ metrics: { active_energy: [{ date: '2026-09-15', qty: 9999 }] } });
        oldDay.error(new Error('Stale failure'));
        expect(component.health.activeEnergyRecords()[0].qty).toBe(600);
        expect(component.health.basalEnergyRecords()[0].qty).toBe(1600);
        expect(component.health.stepsError()).toBe(false);
    });
    it('omits today from the URL and retains other dates without removing unrelated query parameters', () => {
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        vi.spyOn(component.location, 'loadDayViewData').mockImplementation(() => undefined);
        vi.spyOn(component.health, 'loadHealthData').mockImplementation(() => undefined);
        vi.spyOn(component.messages, 'loadMessages').mockImplementation(() => undefined);
        component.goToToday();
        expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { date: null }, queryParamsHandling: 'merge', replaceUrl: false }));
        component.goToPreviousDay();
        expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { date: component.dateToString(component.selectedDate) } }));
        component.goToNextDay();
        expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { date: null } }));
    });
    it('loads today at a bare URL, normalizes explicit today links and handles browser history without duplicate loads', () => {
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        const load = vi.spyOn(component.location, 'loadDayViewData').mockImplementation(() => undefined);
        vi.spyOn(component.health, 'loadHealthData').mockImplementation(() => undefined);
        vi.spyOn(component.messages, 'loadMessages').mockImplementation(() => undefined);
        const today = component.dateToString(new Date());
        component.ngOnInit();
        queryParams.next({});
        expect(component.dateToString(component.selectedDate)).toBe(today);
        expect(navigate).not.toHaveBeenCalled();
        queryParams.next({ date: today });
        expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { date: null }, replaceUrl: true }));
        queryParams.next({});
        expect(load).toHaveBeenCalledTimes(1);
        queryParams.next({ date: '2020-01-02' });
        expect(component.dateToString(component.selectedDate)).toBe('2020-01-02');
        queryParams.next({});
        expect(component.dateToString(component.selectedDate)).toBe(today);
        expect(load).toHaveBeenCalledTimes(3);
    });
    it('counts only explicit sender and receiver identities for the current user', () => {
        const response = new Subject<Message[]>();
        getMessages.mockReturnValue(response);
        component.messages.loadMessages();
        expect(getMessages).toHaveBeenCalledWith('2026-09-15');
        expect(component.messages.messagesLoading()).toBe(true);
        response.next([message(3, 2), message(1, 1), message(2)]);
        expect(component.messages.sentMessageCount()).toBe(1);
        expect(component.messages.receivedMessageCount()).toBe(2);
        expect(component.messages.messages().map(item => item.id)).toEqual([1, 2, 3]);
        expect(component.messages.messagesLoading()).toBe(false);
        expect(component.messages.messagesError()).toBe(false);
    });
    it('shows zero counts for an empty day', () => {
        const response = new Subject<Message[]>();
        getMessages.mockReturnValue(response);
        component.messages.loadMessages();
        response.next([]);
        expect(component.messages.sentMessageCount()).toBe(0);
        expect(component.messages.receivedMessageCount()).toBe(0);
        expect(component.messages.messagesLoading()).toBe(false);
        expect(component.messages.messagesError()).toBe(false);
    });
    it('clears old data and distinguishes a failed request from an empty day', () => {
        const response = new Subject<Message[]>();
        getMessages.mockReturnValueOnce(response).mockReturnValue(new Subject<Message[]>());
        vi.spyOn(console, 'error').mockImplementation(() => { });
        component.messages.messages.set([message(1, 1)]);
        component.messages.messagesDialogVisible.set(true);
        component.messages.loadMessages();
        expect(component.messages.messages()).toEqual([]);
        expect(component.messages.messagesDialogVisible()).toBe(false);
        response.error(new Error('Unavailable'));
        expect(component.messages.messagesLoading()).toBe(false);
        expect(component.messages.messagesError()).toBe(true);
        component.messages.loadMessages();
        expect(component.messages.messagesError()).toBe(false);
        expect(component.messages.messagesLoading()).toBe(true);
    });
    it('ignores an earlier day response that arrives after the selected day response', () => {
        const oldDay = new Subject<Message[]>();
        const newDay = new Subject<Message[]>();
        getMessages
            .mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
        component.messages.loadMessages();
        component.selectedDate = new Date(2026, 8, 16);
        component.messages.loadMessages();
        newDay.next([message(2, 2)]);
        component.messages.messagesDialogVisible.set(true);
        oldDay.next([message(1, 1)]);
        expect(component.messages.messages().map(item => item.id)).toEqual([2]);
        expect(component.messages.sentMessageCount()).toBe(0);
        expect(component.messages.receivedMessageCount()).toBe(1);
        expect(component.messages.messagesDialogVisible()).toBe(true);
    });
    it('ignores stale failures while the selected day is loading', () => {
        const oldDay = new Subject<Message[]>();
        const newDay = new Subject<Message[]>();
        getMessages
            .mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
        component.messages.loadMessages();
        component.selectedDate = new Date(2026, 8, 16);
        component.messages.loadMessages();
        oldDay.error(new Error('Stale failure'));
        expect(component.messages.messagesLoading()).toBe(true);
        expect(component.messages.messagesError()).toBe(false);
        newDay.next([]);
        expect(component.messages.messagesLoading()).toBe(false);
    });
    it('loads 60 days for steps independently of weight history', () => {
        const response = new Subject<HealthDataResponse>();
        getHealthData.mockReturnValue(response);
        component.health.loadHealthData();
        expect(getHealthData).toHaveBeenCalledWith('2026-07-18', '2026-09-15');
        response.next({ metrics: {
                step_count: [{ date: '2026-09-14', qty: 5000 }, { date: '2026-09-15', qty: 5500 }],
                weight_body_mass: [{ date: '2026-09-15', qty: 78 }]
            } });
        expect(component.health.selectedDaySteps()).toBe(5500);
        expect(component.health.selectedStepProgress()?.level).toBe('bonus');
        expect(component.health.selectedStepProgress()?.baseline).toBe(5000);
    });
    it('clears steps, closes the modal and ignores outdated health responses on date changes', () => {
        const oldDay = new Subject<HealthDataResponse>();
        const currentDay = new Subject<HealthDataResponse>();
        getHealthData.mockReturnValueOnce(oldDay).mockReturnValueOnce(currentDay);
        component.health.loadHealthData();
        oldDay.next({ metrics: { step_count: [{ date: '2026-09-15', qty: 1000 }] } });
        component.health.stepsDialogVisible.set(true);
        component.selectedDate = new Date(2026, 8, 16);
        component.health.loadHealthData();
        expect(component.health.stepsDialogVisible()).toBe(false);
        expect(component.health.selectedDaySteps()).toBeNull();
        expect(component.health.stepsChartData()).toBeNull();
        expect(component.health.stepsLoading()).toBe(true);
        currentDay.next({ metrics: { step_count: [{ date: '2026-09-16', qty: 8000 }], apple_stand_time: [{ date: '2026-09-16', qty: 42 }], sleep_analysis: [{ date: '2026-09-16', qty: 8, core: 5, deep: 1, rem: 2 }] } });
        oldDay.next({ metrics: { step_count: [{ date: '2026-09-15', qty: 9999 }] } });
        oldDay.error(new Error('Stale failure'));
        expect(component.health.selectedDaySteps()).toBe(8000);
        expect(component.health.standRecords()[0].qty).toBe(42);
        expect(component.health.sleepRecords()[0].core).toBe(5);
        expect(component.health.stepsError()).toBe(false);
    });
    it('distinguishes an empty history from failed health loading', () => {
        const empty = new Subject<HealthDataResponse>();
        const failed = new Subject<HealthDataResponse>();
        getHealthData.mockReturnValueOnce(empty).mockReturnValueOnce(failed);
        vi.spyOn(console, 'error').mockImplementation(() => { });
        component.health.loadHealthData();
        empty.next({ metrics: {} });
        expect(component.health.selectedStepProgress()?.label).toBe('No step data');
        expect(component.health.stepsError()).toBe(false);
        component.health.loadHealthData();
        failed.error(new Error('Unavailable'));
        expect(component.health.stepsLoading()).toBe(false);
        expect(component.health.stepsError()).toBe(true);
        expect(component.health.selectedStepProgress()).toBeNull();
    });
    it('clears location data while keeping the modal open on date changes, ignoring stale responses', () => {
        const oldDay = new Subject<LocationHistoryEntry[]>();
        const newDay = new Subject<LocationHistoryEntry[]>();
        getLocations.mockReturnValueOnce(oldDay).mockReturnValueOnce(newDay);
        component.location.loadDayViewData();
        const point = { id: 1, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z' };
        oldDay.next([point]);
        component.location.locationDialogVisible.set(true);
        component.selectedDate = new Date(2026, 8, 16);
        component.location.loadDayViewData();
        expect(getLocations).toHaveBeenLastCalledWith('2026-09-16', '2026-09-16');
        expect(component.location.locationDialogVisible()).toBe(true);
        expect(component.location.dayViewDataFull()).toEqual([]);
        expect(component.location.locationsLoading()).toBe(true);
        newDay.next([]);
        oldDay.next([point]);
        oldDay.error(new Error('Stale failure'));
        expect(component.location.dayViewDataFull()).toEqual([]);
        expect(component.location.locationsError()).toBe(false);
        expect(component.location.zoom()).toBe(4);
    });
    it('shows location load failures separately from an empty day', () => {
        const failed = new Subject<LocationHistoryEntry[]>();
        getLocations.mockReturnValue(failed);
        component.location.loadDayViewData();
        failed.error(new Error('Unavailable'));
        expect(component.location.locationsError()).toBe(true);
        expect(component.location.locationsLoading()).toBe(false);
    });
    it('centres a single location at a useful zoom without fitting an empty bounds', () => {
        const map = { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn() };
        component.location.dayViewDataFull.set([{ id: 1, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z' }]);
        component.location.onExpandedMapInitialized(map as unknown as google.maps.Map);
        expect(map.setCenter).toHaveBeenCalledWith({ lat: 47.4, lng: 8.5 });
        expect(map.setZoom).toHaveBeenCalledWith(15);
        expect(map.fitBounds).not.toHaveBeenCalled();
    });
    function point(id = 1): LocationHistoryEntry {
        return { id, latitude: 47.4, longitude: 8.5, timestamp: '2026-09-15T08:00:00Z', altitude: 400, note: 'Original' };
    }
    function selectPoint() {
        const entry = point();
        component.location.dayViewDataFull.set([entry]);
        component.location.selectedLocationEntries.set([entry]);
        return entry;
    }
    it('creates at the actual map centre and local noon without mutating the selected date', () => {
        const originalDate = component.selectedDate.getTime();
        const map = { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn(), getCenter: () => ({ toJSON: () => ({ lat: 10, lng: 20 }) }) };
        component.location.onExpandedMapInitialized(map as any);
        const created = { ...point(2), latitude: 10, longitude: 20 };
        addLocation.mockReturnValue(of(created));
        getLocations.mockReturnValue(of([created]));
        component.location.locationDialogVisible.set(true);
        component.location.createLocation();
        const noon = new Date(2026, 8, 15, 12).toISOString();
        expect(addLocation).toHaveBeenCalledWith({ id: 0, latitude: 10, longitude: 20, timestamp: noon, altitude: 0 });
        expect(component.selectedDate.getTime()).toBe(originalDate);
        expect(component.location.selectedLocationEntries()).toEqual([created]);
        expect(component.location.locationDialogVisible()).toBe(true);
    });
    it('copies the selected position and timestamp, but blocks New with multiple selections', () => {
        const selected = selectPoint();
        addLocation.mockReturnValue(of(point(2)));
        getLocations.mockReturnValue(of([selected, point(2)]));
        component.location.createLocation();
        expect(addLocation).toHaveBeenCalledWith(expect.objectContaining({ latitude: selected.latitude, longitude: selected.longitude, timestamp: new Date(selected.timestamp).toISOString(), altitude: 0 }));
        component.location.selectedLocationEntries.set([selected, point(2)]);
        component.location.createLocation();
        expect(addLocation).toHaveBeenCalledOnce();
    });
    it('edits a detached draft, retaining it on failure and discarding it on cancel', () => {
        const selected = selectPoint();
        component.location.editLocation();
        const draft = component.location.locationEditDraft()!;
        draft.entry.note = 'Changed';
        draft.date = new Date(2026, 8, 15, 15);
        expect(selected.note).toBe('Original');
        expect(selected.timestamp).toBe('2026-09-15T08:00:00Z');
        updateLocation.mockReturnValue(throwError(() => new Error('Offline')));
        component.location.saveLocation();
        expect(component.location.locationEditDraft()).toBe(draft);
        expect(component.location.dayViewDataFull()).toEqual([selected]);
        expect(component.location.locationSaving()).toBe(false);
        expect(toast.add).toHaveBeenLastCalledWith(expect.objectContaining({ severity: 'error' }));
        component.location.locationEditDraft.set(null);
        expect(selected.note).toBe('Original');
    });
    it('rejects missing edit dates and removes entries moved to another day after saving', () => {
        selectPoint();
        component.location.editLocation();
        component.location.locationEditDraft()!.date = null;
        component.location.saveLocation();
        expect(updateLocation).not.toHaveBeenCalled();
        component.location.locationEditDraft()!.date = new Date(2026, 8, 16, 12);
        updateLocation.mockReturnValue(of({ ...point(), timestamp: new Date(2026, 8, 16, 12).toISOString() }));
        getLocations.mockReturnValue(of([]));
        component.location.saveLocation();
        expect(component.location.locationEditDraft()).toBeNull();
        expect(component.location.dayViewDataFull()).toEqual([]);
        expect(component.location.selectedLocationEntries()).toEqual([]);
    });
    it('deletes selected IDs only after success and blocks overlapping writes', () => {
        const entries = [point(), point(2)];
        component.location.dayViewDataFull.set(entries);
        component.location.selectedLocationEntries.set(entries);
        const response = new Subject<void>();
        deleteLocations.mockReturnValue(response);
        getLocations.mockReturnValue(of([]));
        component.location.deleteLocations();
        component.location.deleteLocations();
        expect(deleteLocations).toHaveBeenCalledExactlyOnceWith([1, 2]);
        expect(component.location.selectedLocationEntries()).toEqual(entries);
        expect(component.location.locationSaving()).toBe(true);
        response.next();
        response.complete();
        expect(component.location.selectedLocationEntries()).toEqual([]);
        expect(component.location.dayViewDataFull()).toEqual([]);
        expect(component.location.locationSaving()).toBe(false);
    });
    it('retains selections after a failed delete and supports retry', () => {
        const entry = selectPoint();
        deleteLocations.mockReturnValue(throwError(() => new Error('Offline')));
        component.location.deleteLocations();
        expect(component.location.selectedLocationEntries()).toEqual([entry]);
        expect(component.location.locationSaving()).toBe(false);
    });
    it('restores failed marker moves and rejects mobile or missing-coordinate drag events', () => {
        const entry = selectPoint();
        const restore = vi.fn();
        const marker = { marker: { setPosition: restore } } as unknown as MapMarker;
        const event = { latLng: { lat: () => 12, lng: () => 34 } } as google.maps.MapMouseEvent;
        updateLocation.mockReturnValue(throwError(() => new Error('Offline')));
        component.location.markerDragged(entry, event, marker);
        expect(updateLocation).toHaveBeenCalledWith(entry.id, { ...entry, latitude: 12, longitude: 34 });
        expect(restore).toHaveBeenCalledWith({ lat: 47.4, lng: 8.5 });
        expect(entry.latitude).toBe(47.4);
        component.location.mobileLocationView.set(true);
        expect(component.location.canDragLocations).toBe(false);
        component.location.markerDragged(entry, event, marker);
        component.location.mobileLocationView.set(false);
        component.location.markerDragged(entry, { latLng: null } as google.maps.MapMouseEvent, marker);
        expect(updateLocation).toHaveBeenCalledOnce();
    });
    it('updates the route and selection after a move without changing the viewport', () => {
        const entry = selectPoint();
        const map = { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn() };
        component.location.onExpandedMapInitialized(map as any);
        map.setCenter.mockClear();
        map.setZoom.mockClear();
        const moved = { ...entry, latitude: 12, longitude: 34, horizontalAccuracy: 0 };
        updateLocation.mockReturnValue(of(moved));
        getLocations.mockReturnValue(of([moved]));
        component.location.markerDragged(entry, { latLng: { lat: () => 12, lng: () => 34 } } as google.maps.MapMouseEvent, { marker: { setPosition: vi.fn() } } as any);
        expect(component.location.dayViewDataFull()).toEqual([moved]);
        expect(component.location.selectedLocationEntries()).toEqual([moved]);
        expect(map.setCenter).not.toHaveBeenCalled();
        expect(map.setZoom).not.toHaveBeenCalled();
    });
    it('ignores a mutation result after navigation to another day', () => {
        selectPoint();
        const response = new Subject<LocationHistoryEntry>();
        updateLocation.mockReturnValue(response);
        component.location.editLocation();
        component.location.saveLocation();
        component.selectedDate = new Date(2026, 8, 16);
        getLocations.mockReturnValue(of([]));
        component.location.loadDayViewData();
        response.next({ ...point(), note: 'Old result' });
        response.complete();
        expect(component.location.dayViewDataFull()).toEqual([]);
        expect(component.location.selectedLocationEntries()).toEqual([]);
        expect(getLocations).toHaveBeenCalledOnce();
    });
    it('locates repeatedly at the selected point and falls back to the first entry', () => {
        selectPoint();
        const map = { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn() };
        component.location.onExpandedMapInitialized(map as any);
        map.setCenter.mockClear();
        map.setZoom.mockClear();
        component.location.locateLocation();
        component.location.locateLocation();
        expect(map.setCenter).toHaveBeenCalledTimes(2);
        expect(map.setZoom).toHaveBeenLastCalledWith(16);
        component.location.selectedLocationEntries.set([]);
        component.location.locateLocation();
        expect(map.setZoom).toHaveBeenLastCalledWith(11);
        component.location.dayViewDataFull.set([]);
        component.location.locateLocation();
        expect(map.setCenter).toHaveBeenCalledTimes(3);
    });
    it('selects the nearest route point by ID and toggles it off', () => {
        const first = point();
        const second = { ...point(2), latitude: 48, longitude: 9 };
        component.location.dayViewDataFull.set([first, second]);
        const event = { latLng: { lat: () => 48.001, lng: () => 9 } } as google.maps.PolyMouseEvent;
        component.location.dayLineClick(event);
        expect(component.location.selectedLocationEntries()).toEqual([second]);
        component.location.selectedLocationEntries.set([{ ...second }]);
        component.location.dayLineClick(event);
        expect(component.location.selectedLocationEntries()).toEqual([]);
    });
    it('updates drag availability on viewport changes and removes the listener on destruction', () => {
        const listeners: ((event: MediaQueryListEvent) => void)[] = [];
        const removeEventListener = vi.fn();
        vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: (_: string, callback: any) => listeners.push(callback), removeEventListener }));
        const responsiveFixture = TestBed.createComponent(DayviewComponent);
        const responsive = responsiveFixture.componentInstance;
        expect(responsive.location.canDragLocations).toBe(true);
        responsive.location.locationEditDraft.set({ entry: point(), date: new Date() });
        listeners[0]({ matches: true } as MediaQueryListEvent);
        expect(responsive.location.canDragLocations).toBe(false);
        expect(responsive.location.locationEditDraft()).toBeNull();
        listeners[0]({ matches: false } as MediaQueryListEvent);
        expect(responsive.location.canDragLocations).toBe(true);
        responsiveFixture.destroy();
        expect(removeEventListener).toHaveBeenCalledWith('change', listeners[0]);
        vi.unstubAllGlobals();
    });
    it('updates personal counters with user context without treating groups as recipients', () => {
        const users = TestBed.inject(CurrentUserService);
        component.messages.messages.set([
            {...message(1, 1), receiver: {name: 'Group'}},
            {...message(2, 2), receiver: {name: 'Theo', identityId: 1}},
            {...message(3, 2), receiver: {name: 'Group'}}
        ]);
        expect(component.messages.sentMessageCount()).toBe(1);
        expect(component.messages.receivedMessageCount()).toBe(1);
        users.verifiedState.set({status: 'resolved', identityId: 2});
        expect(component.messages.sentMessageCount()).toBe(2);
        expect(component.messages.receivedMessageCount()).toBe(0);
        users.verifiedState.set({status: 'unlinked'});
        expect(component.messages.identityId()).toBeNull();
        expect(component.messages.messages()).toHaveLength(3);
    });
});
