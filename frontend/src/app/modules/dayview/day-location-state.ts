import { Injectable, inject, DestroyRef, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { registerRefresh } from '../../shared/refresh/refresh-coordinator';
import { localDateString } from '../../shared/dates/local-date';
import { DayViewData } from './day-view-data';
import { Observable, finalize } from 'rxjs';
import { MessageService as ToastService } from 'primeng/api';
import { MapMarker } from '@angular/google-maps';
import { LocationEditDraft } from './location-sidebar/location-sidebar.component';
import { LocationHistoryEntry, LocationService } from '../../../../generated/backend-api/thereabout';
@Injectable()
export class DayLocationState {
  private readonly day = inject(DayViewData);
  private readonly destroyRef = inject(DestroyRef);
  private readonly locationService = inject(LocationService);
  private readonly refresh = registerRefresh(() => this.loadDayViewData(true), () => this.locationSaving() || !!this.locationEditDraft());
  get selectedDate(): Date { return this.day.selectedDate(); }
  readonly dateToString = localDateString;
  center = signal({ lat: 47.3919661, lng: 8.3 });
  zoom = signal(4);
  locationDialogVisible = signal(false);
  locationsLoading = signal(false);
  locationsError = signal(false);
  private locationRequestId = 0;
  private readonly toast = inject(ToastService);
  private readonly mobileQuery = window.matchMedia?.('(max-width: 768px)');
  mobileLocationView = signal(this.mobileQuery?.matches ?? false);
  locationSaving = signal(false);
  locationEditDraft = signal<LocationEditDraft | null>(null);
  highlightedLocationEntry = signal<LocationHistoryEntry | undefined>(undefined);
  readonly blueHighlightMarker: google.maps.Symbol = {
    path: 'M0,0 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0',
    fillColor: '#3B82F6', fillOpacity: 0.6, strokeWeight: 0, scale: 2
  };
  get locationEditorBusy() {
    return this.locationSaving() || this.locationsLoading() || this.locationsError();
  }
  get canDragLocations() {
    return !this.mobileLocationView() && !this.locationEditorBusy && !this.locationEditDraft();
  }
  private expandedMap: google.maps.Map | null = null;
  private locationTrigger: HTMLButtonElement | null = null;
  openLocationDialog(event: Event) {
    this.locationTrigger = event.currentTarget as HTMLButtonElement;
    this.locationDialogVisible.set(true);
  }
  onExpandedMapInitialized(map: google.maps.Map) {
    this.expandedMap = map;
    this.fitExpandedMap();
  }
  fitExpandedMap() {
    const map = this.expandedMap;
    if (!map)
      return;
    const points = this.minifyDayViewData(this.dayViewDataFull());
    if (points.length === 0) {
      map.setCenter(this.center());
      map.setZoom(this.zoom());
    }
    else if (points.every(point => point.lat === points[0].lat && point.lng === points[0].lng)) {
      map.setCenter(points[0]);
      map.setZoom(15);
    }
    else {
      const bounds = new google.maps.LatLngBounds();
      points.forEach(point => bounds.extend(point));
      map.fitBounds(bounds, 48);
    }
  }
  onLocationDialogHidden() {
    this.expandedMap = null;
    this.locationEditDraft.set(null);
    this.highlightedLocationEntry.set(undefined);
    this.locationTrigger?.focus();
  }
  dayViewDataFull = signal<Array<LocationHistoryEntry>>([]);
  selectedLocationEntries = signal<LocationHistoryEntry[]>([]);
  loadDayViewData(preserveViewport = false, selectionIds = this.selectedLocationEntries().map(entry => entry.id)) {
    if (!this.selectedDate)
      return;
    const date = this.dateToString(this.selectedDate);
    const requestId = ++this.locationRequestId;
    if (!preserveViewport) {
      this.dayViewDataFull.set([]);
      this.selectedLocationEntries.set([]);
      selectionIds = [];
      this.locationEditDraft.set(null);
      this.center.set({ lat: 47.3919661, lng: 8.3 });
      this.zoom.set(4);
    }
    this.highlightedLocationEntry.set(undefined);
    this.locationsLoading.set(true);
    this.locationsError.set(false);
    this.locationService.getLocations(date, date).pipe(this.refresh.track('locations'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: locations => {
        if (requestId !== this.locationRequestId)
          return;
        this.dayViewDataFull.set([...locations].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp) || a.id - b.id));
        const entriesById = new Map(this.dayViewDataFull().map(entry => [entry.id, entry]));
        this.selectedLocationEntries.set(selectionIds.flatMap(id => {
          const entry = entriesById.get(id);
          return entry ? [entry] : [];
        }));
        this.locationsLoading.set(false);
        if (!preserveViewport) {
          if (locations.length > 0) {
            this.center.set({ lat: locations[0].latitude, lng: locations[0].longitude });
            this.zoom.set(12);
          }
          this.fitExpandedMap();
        }
      },
      error: () => {
        if (requestId !== this.locationRequestId)
          return;
        this.locationsLoading.set(false);
        this.locationsError.set(true);
      }
    });
  }
  locateLocation() {
    const entry = this.selectedLocationEntries()[0] ?? this.dayViewDataFull()[0];
    if (!entry || !this.expandedMap)
      return;
    this.expandedMap.setCenter({ lat: entry.latitude, lng: entry.longitude });
    this.expandedMap.setZoom(this.selectedLocationEntries().length ? 16 : 11);
  }
  createLocation() {
    if (this.mobileLocationView() || this.locationEditorBusy || this.selectedLocationEntries().length > 1)
      return;
    const selected = this.selectedLocationEntries()[0];
    const noon = new Date(this.selectedDate);
    noon.setHours(12, 0, 0, 0);
    const centre = this.expandedMap?.getCenter()?.toJSON() ?? this.center();
    const entry: LocationHistoryEntry = {
      id: 0, latitude: selected?.latitude ?? centre.lat, longitude: selected?.longitude ?? centre.lng,
      timestamp: selected ? new Date(selected.timestamp).toISOString() : noon.toISOString(), altitude: 0
    };
    this.mutateLocation(this.locationService.addLocation(entry), 'created', created => {
      this.dayViewDataFull.set([...this.dayViewDataFull(), created]);
      return [created.id];
    });
  }
  editLocation() {
    if (this.mobileLocationView() || this.locationEditorBusy || this.selectedLocationEntries().length !== 1)
      return;
    const entry = this.selectedLocationEntries()[0];
    this.locationEditDraft.set({ entry: { ...entry }, date: new Date(entry.timestamp) });
  }
  saveLocation() {
    const draft = this.locationEditDraft();
    if (this.mobileLocationView() || this.locationEditorBusy || !draft?.date || !Number.isFinite(draft.date.getTime()))
      return;
    const entry = { ...draft.entry, timestamp: draft.date.toISOString() };
    this.mutateLocation(this.locationService.updateLocation(entry.id, entry), 'updated', saved => {
      this.applySavedLocation(saved);
      this.locationEditDraft.set(null);
      return [saved.id];
    });
  }
  deleteLocations() {
    if (this.mobileLocationView() || this.locationEditorBusy || !this.selectedLocationEntries().length)
      return;
    const ids = this.selectedLocationEntries().map(entry => entry.id);
    this.mutateLocation(this.locationService.deleteLocations(ids), 'deleted', () => {
      this.dayViewDataFull.set(this.dayViewDataFull().filter(entry => !ids.includes(entry.id)));
      this.selectedLocationEntries.set([]);
      return [];
    });
  }
  markerDragged(entry: LocationHistoryEntry, event: google.maps.MapMouseEvent, marker: MapMarker) {
    const restore = () => marker.marker?.setPosition({ lat: entry.latitude, lng: entry.longitude });
    if (!this.canDragLocations || !event.latLng) {
      restore();
      return;
    }
    const moved = { ...entry, latitude: event.latLng.lat(), longitude: event.latLng.lng() };
    this.mutateLocation(this.locationService.updateLocation(entry.id, moved), 'updated', saved => {
      this.applySavedLocation(saved);
      return this.selectedLocationEntries().map(selected => selected.id);
    }, restore);
  }
  private applySavedLocation(saved: LocationHistoryEntry) {
    this.dayViewDataFull.set(this.dayViewDataFull().map(entry => entry.id === saved.id ? saved : entry));
    this.selectedLocationEntries.set(this.selectedLocationEntries().map(entry => entry.id === saved.id ? saved : entry));
  }
  private mutateLocation<T>(request: Observable<T>, action: string, apply: (result: T) => number[], rollback?: () => void) {
    if (this.locationEditorBusy)
      return;
    const requestId = this.locationRequestId;
    this.locationSaving.set(true);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.locationSaving.set(false))).subscribe({
      next: result => {
        this.toast.add({ severity: 'success', summary: `Location ${action}` });
        // A write may finish after browser navigation. Never apply its result to a different day.
        if (requestId !== this.locationRequestId)
          return;
        const ids = apply(result);
        this.loadDayViewData(true, ids);
      },
      error: () => {
        rollback?.();
        this.toast.add({ severity: 'error', summary: `Location could not be ${action}`, detail: 'Please try again.' });
      }
    });
  }
  dayLineClick(event: google.maps.PolyMouseEvent) {
    if (!event.latLng || this.mobileLocationView() || this.locationEditorBusy)
      return;
    const lat = event.latLng.lat() * Math.PI / 180;
    const lng = event.latLng.lng() * Math.PI / 180;
    // Compare spherical distances without depending on the optional Maps geometry library.
    const distance = (entry: LocationHistoryEntry) => {
      const pointLat = entry.latitude * Math.PI / 180;
      const pointLng = entry.longitude * Math.PI / 180;
      return Math.sin((pointLat - lat) / 2) ** 2 + Math.cos(lat) * Math.cos(pointLat) * Math.sin((pointLng - lng) / 2) ** 2;
    };
    const closest = this.dayViewDataFull().reduce<LocationHistoryEntry | undefined>((best, entry) => !best || distance(entry) < distance(best) ? entry : best, undefined);
    if (closest)
      this.selectedLocationEntries.set(this.selectedLocationEntries().length === 1 && this.selectedLocationEntries()[0].id === closest.id ? [] : [closest]);
  }
  minifyDayViewData(data: Array<LocationHistoryEntry>) {
    return data.map(location => {
      return { lat: location.latitude, lng: location.longitude };
    });
  }
}
