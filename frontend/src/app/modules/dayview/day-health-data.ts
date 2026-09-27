import { Injectable, inject, DestroyRef, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { registerRefresh } from '../../shared/refresh/refresh-coordinator';
import { localDateString } from '../../shared/dates/local-date';
import { DayViewData } from './day-view-data';
import { DailyMetricValue, HealthService, WorkoutSummary } from '../../../../generated/backend-api/thereabout';
import { ChartData, ChartOptions } from 'chart.js';
import { dailyStepTotals, shiftDay, stepHistory, StepProgress } from './steps-progress';
@Injectable()
export class DayHealthData {
  private readonly day = inject(DayViewData);
  private readonly destroyRef = inject(DestroyRef);
  private readonly healthService = inject(HealthService);
  private readonly refresh = registerRefresh(() => this.loadHealthData(true));
  get selectedDate(): Date { return this.day.selectedDate(); }
  get isSelectedDayToday(): boolean { return localDateString(this.selectedDate) === localDateString(new Date()); }
  readonly dateToString = localDateString;
  workouts = signal<WorkoutSummary[]>([]);
  stepsDialogVisible = signal(false);
  stepsLoading = signal(false);
  stepsError = signal(false);
  stepsHistory = signal<StepProgress[]>([]);
  selectedStepProgress = signal<StepProgress | null>(null);
  stepsChartData = signal<ChartData<'bar' | 'line'> | null>(null);
  stepsChartOptions = signal<ChartOptions<'bar' | 'line'>>({});
  private healthRequestId = 0;
  private healthDate: string | null = null;
  get hasStepHistory(): boolean {
    return this.stepsHistory().some(day => day.steps !== null);
  }
  formatStepPercentage(value: number | null): string {
    return value === null ? '--' : `${(Math.floor(value * 10) / 10).toLocaleString()}%`;
  }
  stepDifference(progress: StepProgress): string {
    if (progress.percentage === null || progress.steps === null || progress.baseline === null)
      return 'Baseline unavailable';
    const difference = progress.steps - progress.baseline;
    if (difference === 0)
      return 'At your baseline';
    return `${this.formatSteps(Math.ceil(Math.abs(difference)))} steps ${difference > 0 ? 'above' : 'below'} baseline`;
  }
  private updateStepsChart() {
    const colors = { below: '#efb9b5', almost: '#ecd08c', reached: '#b7dcc0', bonus: '#9ed3b1', exceptional: '#85c9bd' };
    const history = this.stepsHistory();
    this.stepsChartData.set({
      labels: history.map(day => day.date),
      datasets: [
        {
          type: 'line', label: 'Previous 30-day average', data: history.map(day => day.baseline),
          borderColor: '#6486b0', backgroundColor: '#6486b0', borderWidth: 2, pointRadius: 0,
          pointHitRadius: 12, tension: 0.2, spanGaps: false, order: 0
        },
        {
          type: 'bar', label: 'Daily steps', data: history.map(day => day.steps),
          backgroundColor: history.map(day => day.level ? colors[day.level] : '#94a3b8'),
          borderColor: history.map((_, i) => i === 29 ? '#0f172a' : 'transparent'),
          borderWidth: history.map((_, i) => i === 29 ? 2 : 0), borderRadius: 3, order: 1
        }
      ]
    });
    this.stepsChartOptions.set({
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'bottom' },
        tooltip: {
          callbacks: {
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
          }
        }
      },
      scales: {
        x: {
          grid: { display: false }, ticks: {
            maxTicksLimit: 7, callback: (_, index) => {
              const date = history[index].date;
              return `${date.slice(8)}.${date.slice(5, 7)}`;
            }
          }
        },
        y: { beginAtZero: true, title: { display: true, text: 'Steps' } }
      }
    });
  }
  activeEnergyRecords = signal<DailyMetricValue[]>([]);
  basalEnergyRecords = signal<DailyMetricValue[]>([]);
  selectedDaySteps = signal<number | null>(null);
  standRecords = signal<DailyMetricValue[]>([]);
  sleepRecords = signal<DailyMetricValue[]>([]);
  selectedDayDistanceKm = signal<number | null>(null);
  loadHealthData(preserve = false) {
    if (!this.selectedDate)
      return;
    const selectedDateStr = this.dateToString(this.selectedDate);
    const requestId = ++this.healthRequestId;
    if (this.healthDate !== selectedDateStr)
      this.stepsDialogVisible.set(false);
    this.healthDate = selectedDateStr;
    if (!preserve)
      this.stepsLoading.set(true);
    this.stepsError.set(false);
    if (!preserve) {
      this.stepsHistory.set([]);
      this.standRecords.set([]);
      this.sleepRecords.set([]);
      this.activeEnergyRecords.set([]);
      this.basalEnergyRecords.set([]);
      this.selectedStepProgress.set(null);
      this.selectedDaySteps.set(null);
      this.selectedDayDistanceKm.set(null);
      this.stepsChartData.set(null);
    }
    this.healthService.getHealthDataByDateRange(shiftDay(selectedDateStr, -59), selectedDateStr)
      .pipe(this.refresh.track('health'), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (response) => {
          if (requestId !== this.healthRequestId)
            return;
          this.stepsLoading.set(false);
          this.stepsHistory.set(stepHistory(selectedDateStr, dailyStepTotals(response.metrics?.['step_count'] ?? [])));
          this.selectedStepProgress.set(this.stepsHistory()[29]);
          this.selectedDaySteps.set(this.stepsHistory()[29].steps);
          this.updateStepsChart();
          this.activeEnergyRecords.set(response.metrics?.['active_energy'] ?? []);
          this.basalEnergyRecords.set(response.metrics?.['basal_energy_burned'] ?? []);
          // Extract and filter workouts for selected day
          const allWorkouts = response.workouts || [];
          this.workouts.set(allWorkouts
            .filter(workout => {
              if (!workout.start)
                return false;
              const workoutDate = new Date(workout.start);
              const workoutDateStr = this.dateToString(workoutDate);
              return workoutDateStr === selectedDateStr;
            })
            .sort((a, b) => {
              if (!a.start || !b.start)
                return 0;
              return new Date(a.start).getTime() - new Date(b.start).getTime();
            }));
          // Daily stats for selected day
          this.standRecords.set(response.metrics?.['apple_stand_time'] ?? []);
          this.sleepRecords.set(response.metrics?.['sleep_analysis'] ?? []);
          const distanceMetrics = response.metrics?.['walking_running_distance'] || [];
          const distanceForDay = distanceMetrics.find((m: {
            date?: string;
          }) => m.date === selectedDateStr);
          this.selectedDayDistanceKm.set(distanceForDay?.qty != null ? Number(distanceForDay.qty) : null);
        },
        error: (error) => {
          if (requestId !== this.healthRequestId)
            return;
          this.stepsLoading.set(false);
          this.stepsError.set(!preserve);
          console.error('Error loading health data:', error);
          if (preserve)
            return;
          this.activeEnergyRecords.set([]);
          this.basalEnergyRecords.set([]);
          this.workouts.set([]);
          this.selectedDaySteps.set(null);
          this.selectedDayDistanceKm.set(null);
        }
      });
  }
  formatSteps(steps: number | null): string {
    if (steps === null || steps === undefined)
      return '--';
    return Math.round(steps).toLocaleString();
  }
  formatDistanceKm(km: number | null): string {
    if (km === null || km === undefined)
      return '--';
    return km.toFixed(2);
  }
}
