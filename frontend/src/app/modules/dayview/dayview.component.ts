import {Component, OnInit} from '@angular/core';
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
    MapPolyline
} from "@angular/google-maps";
import {
    HealthService,
    LocationHistoryEntry,
    LocationService,
    Message,
    MessageService as MessageApiService,
    WorkoutSummary
} from "../../../../generated/backend-api/thereabout";

@Component({
    selector: 'app-dayview',
    imports: [
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
    DatePipe,
    GoogleMap,
    MapPolyline
],
    templateUrl: './dayview.component.html',
    styleUrl: './dayview.component.scss'
})
export class DayviewComponent implements OnInit {

  selectedDate: Date = new Date();
  private hasLoadedInitialData = false;
  
  // Map configuration
  center = {lat: 47.3919661, lng: 8.3};
  zoom = 4;
  
  // Day view data
  dayViewDataFull: Array<LocationHistoryEntry> = [];
  selectedLocationEntries: LocationHistoryEntry[] = [];

  // Health data
  weightData: { date: string, value: number }[] = [];
  selectedDayWeight: number | null = null;
  trendRange: '7d' | '30d' = '7d';
  chartData: any;
  chartOptions: any;
  workouts: WorkoutSummary[] = [];

  // Messages data
  messages: Message[] = [];
  
  // Energy data
  selectedDayActiveEnergy: number | null = null;
  selectedDayBasalEnergy: number | null = null;
  energyUnits: string = 'kcal';

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
    this.setupChart();
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
    this.locationService.getLocations(this.dateToString(this.selectedDate), this.dateToString(this.selectedDate)).subscribe(locations => {
      this.dayViewDataFull = locations;
      this.selectedLocationEntries = [];
      
      // Center map on the first location if available
      if (locations.length > 0) {
        this.center = {lat: locations[0].latitude, lng: locations[0].longitude};
        this.zoom = 12;
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

    // Calculate date range based on trendRange
    const selectedDateStr = this.dateToString(this.selectedDate);
    const selectedDateObj = new Date(this.selectedDate);
    const daysBack = this.trendRange === '7d' ? 7 : 30;
    const fromDate = new Date(selectedDateObj);
    fromDate.setDate(fromDate.getDate() - daysBack);
    const fromDateStr = this.dateToString(fromDate);

    this.healthService.getHealthDataByDateRange(fromDateStr, selectedDateStr).subscribe({
      next: (response) => {
        // Extract weight metric data (backend uses weight_body_mass)
        const weightMetrics =
          response.metrics?.['weight_body_mass'] ||
          response.metrics?.['weight'] ||
          response.metrics?.['body_mass'] ||
          [];
        
        // Prepare chart data
        this.weightData = weightMetrics
          .filter(m => m.qty != null)
          .map(m => ({
            date: m.date || '',
            value: m.qty || 0
          }))
          .sort((a, b) => a.date.localeCompare(b.date));

        // Find weight for selected day
        const selectedDateStr = this.dateToString(this.selectedDate);
        const selectedDayMetric = weightMetrics.find(m => m.date === selectedDateStr);
        this.selectedDayWeight = selectedDayMetric?.qty || null;

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

        // Update chart
        this.updateChart();
      },
      error: (error) => {
        console.error('Error loading health data:', error);
        this.weightData = [];
        this.selectedDayWeight = null;
        this.selectedDayActiveEnergy = null;
        this.selectedDayBasalEnergy = null;
        this.workouts = [];
        this.updateChart();
      }
    });
  }

  onTrendRangeChange(range: '7d' | '30d') {
    this.trendRange = range;
    this.loadHealthData();
  }

  setupChart() {
    const documentStyle = getComputedStyle(document.documentElement);
    const textColor = documentStyle.getPropertyValue('--text-color');
    const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary');
    const surfaceBorder = documentStyle.getPropertyValue('--surface-border');

    this.chartOptions = {
      plugins: {
        legend: {
          labels: {
            color: textColor
          }
        }
      },
      scales: {
        x: {
          ticks: {
            color: textColorSecondary
          },
          grid: {
            color: surfaceBorder
          }
        },
        y: {
          ticks: {
            color: textColorSecondary
          },
          grid: {
            color: surfaceBorder
          }
        }
      }
    };
  }

  updateChart() {
    const labels = this.weightData.map(d => {
      const date = new Date(d.date);
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });
    const data = this.weightData.map(d => d.value);

    // Calculate trend line (linear regression)
    const trendData = this.calculateTrendLine(data);

    this.chartData = {
      labels: labels,
      datasets: [
        {
          label: 'Weight (kg)',
          data: data,
          fill: false,
          borderColor: '#42A5F5',
          tension: 0.4,
          pointRadius: 4,
          pointHoverRadius: 6
        },
        {
          label: 'Trend',
          data: trendData,
          fill: false,
          borderColor: '#FFA726',
          borderDash: [5, 5],
          pointRadius: 0,
          pointHoverRadius: 0,
          tension: 0
        }
      ]
    };
  }

  calculateTrendLine(data: number[]): number[] {
    if (data.length === 0) return [];
    if (data.length === 1) return [data[0]];

    const n = data.length;
    let sumX = 0;
    let sumY = 0;
    let sumXY = 0;
    let sumXX = 0;

    // Calculate linear regression coefficients
    for (let i = 0; i < n; i++) {
      const x = i;
      const y = data[i];
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumXX += x * x;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    // Generate trend line points
    return data.map((_, i) => slope * i + intercept);
  }

  loadMessages() {
    if (!this.selectedDate) return;
    const dateStr = this.dateToString(this.selectedDate);
    this.messageApiService.getMessages(dateStr).subscribe({
      next: (messages) => {
        this.messages = messages;
      },
      error: (error) => {
        console.error('Error loading messages:', error);
        this.messages = [];
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
} 