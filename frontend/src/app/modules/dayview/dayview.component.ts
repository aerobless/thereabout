import {Component, OnInit, ChangeDetectionStrategy, DestroyRef, inject} from '@angular/core';
import {Observable, finalize} from 'rxjs';
import {MessageService as ToastService} from 'primeng/api';
import {LocationEditDraft, LocationSidebarComponent} from './location-sidebar/location-sidebar.component';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from "primeng/button";
import {Router, ActivatedRoute, RouterModule} from "@angular/router";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {TooltipModule} from "primeng/tooltip";
import {DatePickerModule} from "primeng/datepicker";
import {FormsModule} from "@angular/forms";
import {PanelModule} from "primeng/panel";
import {CardModule} from "primeng/card";
import {ChartModule} from "primeng/chart";
import {TableModule} from "primeng/table";
import { DatePipe, NgTemplateOutlet } from "@angular/common";
import {
    GoogleMap,
    MapPolyline,
    MapMarker
} from "@angular/google-maps";
import {
    HealthService,
    LocationHistoryEntry,
    LocationService,
    Message,
    MessageService as MessageApiService,
    WorkoutSummary
} from "../../../../generated/backend-api/thereabout";

import {ChartData, ChartOptions} from 'chart.js';
import {dailyStepTotals, shiftDay, stepHistory, StepProgress} from './steps-progress';

import {WeightCardComponent} from './weight/weight-card.component';
import {HeartRateCardComponent} from './heart/heart-rate-card.component';
import {HrvCardComponent} from './heart/hrv-card.component';

const THEO_IDENTITY_ID = 1;

@Component({
    selector: 'app-dayview',
    imports: [
    WeightCardComponent,
    HeartRateCardComponent,
    HrvCardComponent,
    LocationSidebarComponent,
    ButtonModule,
    RouterModule,
    ToolbarComponent,
    TooltipModule,
    DatePickerModule,
    FormsModule,
    PanelModule,
    CardModule,
    ChartModule,
    TableModule,
    DialogModule,
    DatePipe,
    NgTemplateOutlet,
    GoogleMap,
    MapPolyline,
    MapMarker
],
    templateUrl: './dayview.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./dayview.component.scss', './dayview-map.scss']
})
export class DayviewComponent implements OnInit {

  selectedDate: Date = new Date();
  private hasLoadedInitialData = false;
  
  // Map configuration
  center = {lat: 47.3919661, lng: 8.3};
  zoom = 4;
  locationDialogVisible = false;
  locationsLoading = false;
  locationsError = false;
  private locationRequestId = 0;
  private readonly toast = inject(ToastService);
  private readonly mobileQuery = window.matchMedia?.('(max-width: 768px)');
  mobileLocationView = this.mobileQuery?.matches ?? false;
  locationSaving = false;
  locationEditDraft: LocationEditDraft | null = null;
  highlightedLocationEntry: LocationHistoryEntry | undefined;
  readonly blueHighlightMarker: google.maps.Symbol = {
    path: 'M0,0 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0',
    fillColor: '#3B82F6', fillOpacity: 0.6, strokeWeight: 0, scale: 2
  };

  get locationEditorBusy() {
    return this.locationSaving || this.locationsLoading || this.locationsError;
  }

  get canDragLocations() {
    return !this.mobileLocationView && !this.locationEditorBusy && !this.locationEditDraft;
  }

  private expandedMap: google.maps.Map | null = null;
  private locationTrigger: HTMLButtonElement | null = null;

  openLocationDialog(event: Event) {
    this.locationTrigger = event.currentTarget as HTMLButtonElement;
    this.locationDialogVisible = true;
  }

  onExpandedMapInitialized(map: google.maps.Map) {
    this.expandedMap = map;
    this.fitExpandedMap();
  }

  fitExpandedMap() {
    const map = this.expandedMap;
    if (!map) return;
    const points = this.minifyDayViewData(this.dayViewDataFull);
    if (points.length === 0) {
      map.setCenter(this.center);
      map.setZoom(this.zoom);
    } else if (points.every(point => point.lat === points[0].lat && point.lng === points[0].lng)) {
      map.setCenter(points[0]);
      map.setZoom(15);
    } else {
      const bounds = new google.maps.LatLngBounds();
      points.forEach(point => bounds.extend(point));
      map.fitBounds(bounds, 48);
    }
  }

