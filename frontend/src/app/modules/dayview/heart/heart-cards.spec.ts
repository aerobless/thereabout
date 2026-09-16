import {Component, Input, Type} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ChartModule} from 'primeng/chart';
import {of, Subject, throwError} from 'rxjs';
import {vi} from 'vitest';
import {HeartRateHistory, HeartService, HrvHistory} from '../../../../../generated/backend-api/thereabout';
import {HeartRateCardComponent} from './heart-rate-card.component';
import {HrvCardComponent} from './hrv-card.component';

@Component({selector: 'p-chart', template: ''})
class ChartStub {
  @Input() type: unknown;
  @Input() data: unknown;
  @Input() options: unknown;
  @Input() ariaLabel: unknown;
}
const date = '2026-09-16';
const hrv = (day = date, state: HrvHistory.StateEnum = 'UP'): HrvHistory => ({
  date: day, state, coverage: 7, previousCoverage: 7, weeklyAverageMs: 52, previousWeeklyAverageMs: 48, changePercent: 100 / 12,
  selectedDay: {date: day, averageMs: 56.09, recordCount: 2, coverage: 7, baselineCoverage: 30, baselineLowMs: 40, baselineHighMs: 60},
  series: [{date: '2026-09-15', recordCount: 0, coverage: 3, baselineCoverage: 10},
    {date: day, averageMs: 56.09, recordCount: 2, coverage: 7, baselineCoverage: 30, trendMs: 52, baselineLowMs: 40, baselineHighMs: 60}]
});
const heart = (day = date): HeartRateHistory => ({
  date: day, selectedDay: {date: day, averageBpm: 72.5, minimumBpm: 48, maximumBpm: 136, restingBpm: 55, recordCount: 2, restingRecordCount: 1},
  series: [{date: '2026-09-15', recordCount: 0, restingRecordCount: 0},
    {date: day, averageBpm: 72.5, minimumBpm: 48, maximumBpm: 136, restingBpm: 55, recordCount: 2, restingRecordCount: 1}]
});
async function setup<T>(type: Type<T>, api: object) {
  await TestBed.configureTestingModule({imports: [type], providers: [{provide: HeartService, useValue: api}]})
    .overrideComponent(type, {remove: {imports: [ChartModule]}, add: {imports: [ChartStub]}}).compileComponents();
  const fixture = TestBed.createComponent(type);
  fixture.componentRef.setInput('date', date); fixture.detectChanges();
  return fixture;
}
describe('HRV card and modal', () => {
  it('renders rounded values and labelled colour, opens the modal and preserves the summary across range changes', async () => {
    const getHrvHistory = vi.fn().mockReturnValue(of(hrv()));
    const fixture = await setup(HrvCardComponent, {getHrvHistory});
    const component = fixture.componentInstance;
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.heart-card');
    expect(button.textContent).toContain('56');
    expect(button.textContent).not.toContain('56.09');
    expect(button.textContent).toContain('Improving HRV trend');
    expect(button.getAttribute('data-trend')).toBe('UP');
    button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Why this colour?');
    const controls = [...fixture.nativeElement.querySelectorAll('.range-controls button')] as HTMLButtonElement[];
    expect(controls.map(el => el.textContent?.trim())).toEqual(['7 days', '30 days']);
    controls[0].click(); fixture.detectChanges();
    expect(getHrvHistory).toHaveBeenLastCalledWith(date, 7);
    expect(component.visible).toBe(true);
    expect(component.history?.selectedDay.averageMs).toBe(56.09);
    expect(component.status).toBe('Improving HRV trend');
    expect(component.chartData?.datasets[2].data).toEqual([null, 56.09]);
    expect(component.chartData?.datasets[3].data).toEqual([null, 52]);
    expect(component.chartData?.datasets[3].spanGaps).toBe(false);
  });
  it('uses backend state for every trend and leaves missing values empty', async () => {
    const fixture = await setup(HrvCardComponent, {getHrvHistory: vi.fn().mockReturnValue(of(hrv()))});
    const component = fixture.componentInstance;
    for (const [state, arrow, label] of [
      ['UP', '↗', 'Improving HRV trend'], ['DOWN', '↘', 'Declining HRV trend'], ['STEADY', '→', 'Stable HRV trend'],
      ['INSUFFICIENT_DATA', '—', 'Not enough comparison data'], ['NO_DATA', '—', 'No HRV recorded']
    ] as const) {
      component.history = hrv(date, state); fixture.detectChanges();
      expect(component.arrow).toBe(arrow); expect(component.status).toBe(label);
      expect(fixture.nativeElement.querySelector('.heart-card').getAttribute('data-trend')).toBe(state);
    }
    expect(component.value(undefined)).toBe('—'); expect(component.comparable).toBe(false);
  });
  it('marks today provisional', async () => {
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const fixture = await setup(HrvCardComponent, {getHrvHistory: vi.fn().mockReturnValue(of(hrv(today)))});
    fixture.componentRef.setInput('date', today); fixture.detectChanges();
    expect(fixture.componentInstance.today).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('provisional');
    fixture.componentRef.setInput('date', '2000-01-01'); fixture.detectChanges();
    expect(fixture.componentInstance.today).toBe(false);
    expect(fixture.nativeElement.querySelector('.heart-card').textContent).not.toContain('provisional');
  });
  it('closes on date change and ignores superseded successes and errors', async () => {
    const old = new Subject<HrvHistory>(); const current = new Subject<HrvHistory>();
    const getHrvHistory = vi.fn().mockReturnValueOnce(old).mockReturnValueOnce(current);
    const fixture = await setup(HrvCardComponent, {getHrvHistory});
    const component = fixture.componentInstance;
    old.next(hrv()); component.visible = true;
    fixture.componentRef.setInput('date', '2026-09-17'); fixture.detectChanges();
    expect(component.visible).toBe(false); expect(component.history).toBeNull(); expect(component.chartData).toBeNull();
    current.next(hrv('2026-09-17')); old.next(hrv()); old.error(new Error('stale'));
    expect(component.history?.date).toBe('2026-09-17'); expect(component.error).toBe(false);
  });
  it('distinguishes loading, errors, retry, empty history and baseline warm-up', async () => {
    const response = new Subject<HrvHistory>();
    const getHrvHistory = vi.fn().mockReturnValueOnce(response);
    const fixture = await setup(HrvCardComponent, {getHrvHistory});
    expect(fixture.nativeElement.textContent).toContain('Loading HRV');
    response.error(new Error('offline')); fixture.detectChanges();
    fixture.nativeElement.querySelector('.heart-card').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Unable to load HRV history');
    const empty: HrvHistory = {...hrv(date, 'NO_DATA'), selectedDay: {date, recordCount: 0, coverage: 0, baselineCoverage: 0}, series: []};
    getHrvHistory.mockReturnValue(of(empty));
    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    buttons.find(el => el.textContent?.trim() === 'Retry')!.click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No HRV history');
    expect(fixture.nativeElement.textContent).toContain('Building your baseline');
    expect(fixture.componentInstance.error).toBe(false);
  });
});
describe('Heart rate card and modal', () => {
  it('shows average and range, puts resting heart rate in the modal and plots gaps', async () => {
    const getHeartRateHistory = vi.fn().mockReturnValue(of(heart()));
    const fixture = await setup(HeartRateCardComponent, {getHeartRateHistory});
    const component = fixture.componentInstance;
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.heart-card');
    expect(button.textContent).toContain('73'); expect(button.textContent).toContain('48–136'); expect(button.textContent).not.toContain('55');
    button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Resting heart rate');
    expect(fixture.nativeElement.textContent).toContain('average of imported summaries');
    component.selectRange(7); fixture.detectChanges();
    expect(getHeartRateHistory).toHaveBeenLastCalledWith(date, 7); expect(component.visible).toBe(true);
    expect(component.chartData?.datasets[2].data).toEqual([null, 72.5]); expect(component.chartData?.datasets[3].data).toEqual([null, 55]);
    expect(component.chartData?.datasets.every(dataset => dataset.spanGaps === false)).toBe(true);
  });
  it('handles missing data, retry and rapid date changes without stale results', async () => {
    const getHeartRateHistory = vi.fn().mockReturnValueOnce(throwError(() => new Error('offline')));
    const fixture = await setup(HeartRateCardComponent, {getHeartRateHistory});
    const component = fixture.componentInstance; expect(component.error).toBe(true);
    const old = new Subject<HeartRateHistory>(); const current = new Subject<HeartRateHistory>();
    getHeartRateHistory.mockReturnValueOnce(old).mockReturnValueOnce(current);
    component.load(); component.visible = true;
    fixture.componentRef.setInput('date', '2026-09-17'); fixture.detectChanges(); expect(component.visible).toBe(false);
    current.next({...heart('2026-09-17'), selectedDay: {date: '2026-09-17', recordCount: 0, restingRecordCount: 0}, series: []});
    old.next(heart()); old.error(new Error('stale')); fixture.detectChanges();
    expect(component.history?.date).toBe('2026-09-17'); expect(component.hasChartHistory).toBe(false); expect(component.error).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('—');
  });
});
