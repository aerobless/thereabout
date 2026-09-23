import {energyHistory, formatEnergy} from './energy-history';

describe('energy history', () => {
  it('sums daily records and converts kJ while defaulting absent units to kcal', () => {
    const [day] = energyHistory([
      {date: '2026-09-23', qty: 100}, {date: '2026-09-23', qty: 418.4, units: 'kJ'},
      {date: '2026-09-23', qty: 50, units: ' kcal '}
    ], [{date: '2026-09-23', qty: 1500, units: 'kcal'}], '2026-09-23', 1);
    expect(day.active).toBeCloseTo(250);
    expect(day.total).toBeCloseTo(1750);
    expect(day.incomplete).toBe(false);
  });

  it('preserves zeros, gaps and incomplete days without inventing totals', () => {
    const rows = energyHistory([{date: '2026-09-22', qty: 0}, {date: '2026-09-23', qty: 20}],
      [{date: '2026-09-22', qty: 0}], '2026-09-23', 3);
    expect(rows.map(day => day.total)).toEqual([null, 0, null]);
    expect(rows.map(day => day.incomplete)).toEqual([false, false, true]);
    expect(rows[2]).toMatchObject({active: 20, basal: null});
    expect(formatEnergy(0)).toBe('0 kcal');
    expect(formatEnergy(null)).toBe('—');
  });

  it('excludes invalid quantities and identifies unsupported units without mixing them', () => {
    const [day] = energyHistory([
      {date: '2026-09-23', qty: -1}, {date: '2026-09-23', qty: NaN},
      {date: '2026-09-23', qty: Infinity}, {date: '2026-09-23'},
      {date: '2026-09-23', qty: 20, units: 'J'}, {date: '2026-09-23', qty: 10}
    ], [{date: '2026-09-23', qty: 100}], '2026-09-23', 1);
    expect(day).toMatchObject({active: 10, basal: 100, total: null, incomplete: true, unsupportedUnits: ['J']});
  });

  it('builds inclusive calendar ranges across leap days, DST and year boundaries', () => {
    const leap = energyHistory([], [], '2024-03-01', 7);
    expect(leap).toHaveLength(7);
    expect(leap[0].date).toBe('2024-02-24');
    expect(leap[5].date).toBe('2024-02-29');
    expect(leap[6].date).toBe('2024-03-01');
    const year = energyHistory([], [], '2026-01-01', 30);
    expect(year).toHaveLength(30);
    expect(year[0].date).toBe('2025-12-03');
    expect(year[29].date).toBe('2026-01-01');
    expect(energyHistory([], [], '2026-03-30', 3).map(day => day.date))
      .toEqual(['2026-03-28', '2026-03-29', '2026-03-30']);
  });
});