  onLocationDialogHidden() {
    this.expandedMap = null;
    this.locationEditDraft = null;
    this.highlightedLocationEntry = undefined;
    this.locationTrigger?.focus();
  }
  
  // Day view data
  dayViewDataFull: Array<LocationHistoryEntry> = [];
  selectedLocationEntries: LocationHistoryEntry[] = [];

  // Health data
  workouts: WorkoutSummary[] = [];
  stepsDialogVisible = false;
  stepsLoading = false;
  stepsError = false;
  stepsHistory: StepProgress[] = [];
  selectedStepProgress: StepProgress | null = null;
  stepsChartData: ChartData<'bar' | 'line'> | null = null;
  stepsChartOptions: ChartOptions<'bar' | 'line'> = {};
  private healthRequestId = 0;
  private healthDate: string | null = null;

  get isSelectedDayToday(): boolean {
    return this.dateToString(this.selectedDate) === this.dateToString(new Date());
  }

  get hasStepHistory(): boolean {
    return this.stepsHistory.some(day => day.steps !== null);
  }

  formatStepPercentage(value: number | null): string {
    return value === null ? '--' : `${(Math.floor(value * 10) / 10).toLocaleString()}%`;
  }

  stepDifference(progress: StepProgress): string {
    if (progress.percentage === null || progress.steps === null || progress.baseline === null) return 'Baseline unavailable';
    const difference = progress.steps - progress.baseline;
    if (difference === 0) return 'At your baseline';
    return `${this.formatSteps(Math.ceil(Math.abs(difference)))} steps ${difference > 0 ? 'above' : 'below'} baseline`;
  }

  private updateStepsChart() {
    const colors = {below: '#efb9b5', almost: '#ecd08c', reached: '#b7dcc0', bonus: '#9ed3b1', exceptional: '#85c9bd'};
    const history = this.stepsHistory;
    this.stepsChartData = {
      labels: history.map(day => day.date),
      datasets: [
        {type: 'line', label: 'Previous 30-day average', data: history.map(day => day.baseline),
          borderColor: '#6486b0', backgroundColor: '#6486b0', borderWidth: 2, pointRadius: 0,
          pointHitRadius: 12, tension: 0.2, spanGaps: false, order: 0},
        {type: 'bar', label: 'Daily steps', data: history.map(day => day.steps),
          backgroundColor: history.map(day => day.level ? colors[day.level] : '#94a3b8'),
          borderColor: history.map((_, i) => i === 29 ? '#0f172a' : 'transparent'),
          borderWidth: history.map((_, i) => i === 29 ? 2 : 0), borderRadius: 3, order: 1}
      ]
    };
    this.stepsChartOptions = {
      responsive: true, maintainAspectRatio: false,
      interaction: {mode: 'index', intersect: false},
      plugins: {
        legend: {position: 'bottom'},
        tooltip: {callbacks: {
          title: items => history[items[0].dataIndex].date,
          label: item => {
            const day = history[item.dataIndex];
            return item.datasetIndex === 0
              ? `Baseline: ${day.baseline === null ? 'Unavailable' : this.formatSteps(day.baseline) + ' steps'}`
              : `Steps: ${day.steps === null ? 'No data' : this.formatSteps(day.steps)}`;
          },
          afterBody: items => {
            const day = history[items[0].dataIndex];
            return [`${this.formatStepPercentage(day.percentage)} · ${day.label}`, `${day.coverage}/30 days recorded`];
          }
        }}
      },
      scales: {
        x: {grid: {display: false}, ticks: {maxTicksLimit: 7, callback: (_, index) => {
          const date = history[index].date;
          return `${date.slice(8)}.${date.slice(5, 7)}`;
        }}},
        y: {beginAtZero: true, title: {display: true, text: 'Steps'}}
      }
    };
  }

  // Messages data
  messages: Message[] = [];
  messagesDialogVisible = false;
  messagesLoading = false;
  messagesError = false;
  private messageRequestId = 0;
  private readonly destroyRef = inject(DestroyRef);

