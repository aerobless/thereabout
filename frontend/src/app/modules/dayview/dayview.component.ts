import {Component, OnInit, ChangeDetectionStrategy, DestroyRef, inject} from '@angular/core';
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
import { DatePipe } from "@angular/common";
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

const THEO_IDENTITY_ID = 1;

@Component({
    selector: 'app-dayview',
    imports: [
    WeightCardComponent,
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
  selectedDayRestingHeartRate: number | null = null;
  selectedDayStandMinutes: number | null = null;
  selectedDayDistanceKm: number | null = null;
  selectedDayHrvMs: number | null = null;
  selectedDaySleepHours: number | null = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private locationService: LocationService,
    private healthService: HealthService,
    private messageApiService: MessageApiService
  ) {
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
    // Handle date change - can be extended with additional logic
    console.log('Date changed to:', this.selectedDate);
    this.updateUrl();
    this.loadAllData();
  }

  private setDateAndUpdateUrl(date: Date) {
    this.selectedDate = date;
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
    this.route.queryParams.subscribe(params => {
      const dateParam = params['date'];
      if (dateParam) {
        const parsedDate = new Date(dateParam);
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

  loadDayViewData() {
    if (!this.selectedDate) return;
    const date = this.dateToString(this.selectedDate);
    const requestId = ++this.locationRequestId;
    this.locationDialogVisible = false;
    this.dayViewDataFull = [];
    this.selectedLocationEntries = [];
    this.locationsLoading = true;
    this.locationsError = false;
    this.center = {lat: 47.3919661, lng: 8.3};
    this.zoom = 4;
    this.locationService.getLocations(date, date).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: locations => {
        if (requestId !== this.locationRequestId) return;
        this.dayViewDataFull = locations;
        this.locationsLoading = false;
        if (locations.length > 0) {
          this.center = {lat: locations[0].latitude, lng: locations[0].longitude};
          this.zoom = 12;
        }
        this.fitExpandedMap();
      },
      error: () => {
        if (requestId !== this.locationRequestId) return;
        this.locationsLoading = false;
        this.locationsError = true;
      }
    });
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
        const restingHrMetrics = response.metrics?.['resting_heart_rate'] || [];
        const hrvMetrics = response.metrics?.['heart_rate_variability'] || [];
        const standMetrics = response.metrics?.['apple_stand_time'] || [];
        const distanceMetrics = response.metrics?.['walking_running_distance'] || [];
        const sleepMetrics = response.metrics?.['sleep_analysis'] || [];
        const restingHrForDay = restingHrMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const hrvForDay = hrvMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const standForDay = standMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const distanceForDay = distanceMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        const sleepForDay = sleepMetrics.find((m: { date?: string }) => m.date === selectedDateStr);
        this.selectedDayRestingHeartRate = restingHrForDay?.qty != null ? Number(restingHrForDay.qty) : null;
        this.selectedDayHrvMs = hrvForDay?.qty != null ? Number(hrvForDay.qty) : null;
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
        this.selectedDayRestingHeartRate = null;
        this.selectedDayHrvMs = null;
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
