import {describe, it, expect} from 'vitest';
import {average, duration, durationHistory} from './duration-history';
import {DurationCardComponent} from './duration-card.component';

describe('duration history', () => {
  it('aggregates records, preserves zeros and excludes missing days from averages', () => {
    const rows = durationHistory([{date:'2026-03-29',qty:10},{date:'2026-03-29',qty:20},{date:'2026-03-30',qty:0}], '2026-03-30', 3, false);
    expect(rows.map(d => d.total)).toEqual([null,30,0]);
    expect(average(rows)).toBe(15);
    expect(rows.map(d => d.date)).toEqual(['2026-03-28','2026-03-29','2026-03-30']);
  });
  it('retains totals separately from complete, partial and unavailable stages', () => {
    const rows = durationHistory([
      {date:'2026-09-17',qty:8,core:5,deep:1,rem:2},
      {date:'2026-09-18',qty:7,core:4,rem:1},
      {date:'2026-09-19',qty:6}
    ], '2026-09-19', 3, true);
    expect(rows.map(d=>d.total)).toEqual([8,7,6]);
    expect(rows.map(d=>d.partial)).toEqual([false,true,false]);
    expect(rows[1].deep).toBeNull();
    expect(rows[2].core).toBeNull();
  });
  it('flags incomplete multi-record stage totals and invalid values', () => {
    const rows = durationHistory([{date:'2026-09-19',qty:7,core:4,deep:1,rem:2},{date:'2026-09-19',qty:1,core:-1}], '2026-09-19', 1, true);
    expect(rows[0]).toMatchObject({total:8,core:4,deep:1,rem:2,partial:true});
    expect(average(durationHistory([], '2026-09-19',7,true))).toBeNull();
  });
  it('formats durations with minute rounding and hour carry', () => {
    expect(duration(7.7,true)).toBe('7h 42m');
    expect(duration(7.999,true)).toBe('8h 00m');
    expect(duration(0,true)).toBe('0h 00m');
    expect(duration(null,true)).toBe('—');
    expect(duration(42.3,false)).toBe('42 min');
  });
  it('switches ranges and closes the modal when its selected date changes', () => {
    const card = new DurationCardComponent();
    card.kind='sleep';card.date='2026-09-19';card.records=[{date:card.date,qty:8,core:5,deep:1,rem:2}];
    card.ngOnChanges({date:true});
    expect(card.range.length).toBe(30);
    expect(card.chartData.datasets.length).toBe(4);
    card.selectRange(7);
    expect(card.range.length).toBe(7);
    expect(card.coverage).toBe(1);
    expect(card.rangeAverage).toBe(8);
    card.visible=true;card.date='2026-09-20';card.ngOnChanges({date:true});
    expect(card.visible).toBe(false);
    expect(card.selected.total).toBeNull();
  });
});
