import {registerRefresh} from '../../../shared/refresh/refresh-coordinator';
import {DestroyRef, Directive, Input, OnChanges, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable} from 'rxjs';
import {ChartData, ChartOptions} from 'chart.js';

@Directive()
export abstract class HistoryCard<T> implements OnChanges {
  private readonly refresh = registerRefresh(() => this.load());
  @Input({required: true}) date = '';
  private readonly destroyRef = inject(DestroyRef);
  private requestId = 0;
  history: T | null = null;
  loading = false;
  error = false;
  visible = false;
  days: 7 | 30 = 30;
  chartData: ChartData<'line'> | null = null;
  chartOptions: ChartOptions<'line'> = {};
  readonly dialogStyle = {width: '960px', maxWidth: 'calc(100vw - 2rem)', maxHeight: '90dvh'};

  protected abstract fetch(): Observable<T>;
  protected abstract buildChart(history: T): void;

  ngOnChanges() {
    this.visible = false;
    this.history = null;
    this.chartData = null;
    // Invalidate previous responses even if the new date is empty.
    ++this.requestId;
    this.load();
  }

  load() {
    if (!this.date) return;
    const requestId = ++this.requestId;
    this.loading = true;
    this.error = false;
    this.fetch().pipe(this.refresh.track('history'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: history => {
        if (requestId !== this.requestId) return;
        this.history = history;
        this.loading = false;
        this.buildChart(history);
      },
      error: () => {
        if (requestId !== this.requestId) return;
        this.loading = false;
        this.error = true;
      }
    });
  }

  selectRange(days: 7 | 30) {
    if (days === this.days) return;
    this.days = days;
    this.load();
  }

  get today(): boolean {
    const now = new Date();
    return this.date === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  value(value: number | null | undefined): string {
    return value == null ? '—' : value.toLocaleString(undefined, {maximumFractionDigits: 0});
  }

  protected options(dates: string[], unit: string, hiddenDataset: number, afterBody: (index: number) => string[]): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      interaction: {mode: 'index', intersect: false},
      plugins: {
        legend: {position: 'bottom', labels: {filter: item => item.datasetIndex !== hiddenDataset}},
        tooltip: {callbacks: {
          title: items => items.length ? dates[items[0].dataIndex] : '',
          label: item => `${item.dataset.label}: ${this.value(item.parsed.y)} ${unit}`,
          afterBody: items => items.length ? afterBody(items[0].dataIndex) : []
        }}
      },
      scales: {
        x: {grid: {display: false}, ticks: {maxTicksLimit: 7, callback: (_, index) => `${dates[index].slice(8)}.${dates[index].slice(5, 7)}`}},
        y: {title: {display: true, text: unit}}
      }
    };
  }
}
