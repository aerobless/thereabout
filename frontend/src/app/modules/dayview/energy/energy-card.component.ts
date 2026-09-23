import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {DatePipe} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ChartModule} from 'primeng/chart';
import {ChartData, ChartOptions} from 'chart.js';
import {DailyMetricValue} from '../../../../../generated/backend-api/thereabout';
import {EnergyDay, energyHistory, formatEnergy} from './energy-history';

@Component({
  selector: 'app-energy-card',
  imports: [DatePipe, DialogModule, ChartModule],
  templateUrl: './energy-card.component.html',
  styleUrl: './energy-card.component.scss'
})
export class EnergyCardComponent implements OnChanges {
  @Input({required: true}) date = '';
  @Input() activeRecords: DailyMetricValue[] = [];
  @Input() basalRecords: DailyMetricValue[] = [];
  @Input() loading = false;
  @Input() error = false;
  @Output() retry = new EventEmitter<void>();
  visible = false;
  days: 7 | 30 = 30;
  history: EnergyDay[] = [];
  chartData: ChartData<'bar'> = {datasets: []};
  chartOptions: ChartOptions<'bar'> = {};
  readonly dialogStyle = {width: '900px', maxWidth: 'calc(100vw - 2rem)', maxHeight: '90dvh'};
  readonly format = formatEnergy;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['date']) this.visible = false;
    this.build();
  }

  get selected() { return this.history[this.history.length - 1]; }
  get range() { return this.history.slice(-this.days); }
  get hasData() { return this.range.some(day => day.active !== null || day.basal !== null); }
  get unsupportedUnits() { return [...new Set(this.range.flatMap(day => day.unsupportedUnits))].join(', '); }
  isToday(date: string) {
    const now = new Date();
    return date === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }
  status(day: EnergyDay) {
    return day.incomplete ? 'Incomplete' : day.total === null ? 'No data' : 'Complete';
  }
  selectRange(days: 7 | 30) { this.days = days; this.build(); }

  private build() {
    if (!this.date) return;
    this.history = energyHistory(this.activeRecords, this.basalRecords, this.date, 30);
    const rows = this.range;
    this.chartData = {
      labels: rows.map(day => day.date),
      datasets: [
        {label: 'Basal', data: rows.map(day => day.basal), backgroundColor: '#a6b4c8'},
        {label: 'Active', data: rows.map(day => day.active), backgroundColor: '#e7aa72'}
      ].map(dataset => ({...dataset, stack: 'energy',
        borderColor: rows.map(day => day.date === this.date ? '#52647f' : 'transparent'),
        borderWidth: rows.map(day => day.date === this.date ? 1 : 0)}))
    };
    this.chartOptions = {
      responsive: true, maintainAspectRatio: false,
      interaction: {mode: 'index', intersect: false},
      scales: {
        x: {stacked: true, grid: {display: false}, ticks: {maxTicksLimit: 7, callback: (_, index) => rows[index]?.date.slice(5)}},
        y: {stacked: true, beginAtZero: true, title: {display: true, text: 'kcal'}}
      },
      plugins: {
        legend: {position: 'bottom'},
        tooltip: {callbacks: {
          title: items => {
            const day = rows[items[0].dataIndex];
            return day.date + (this.isToday(day.date) ? ' · so far' : '');
          },
          label: item => `${item.dataset.label}: ${this.format(item.parsed.y)}`,
          afterBody: items => {
            const day = rows[items[0].dataIndex];
            return day.incomplete ? ['Incomplete day — total unavailable'] : [`Total: ${this.format(day.total)}`];
          }
        }}
      }
    };
  }
}
