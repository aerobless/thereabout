import {Component, ChangeDetectionStrategy, DestroyRef, Input, OnChanges, inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ChartModule} from 'primeng/chart';
import {DialogModule} from 'primeng/dialog';
import {ChartData, ChartOptions} from 'chart.js';
import {ChoicesHistory, ChoicesService} from '../../../../../generated/backend-api/thereabout';

@Component({
  selector: 'app-choices-card',
  imports: [DatePipe, ChartModule, DialogModule],
  templateUrl: './choices-card.component.html',
  styleUrl: './choices-card.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ChoicesCardComponent implements OnChanges {
  @Input({required: true}) date = '';
  private readonly api = inject(ChoicesService);
  private readonly destroyRef = inject(DestroyRef);
  private requestId = 0;
  private savingDate: string | null = null;
  history: ChoicesHistory | null = null;
  days: 7 | 30 = 7;
  visible = false;
  loading = false;
  error = false;
  saveError = false;
  chartData: ChartData<'bar'> | null = null;
  readonly chartOptions: ChartOptions<'bar'> = {
    responsive: true, maintainAspectRatio: false,
    plugins: {legend: {display: false}},
    scales: {
      x: {grid: {display: false}, ticks: {maxTicksLimit: 7}},
      y: {beginAtZero: true, suggestedMin: -1, suggestedMax: 1,
        ticks: {precision: 0}, title: {display: true, text: 'Points'},
        grid: {color: context => context.tick.value === 0 ? '#566786' : '#e8edf3',
          lineWidth: context => context.tick.value === 0 ? 2 : 1}}
    }
  };
  get saving() { return this.savingDate !== null; }
  get canAdjust() { return !!this.history?.editable && !this.loading && !this.error && !this.saving; }
  get state() { return !this.history ? 'neutral' : this.history.score > 0 ? 'positive' : this.history.score < 0 ? 'negative' : 'neutral'; }
  signed(score: number) { return score > 0 ? `+${score}` : `${score}`; }

  ngOnChanges() {
    this.visible = false;
    this.history = null;
    this.chartData = null;
    this.saveError = false;
    ++this.requestId;
    this.load();
  }

  load() {
    const id = ++this.requestId;
    if (!this.date) { this.loading = false; return; }
    this.loading = true;
    this.error = false;
    this.api.getChoicesHistory(this.date, this.days).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: history => {
        if (id !== this.requestId) return;
        this.history = history;
        this.loading = false;
        this.chartData = {
          labels: history.series.map(day => day.date),
          datasets: [{label: 'Daily score', data: history.series.map(day => day.score),
            backgroundColor: history.series.map(day => day.score > 0 ? '#65a879' : day.score < 0 ? '#cf7272' : '#9ca9ba'),
            borderColor: history.series.map(day => day.date === this.date ? '#101b4a' : 'transparent'),
            borderWidth: history.series.map(day => day.date === this.date ? 2 : 0), borderRadius: 3}]
        };
      },
      error: () => { if (id === this.requestId) { this.error = true; this.loading = false; } }
    });
  }

  adjust(delta: -1 | 1) {
    if (!this.canAdjust) return;
    const date = this.date;
    this.savingDate = date;
    this.saveError = false;
    // Invalidate any read that started before this write.
    ++this.requestId;
    this.api.adjustChoices(date, {delta}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingDate = null;
        this.load();
      },
      error: () => {
        this.savingDate = null;
        this.saveError = this.date === date;
        // A lost response may still have committed. Reconcile, never replay the increment.
        this.load();
      }
    });
  }

  selectRange(days: 7 | 30) {
    if (days === this.days) return;
    this.days = days;
    this.load();
  }
}
