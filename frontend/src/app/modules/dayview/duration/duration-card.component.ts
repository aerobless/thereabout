import {Component, Input, Output, EventEmitter, OnChanges} from '@angular/core';
import {DatePipe, TitleCasePipe} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ChartModule} from 'primeng/chart';
import {ChartData, ChartOptions} from 'chart.js';
import {DailyMetricValue} from '../../../../../generated/backend-api/thereabout';
import {average, duration, durationHistory, DurationDay, stages} from './duration-history';

@Component({
  selector: 'app-duration-card',
  imports: [DatePipe, TitleCasePipe, DialogModule, ChartModule],
  templateUrl: './duration-card.component.html',
  styleUrl: './duration-card.component.scss'
})
export class DurationCardComponent implements OnChanges {
  @Input({required: true}) date = '';
  @Input() kind: 'stand' | 'sleep' = 'stand';
  @Input() records: DailyMetricValue[] = [];
  @Input() loading = false;
  @Input() error = false;
  @Output() retry = new EventEmitter<void>();
  visible = false;
  days: 7 | 30 = 30;
  history: DurationDay[] = [];
  readonly stages = stages;
  readonly colours = ['#a6b4c8', '#52647f', '#7e86a8'];
  readonly dialogStyle = {width: '900px', maxWidth: 'calc(100vw - 2rem)', maxHeight: '90dvh'};
  chartData: ChartData<'bar' | 'line'> = {datasets: []};
  chartOptions: ChartOptions<'bar' | 'line'> = {};
  ngOnChanges(changes: Record<string, unknown>) {
    if (changes['date']) this.visible = false;
    this.build();
  }
  get sleep() { return this.kind === 'sleep'; }
  get title() { return this.sleep ? 'Sleep' : 'Stand'; }
  get selected() { return this.history[this.history.length - 1]; }
  get range() { return this.history.slice(-this.days); }
  get coverage() { return this.range.filter(d => d.total !== null).length; }
  get rangeAverage() { return average(this.range); }
  get weekAverage() { return average(this.history.slice(-7)); }
  get hasStages() { return !!this.selected && stages.some(s => this.selected[s] !== null); }
  get hasData() { return this.range.some(d => d.total !== null || stages.some(s => d[s] !== null)); }
  format(value: number | null | undefined) { return duration(value ?? null, this.sleep); }
  width(stage: typeof stages[number]) {
    const d = this.selected;
    if (!d) return 0;
    const total = Math.max(d.total ?? 0, stages.reduce((n, s) => n + (d[s] ?? 0), 0));
    return total ? (d[stage] ?? 0) / total * 100 : 0;
  }
  selectRange(days: 7 | 30) { this.days = days; this.build(); }
  private build() {
    if (!this.date) return;
    this.history = durationHistory(this.records, this.date, 30, this.sleep);
    const rows = this.range;
    this.chartData = {
      labels: rows.map(d => d.date),
      datasets: this.sleep ? [
        ...stages.filter(s => rows.some(d => d[s] !== null)).map((s) => ({type: 'bar' as const, label: s === 'rem' ? 'REM' : s[0].toUpperCase() + s.slice(1), data: rows.map(d => d[s]), backgroundColor: this.colours[stages.indexOf(s)], stack: 'stages', order: 2})),
        {type: 'line', label: 'Total sleep', data: rows.map(d => d.total), borderColor: '#233750', backgroundColor: '#233750', pointRadius: 3, spanGaps: false, stack: 'total', order: 1}
      ] : [{type: 'bar', label: 'Standing time', data: rows.map(d => d.total), backgroundColor: '#7e8da5'}]
    };
    this.chartOptions = {responsive: true, maintainAspectRatio: false,
      interaction: {mode: 'index', intersect: false},
      scales: {x: {stacked: this.sleep, grid: {display: false}, ticks: {maxTicksLimit: 7, callback: (_, i) => rows[i]?.date.slice(5)}}, y: {stacked: this.sleep, beginAtZero: true, title: {display: true, text: this.sleep ? 'Hours' : 'Minutes'}}},
      plugins: {legend: {display: this.sleep, position: 'bottom'}, tooltip: {callbacks: {label: item => `${item.dataset.label}: ${this.format(item.parsed.y)}`, afterBody: items => items.some(i => rows[i.dataIndex].partial) ? ['Incomplete stage breakdown'] : []}}}};
  }
}
