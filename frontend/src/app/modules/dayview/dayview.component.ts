import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {ToolbarModule} from "primeng/toolbar";
import {Router, ActivatedRoute} from "@angular/router";
import {TooltipModule} from "primeng/tooltip";
import {CalendarModule} from "primeng/calendar";
import {FormsModule} from "@angular/forms";
import {PanelModule} from "primeng/panel";
import {CardModule} from "primeng/card";
import {ChartModule} from "primeng/chart";
import {NgIf, NgForOf, DatePipe} from "@angular/common";
import {
    GoogleMap,
    MapMarker,
    MapPolyline
} from "@angular/google-maps";
import {
    HealthService,
    LocationHistoryEntry,
    LocationService
} from "../../../../generated/backend-api/thereabout";

@Component({
  selector: 'app-dayview',
  standalone: true,
  imports: [
    ButtonModule,
    ToolbarModule,
    TooltipModule,
    CalendarModule,
    FormsModule,
    PanelModule,
    CardModule,
    ChartModule,
    NgIf,
    NgForOf,
    DatePipe,
    GoogleMap,
    MapMarker,
    MapPolyline
  ],
  templateUrl: './dayview.component.html',
  styleUrl: './dayview.component.scss'
})
export class DayviewComponent implements OnInit {

  selectedDate: Date = new Date();
  
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

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private locationService: LocationService,
    private healthService: HealthService
  ) {
  }

  navigateBackToMap() {
    this.router.navigate(['']);
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

  onDateChange() {
    // Handle date change - can be extended with additional logic
    console.log('Date changed to:', this.selectedDate);
    this.updateUrl();
    this.loadDayViewData();
    this.loadHealthData();
  }

  private setDateAndUpdateUrl(date: Date) {
    this.selectedDate = date;
    this.updateUrl();
    this.loadDayViewData();
    this.loadHealthData();
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
          if (newDateStr !== currentDateStr) {
            this.selectedDate = parsedDate;
            this.loadDayViewData();
            this.loadHealthData();
          }
        }
      } else {
        // If no date in URL, update URL with current date (use replaceUrl to avoid history entry)
        this.updateUrl(true);
      }
    });
    
    // Load data immediately on initial load (regardless of URL params)
    this.loadDayViewData();
    this.loadHealthData();
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

        // Update chart
        this.updateChart();
      },
      error: (error) => {
        console.error('Error loading health data:', error);
        this.weightData = [];
        this.selectedDayWeight = null;
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
} 