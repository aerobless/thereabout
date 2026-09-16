import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ChartModule} from 'primeng/chart';
import {HeartService, HrvHistory} from '../../../../../generated/backend-api/thereabout';
import {HistoryCard} from './history-card';

@Component({
  selector: 'app-hrv-card',
  imports: [DatePipe, DialogModule, ChartModule],
  templateUrl: './hrv-card.component.html',
  styleUrl: './heart-history.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HrvCardComponent extends HistoryCard<HrvHistory> {
  private readonly api = inject(HeartService);
  protected fetch() { return this.api.getHrvHistory(this.date, this.days); }
  get hasChartHistory() { return this.history?.series.some(day => day.averageMs != null || day.trendMs != null) ?? false; }
  get comparable() { return !!this.history && ['UP', 'DOWN', 'STEADY'].includes(this.history.state); }
  get status() {
    return this.history ? {
      UP: 'Improving HRV trend', DOWN: 'Declining HRV trend', STEADY: 'Stable HRV trend',
      INSUFFICIENT_DATA: 'Not enough comparison data', NO_DATA: 'No HRV recorded'
    }[this.history.state] : '';
  }
  get arrow() {
    return this.history ? {UP: '↗', DOWN: '↘', STEADY: '→', INSUFFICIENT_DATA: '—', NO_DATA: '—'}[this.history.state] : '';
  }
  get percent() {
    const change = this.history?.changePercent;
    return change == null ? '—' : `${change > 0 ? '+' : ''}${change.toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1})}%`;
  }

  protected buildChart(history: HrvHistory) {
    const series = history.series;
    this.chartData = {labels: series.map(day => day.date), datasets: [
      {label: 'Usual range lower', data: series.map(day => day.baselineLowMs ?? null), borderWidth: 0, pointRadius: 0, backgroundColor: 'transparent', spanGaps: false, order: 3},
      {label: 'Your usual range', data: series.map(day => day.baselineHighMs ?? null), borderWidth: 0, pointRadius: 0, backgroundColor: 'rgba(148, 163, 184, 0.20)', fill: '-1', spanGaps: false, order: 3},
      {label: 'Daily HRV', data: series.map(day => day.averageMs ?? null), borderColor: '#9caec5', backgroundColor: '#9caec5', showLine: false, pointRadius: 4, order: 1},
      {label: '7-day average', data: series.map(day => day.trendMs ?? null), borderColor: '#4f7fb5', backgroundColor: '#4f7fb5', pointRadius: 0, borderWidth: 2, spanGaps: false, order: 0}
    ]};
    this.chartOptions = this.options(series.map(day => day.date), 'ms', 0, index => [
      `${series[index].recordCount} imported records`, `${series[index].coverage}/7 recorded days in trend`,
      `Personal range: ${series[index].baselineCoverage}/30 preceding days`
    ]);
  }
}
