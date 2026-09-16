import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ChartModule} from 'primeng/chart';
import {HeartRateHistory, HeartService} from '../../../../../generated/backend-api/thereabout';
import {HistoryCard} from './history-card';

@Component({
  selector: 'app-heart-rate-card',
  imports: [DatePipe, DialogModule, ChartModule],
  templateUrl: './heart-rate-card.component.html',
  styleUrl: './heart-history.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HeartRateCardComponent extends HistoryCard<HeartRateHistory> {
  private readonly api = inject(HeartService);
  protected fetch() { return this.api.getHeartRateHistory(this.date, this.days); }
  get hasChartHistory() {
    return this.history?.series.some(day => day.averageBpm != null || day.restingBpm != null) ?? false;
  }
  get hasMultipleSummaries() { return this.history?.series.some(day => day.recordCount > 1) ?? false; }

  protected buildChart(history: HeartRateHistory) {
    const series = history.series;
    this.chartData = {labels: series.map(day => day.date), datasets: [
      {label: 'Daily minimum', data: series.map(day => day.minimumBpm ?? null), borderWidth: 0, pointRadius: 0, backgroundColor: 'transparent', spanGaps: false, order: 3},
      {label: 'Daily maximum / recorded range', data: series.map(day => day.maximumBpm ?? null), borderWidth: 0, pointRadius: 0, backgroundColor: 'rgba(96, 165, 250, 0.16)', fill: '-1', spanGaps: false, order: 3},
      {label: 'Daily average', data: series.map(day => day.averageBpm ?? null), borderColor: '#4f7fb5', backgroundColor: '#4f7fb5', pointRadius: 3, borderWidth: 2, spanGaps: false, order: 1},
      {label: 'Resting heart rate', data: series.map(day => day.restingBpm ?? null), borderColor: '#9b7bb5', backgroundColor: '#9b7bb5', borderDash: [5, 5], pointRadius: 2, borderWidth: 2, spanGaps: false, order: 0}
    ]};
    this.chartOptions = this.options(series.map(day => day.date), 'bpm', 0, index => [
      `${series[index].recordCount} imported heart rate records`,
      `${series[index].restingRecordCount} imported resting heart rate records`
    ]);
  }
}
