import {registerRefresh} from '../../../shared/refresh/refresh-coordinator';
import {ChangeDetectionStrategy, Component, DestroyRef, Input, OnChanges, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DialogModule} from 'primeng/dialog';
import {ChartModule} from 'primeng/chart';
import {ChartData, ChartOptions} from 'chart.js';
import {Preferences, PreferencesService, WeightProgress, WeightService} from '../../../../../generated/backend-api/thereabout';

@Component({
  selector: 'app-weight-card',
  imports: [DatePipe, FormsModule, DialogModule, ChartModule],
  templateUrl: './weight-card.component.html',
  styleUrl: './weight-card.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class WeightCardComponent implements OnChanges {
  private readonly refresh = registerRefresh(() => this.load(true), () => this.saving || this.editing);
  @Input({required: true}) date = '';
  private readonly api = inject(WeightService);
  private readonly preferencesApi = inject(PreferencesService);
  private readonly destroyRef = inject(DestroyRef);
  private requestId = 0;
  progress: WeightProgress | null = null;
  preferences: Preferences | null = null;
  loading = false;
  error = false;
  visible = false;
  days: 7 | 30 = 30;
  editing = false;
  goalDraft: number | null = null;
  saving = false;
  saveError = '';
  chartData: ChartData<'line'> | null = null;
  chartOptions: ChartOptions<'line'> = {};

  ngOnChanges() {
    this.visible = false;
    this.editing = false;
    this.load();
  }

  load(preserve = false) {
    if (!this.date) return;
    const requestId = ++this.requestId;
    this.loading = true;
    this.error = false;
    if (!preserve) { this.progress = null; this.chartData = null; }
    this.api.getWeightProgress(this.date, this.days).pipe(this.refresh.track('weight'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: progress => {
        if (requestId !== this.requestId) return;
        this.progress = progress;
        this.preferences = progress.preferences;
        this.loading = false;
        this.buildChart(progress);
      },
      error: () => {
        if (requestId !== this.requestId) return;
        this.loading = false;
        this.error = true;
      }
    });
  }

  selectRange(days: 7 | 30) {
    if (this.days === days) return;
    this.days = days;
    this.load();
  }

  editGoal() {
    this.goalDraft = this.preferences?.weightGoalKg ?? null;
    this.saveError = '';
    this.editing = true;
  }

  cancelEdit() {
    this.goalDraft = this.preferences?.weightGoalKg ?? null;
    this.saveError = '';
    this.editing = false;
  }

  get validGoal(): boolean {
    const goal = this.goalDraft;
    return goal !== null && Number.isFinite(goal) && goal > 0 && (Number.isInteger(goal) || Math.abs(goal * 10 - Math.round(goal * 10)) < 1e-8);
  }

  saveGoal() {
    if (!this.validGoal || this.saving) return;
    this.saving = true;
    this.saveError = '';
    this.preferencesApi.updatePreferences({weightGoalKg: this.goalDraft!}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: preferences => {
        this.preferences = preferences;
        this.saving = false;
        this.editing = false;
        this.load();
      },
      error: () => {
        this.saving = false;
        this.saveError = 'Could not save your goal. Your previous goal is unchanged. Please try again.';
      }
    });
  }

  kg(value: number | null | undefined): string {
    return value == null ? '--' : value.toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1});
  }

  get status(): string {
    if (!this.progress) return '';
    return {
      IN_ZONE: 'Stay in the zone', NEW_BEST: 'New best', TOWARD: 'Moving toward goal',
      STEADY: 'Essentially unchanged', AWAY: 'Moving away from goal', BEFORE_CHALLENGE: 'Before current goal',
      NO_DATA: this.progress.trendKg == null ? 'No recent weigh-ins' : 'Not enough comparison data'
    }[this.progress.state];
  }

  get arrow(): string {
    return this.progress?.arrow === 'UP' ? '↑' : this.progress?.arrow === 'DOWN' ? '↓' : this.progress?.arrow === 'STEADY' ? '→' : '';
  }

  get arrowLabel(): string {
    return this.progress?.arrow === 'UP' ? 'Weight trend increased compared with last week'
      : this.progress?.arrow === 'DOWN' ? 'Weight trend decreased compared with last week' : 'Weight trend unchanged compared with last week';
  }

  get weeklyProgress(): string {
    const change = this.progress?.weeklyGapChangeKg;
    if (change == null) return 'Weekly comparison unavailable';
    if (Math.abs(change) < 0.1) return 'Gap essentially unchanged this week';
    return `${this.kg(Math.abs(change))} kg ${change < 0 ? 'closer to' : 'further from'} goal this week`;
  }

  get hasChartHistory(): boolean {
    return this.progress?.series.some(point => point.readingKg != null || point.trendKg != null) ?? false;
  }

  private buildChart(progress: WeightProgress) {
    const series = progress.series;
    this.chartData = {
      labels: series.map(point => point.date),
      datasets: [
        {label: 'Target zone lower', data: series.map(() => progress.zoneMinKg), borderWidth: 0, pointRadius: 0, backgroundColor: 'transparent', order: 3},
        {label: 'Target zone', data: series.map(() => progress.zoneMaxKg), borderWidth: 0, pointRadius: 0, backgroundColor: 'rgba(158, 211, 177, 0.25)', fill: '-1', order: 3},
        {label: 'Goal', data: series.map(() => progress.preferences.weightGoalKg), borderColor: '#628e79', borderDash: [5, 5], borderWidth: 1, pointRadius: 0, order: 2},
        {label: 'Daily reading', data: series.map(point => point.readingKg ?? null), borderColor: '#b5c5d8', backgroundColor: '#b5c5d8', showLine: false, pointRadius: 3, order: 1},
        {label: '7-day trend', data: series.map(point => point.trendKg ?? null), borderColor: '#6486b0', backgroundColor: '#6486b0', pointRadius: 0, borderWidth: 2, spanGaps: false, order: 0}
      ]
    };
    this.chartOptions = {
      responsive: true, maintainAspectRatio: false, interaction: {mode: 'index', intersect: false},
      plugins: {
        legend: {position: 'bottom', labels: {filter: item => item.datasetIndex !== 0}},
        tooltip: {filter: item => item.datasetIndex >= 3, callbacks: {
          title: items => items.length ? series[items[0].dataIndex].date : '',
          label: item => `${item.dataset.label}: ${this.kg(item.parsed.y)} kg`,
          afterBody: items => items.length ? [`${series[items[0].dataIndex].coverage}/7 weigh-in days`] : []
        }}
      },
      scales: {
        x: {grid: {display: false}, ticks: {maxTicksLimit: 7, callback: (_, index) => `${series[index].date.slice(8)}.${series[index].date.slice(5, 7)}`}},
        y: {title: {display: true, text: 'kg'}}
      }
    };
  }
}