  get sentMessageCount(): number {
    return this.messages.filter(message => message.sender?.identityId === THEO_IDENTITY_ID).length;
  }

  get receivedMessageCount(): number {
    return this.messages.length - this.sentMessageCount;
  }
  
  // Energy data
  selectedDayActiveEnergy: number | null = null;
  selectedDayBasalEnergy: number | null = null;
  energyUnits: string = 'kcal';

  // Daily stats (above Workouts)
  selectedDaySteps: number | null = null;
  selectedDayStandMinutes: number | null = null;
  selectedDayDistanceKm: number | null = null;
  selectedDaySleepHours: number | null = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private locationService: LocationService,
    private healthService: HealthService,
    private messageApiService: MessageApiService
  ) {
    const onViewportChange = (event: MediaQueryListEvent) => {
      this.mobileLocationView = event.matches;
      this.highlightedLocationEntry = undefined;
      if (event.matches) this.locationEditDraft = null;
    };
    this.mobileQuery?.addEventListener('change', onViewportChange);
    this.destroyRef.onDestroy(() => this.mobileQuery?.removeEventListener('change', onViewportChange));
  }

  goToPreviousDay() {
    const previousDay = new Date(this.selectedDate);
    previousDay.setDate(previousDay.getDate() - 1);
    this.setDateAndUpdateUrl(previousDay);
  }

  goToNextDay() {
    const nextDay = new Date(this.selectedDate);
    nextDay.setDate(nextDay.getDate() + 1);
    this.setDateAndUpdateUrl(nextDay);
  }

  goToToday() {
    const today = new Date();
    this.setDateAndUpdateUrl(today);
  }

  onDateChange() {
    this.updateUrl();
    this.loadAllData();
  }

  setDateAndUpdateUrl(date: Date) {
    if (!date || !Number.isFinite(date.getTime())) return;
    this.selectedDate = new Date(date);
    this.updateUrl();
    this.loadAllData();
  }

  private updateUrl(skipHistory = false) {
    const dateStr = this.dateToString(this.selectedDate);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { date: dateStr },
      queryParamsHandling: 'merge',
      replaceUrl: skipHistory
    });
  }

  ngOnInit() {
    // Read date from URL query params, default to today if not provided
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const dateParam = params['date'];
      if (dateParam) {
        const parsedDate = /^\d{4}-\d{2}-\d{2}$/.test(dateParam) ? new Date(`${dateParam}T00:00:00`) : new Date(NaN);
        if (!isNaN(parsedDate.getTime())) {
          // Only update if the date actually changed (to avoid reloading when we update URL ourselves)
          const newDateStr = this.dateToString(parsedDate);
          const currentDateStr = this.dateToString(this.selectedDate);
          if (!this.hasLoadedInitialData || newDateStr !== currentDateStr) {
            this.selectedDate = parsedDate;
            this.loadAllData();
          }
          this.hasLoadedInitialData = true;
        }
      } else {
        // If no date in URL, update URL with current date (use replaceUrl to avoid history entry)
        this.updateUrl(true);
        // Load data for default date (today) since updateUrl doesn't trigger reload
        this.loadAllData();
        this.hasLoadedInitialData = true;
      }
    });
  }

  private loadAllData() {
    this.loadDayViewData();
    this.loadHealthData();
    this.loadMessages();
  }

  loadDayViewData(preserveViewport = false, selectionIds = this.selectedLocationEntries.map(entry => entry.id)) {
    if (!this.selectedDate) return;
    const date = this.dateToString(this.selectedDate);
    const requestId = ++this.locationRequestId;
    if (!preserveViewport) {
      this.dayViewDataFull = [];
      this.selectedLocationEntries = [];
      selectionIds = [];
      this.locationEditDraft = null;
      this.center = {lat: 47.3919661, lng: 8.3};
      this.zoom = 4;
    }
    this.highlightedLocationEntry = undefined;
    this.locationsLoading = true;
    this.locationsError = false;
    this.locationService.getLocations(date, date).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: locations => {
        if (requestId !== this.locationRequestId) return;
        this.dayViewDataFull = [...locations].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp) || a.id - b.id);
        const entriesById = new Map(this.dayViewDataFull.map(entry => [entry.id, entry]));
        this.selectedLocationEntries = selectionIds.flatMap(id => {
          const entry = entriesById.get(id);
          return entry ? [entry] : [];
        });
        this.locationsLoading = false;
        if (!preserveViewport) {
          if (locations.length > 0) {
            this.center = {lat: locations[0].latitude, lng: locations[0].longitude};
            this.zoom = 12;
          }
          this.fitExpandedMap();
        }
      },
      error: () => {
        if (requestId !== this.locationRequestId) return;
        this.locationsLoading = false;
        this.locationsError = true;
      }
    });
  }

  locateLocation() {
    const entry = this.selectedLocationEntries[0] ?? this.dayViewDataFull[0];
    if (!entry || !this.expandedMap) return;
    this.expandedMap.setCenter({lat: entry.latitude, lng: entry.longitude});
    this.expandedMap.setZoom(this.selectedLocationEntries.length ? 16 : 11);
  }

  openLocationPhotos() {
    window.open(`https://photos.google.com/search/${this.dateToString(this.selectedDate)}`, '_blank', 'noopener');
  }

  createLocation() {
    if (this.mobileLocationView || this.locationEditorBusy || this.selectedLocationEntries.length > 1) return;
    const selected = this.selectedLocationEntries[0];
    const noon = new Date(this.selectedDate);
    noon.setHours(12, 0, 0, 0);
    const centre = this.expandedMap?.getCenter()?.toJSON() ?? this.center;
    const entry: LocationHistoryEntry = {
      id: 0, latitude: selected?.latitude ?? centre.lat, longitude: selected?.longitude ?? centre.lng,
      timestamp: selected ? new Date(selected.timestamp).toISOString() : noon.toISOString(), altitude: 0
    };
    this.mutateLocation(this.locationService.addLocation(entry), 'created', created => {
      this.dayViewDataFull = [...this.dayViewDataFull, created];
      return [created.id];
    });
  }

  editLocation() {
    if (this.mobileLocationView || this.locationEditorBusy || this.selectedLocationEntries.length !== 1) return;
    const entry = this.selectedLocationEntries[0];
    this.locationEditDraft = {entry: {...entry}, date: new Date(entry.timestamp)};
  }

  saveLocation() {
    const draft = this.locationEditDraft;
    if (this.mobileLocationView || this.locationEditorBusy || !draft?.date || !Number.isFinite(draft.date.getTime())) return;
    const entry = {...draft.entry, timestamp: draft.date.toISOString()};
    this.mutateLocation(this.locationService.updateLocation(entry.id, entry), 'updated', saved => {
      this.applySavedLocation(saved);
      this.locationEditDraft = null;
      return [saved.id];
    });
  }

  deleteLocations() {
    if (this.mobileLocationView || this.locationEditorBusy || !this.selectedLocationEntries.length) return;
    const ids = this.selectedLocationEntries.map(entry => entry.id);
    this.mutateLocation(this.locationService.deleteLocations(ids), 'deleted', () => {
      this.dayViewDataFull = this.dayViewDataFull.filter(entry => !ids.includes(entry.id));
      this.selectedLocationEntries = [];
      return [];
    });
  }

  markerDragged(entry: LocationHistoryEntry, event: google.maps.MapMouseEvent, marker: MapMarker) {
    const restore = () => marker.marker?.setPosition({lat: entry.latitude, lng: entry.longitude});
    if (!this.canDragLocations || !event.latLng) { restore(); return; }
    const moved = {...entry, latitude: event.latLng.lat(), longitude: event.latLng.lng()};
    this.mutateLocation(this.locationService.updateLocation(entry.id, moved), 'updated', saved => {
      this.applySavedLocation(saved);
      return this.selectedLocationEntries.map(selected => selected.id);
    }, restore);
  }

  private applySavedLocation(saved: LocationHistoryEntry) {
    this.dayViewDataFull = this.dayViewDataFull.map(entry => entry.id === saved.id ? saved : entry);
    this.selectedLocationEntries = this.selectedLocationEntries.map(entry => entry.id === saved.id ? saved : entry);
  }

  private mutateLocation<T>(request: Observable<T>, action: string, apply: (result: T) => number[], rollback?: () => void) {
    if (this.locationEditorBusy) return;
    const requestId = this.locationRequestId;
    this.locationSaving = true;
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.locationSaving = false)).subscribe({
      next: result => {
        this.toast.add({severity: 'success', summary: `Location ${action}`});
        // A write may finish after browser navigation. Never apply its result to a different day.
        if (requestId !== this.locationRequestId) return;
        const ids = apply(result);
        this.loadDayViewData(true, ids);
      },
      error: () => {
        rollback?.();
        this.toast.add({severity: 'error', summary: `Location could not be ${action}`, detail: 'Please try again.'});
      }
    });
  }

  dayLineClick(event: google.maps.PolyMouseEvent) {
    if (!event.latLng || this.mobileLocationView || this.locationEditorBusy) return;
    const lat = event.latLng.lat() * Math.PI / 180;
    const lng = event.latLng.lng() * Math.PI / 180;
    // Compare spherical distances without depending on the optional Maps geometry library.
    const distance = (entry: LocationHistoryEntry) => {
      const pointLat = entry.latitude * Math.PI / 180;
      const pointLng = entry.longitude * Math.PI / 180;
      return Math.sin((pointLat - lat) / 2) ** 2 + Math.cos(lat) * Math.cos(pointLat) * Math.sin((pointLng - lng) / 2) ** 2;
    };
    const closest = this.dayViewDataFull.reduce<LocationHistoryEntry | undefined>((best, entry) =>
      !best || distance(entry) < distance(best) ? entry : best, undefined);
    if (closest) this.selectedLocationEntries = this.selectedLocationEntries.length === 1 && this.selectedLocationEntries[0].id === closest.id ? [] : [closest];
  }

  dateToString(date: Date) {
    const year = date.getFullYear().toString().padStart(4, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');

    return `${year}-${month}-${day}`;
  }

  minifyDayViewData(data: Array<LocationHistoryEntry>) {
    return data.map(location => {
      return {lat: location.latitude, lng: location.longitude}
    });
  }

  loadHealthData() {
    if (!this.selectedDate) return;

    const selectedDateStr = this.dateToString(this.selectedDate);
    const requestId = ++this.healthRequestId;
    if (this.healthDate !== selectedDateStr) this.stepsDialogVisible = false;
    this.healthDate = selectedDateStr;
    this.stepsLoading = true;
    this.stepsError = false;
    this.stepsHistory = [];
    this.selectedStepProgress = null;
    this.selectedDaySteps = null;
    this.selectedDayDistanceKm = null;
    this.stepsChartData = null;

    this.healthService.getHealthDataByDateRange(shiftDay(selectedDateStr, -59), selectedDateStr)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        if (requestId !== this.healthRequestId) return;
        this.stepsLoading = false;
        this.stepsHistory = stepHistory(selectedDateStr, dailyStepTotals(response.metrics?.['step_count'] ?? []));
        this.selectedStepProgress = this.stepsHistory[29];
        this.selectedDaySteps = this.selectedStepProgress.steps;
        this.updateStepsChart();
        // Extract energy metrics
        const activeEnergyMetrics = response.metrics?.['active_energy'] || [];
        const basalEnergyMetrics = response.metrics?.['basal_energy_burned'] || [];

        // Find energy for selected day
        const selectedDayActiveEnergyMetric = activeEnergyMetrics.find((m: any) => m.date === selectedDateStr);
        const selectedDayBasalEnergyMetric = basalEnergyMetrics.find((m: any) => m.date === selectedDateStr);
        
        this.selectedDayActiveEnergy = selectedDayActiveEnergyMetric?.qty != null 
          ? Number(selectedDayActiveEnergyMetric.qty) 
          : null;
        this.selectedDayBasalEnergy = selectedDayBasalEnergyMetric?.qty != null 
          ? Number(selectedDayBasalEnergyMetric.qty) 
          : null;
        
        // Extract units (use from active energy if available, otherwise default)
        if (selectedDayActiveEnergyMetric?.units) {
          this.energyUnits = selectedDayActiveEnergyMetric.units;
        } else if (selectedDayBasalEnergyMetric?.units) {
          this.energyUnits = selectedDayBasalEnergyMetric.units;
        }

        // Extract and filter workouts for selected day
        const allWorkouts = response.workouts || [];
        this.workouts = allWorkouts
          .filter(workout => {
            if (!workout.start) return false;
            const workoutDate = new Date(workout.start);
            const workoutDateStr = this.dateToString(workoutDate);
            return workoutDateStr === selectedDateStr;
          })
          .sort((a, b) => {
            if (!a.start || !b.start) return 0;
            return new Date(a.start).getTime() - new Date(b.start).getTime();
          });

        // Daily stats for selected day
        const standMetrics = response.metrics?.['apple_stand_time'] || [];
        const distanceMetrics = response.metrics?.['walking_running_distance'] || [];
        const sleepMetrics = response.metrics?.['sleep_analysis'] || [];
        const standForDay = standMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const distanceForDay = distanceMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const sleepForDay = sleepMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        this.selectedDayStandMinutes = standForDay?.qty != null ? Number(standForDay.qty) : null;
        this.selectedDayDistanceKm = distanceForDay?.qty != null ? Number(distanceForDay.qty) : null;
        this.selectedDaySleepHours = sleepForDay?.qty != null ? Number(sleepForDay.qty) : null;

      },
      error: (error) => {
        if (requestId !== this.healthRequestId) return;
        this.stepsLoading = false;
        this.stepsError = true;
        console.error('Error loading health data:', error);
        this.selectedDayActiveEnergy = null;
        this.selectedDayBasalEnergy = null;
        this.workouts = [];
        this.selectedDaySteps = null;
        this.selectedDayStandMinutes = null;
        this.selectedDayDistanceKm = null;
        this.selectedDaySleepHours = null;
      }
    });
  }

  loadMessages() {
    if (!this.selectedDate) return;
    const dateStr = this.dateToString(this.selectedDate);
    const requestId = ++this.messageRequestId;
    this.messagesDialogVisible = false;
    this.messages = [];
    this.messagesLoading = true;
    this.messagesError = false;
    this.messageApiService.getMessages(dateStr).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (messages) => {
        if (requestId !== this.messageRequestId) return;
        this.messages = [...messages].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));
        this.messagesLoading = false;
      },
      error: (error) => {
        if (requestId !== this.messageRequestId) return;
        console.error('Error loading messages:', error);
        this.messages = [];
        this.messagesLoading = false;
        this.messagesError = true;
      }
    });
  }

  formatMessageTime(timestamp: string | undefined): string {
    if (!timestamp) return '--';
    return new Date(timestamp).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  formatWorkoutTime(start: string | undefined): string {
    if (!start) return '--';
    return new Date(start).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  formatDuration(seconds: number | undefined): string {
    if (!seconds) return '--';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    
    if (hours > 0 && minutes > 0) {
      return `${hours}h ${minutes}m`;
    } else if (hours > 0) {
      return `${hours}h`;
    } else {
      return `${minutes}m`;
    }
  }

  getLocationSymbol(location: string | undefined): string {
    if (!location) return '--';
    return location === 'Outdoor' ? '🌳' : '🏠';
  }

  formatEnergy(energy: number | undefined, units: string | undefined): string {
    if (energy === undefined || energy === null) return '--';
    const unitsStr = units || '';
    return `${Math.round(energy)} ${unitsStr}`.trim();
  }

  formatDistance(distance: number | undefined, units: string | undefined): string {
    if (distance === undefined || distance === null || !units) return '--';
    return `${distance.toFixed(2)} ${units}`;
  }

  formatDailyEnergy(energy: number | null): string {
    if (energy === null || energy === undefined) return '--';
    return Math.round(energy).toString();
  }

  formatSteps(steps: number | null): string {
    if (steps === null || steps === undefined) return '--';
    return Math.round(steps).toLocaleString();
  }

  formatDistanceKm(km: number | null): string {
    if (km === null || km === undefined) return '--';
    return km.toFixed(2);
  }

  formatStandMinutes(min: number | null): string {
    if (min === null || min === undefined) return '--';
    return Math.round(min).toString();
  }

  formatSleepHours(hours: number | null): string {
    if (hours === null || hours === undefined) return '--';
    const h = Math.floor(hours);
    const m = Math.round((hours - h) * 60);
    return m === 0 ? `${h} hr` : `${h}h ${m}m`;
  }
}
