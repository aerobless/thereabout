import {dailyStepTotals, shiftDay, stepHistory, stepProgress} from './steps-progress';

const day = '2026-09-15';
function totalsWithBaseline(baseline: number, steps: number): Map<string, number> {
  return new Map([[shiftDay(day, -1), baseline], [day, steps]]);
}

describe('steps progress', () => {
  it('sums valid samples per API date without turning missing or invalid values into zero', () => {
    const totals = dailyStepTotals([
      {date: day, qty: 200}, {date: day, qty: 350}, {date: '2026-09-14', qty: 0},
      {date: '2026-09-13'}, {date: day, qty: -1}, {date: day, qty: NaN},
      {date: day, qty: Infinity}, {date: '2026-02-30', qty: 99}, {qty: 400}
    ]);
    expect([...totals]).toEqual([[day, 550], ['2026-09-14', 0]]);
  });

  it.each([
    [8999, 'below'], [9000, 'almost'], [9999, 'almost'], [10000, 'reached'],
    [10999, 'reached'], [11000, 'bonus'], [12499, 'bonus'], [12500, 'exceptional'], [20000, 'exceptional']
  ])('classifies %i steps against 10000 without rounding thresholds', (steps, level) => {
    expect(stepProgress(day, totalsWithBaseline(10000, steps)).level).toBe(level);
  });

  it('excludes the selected day and days outside the preceding 30-day window', () => {
    const totals = new Map([[day, 100000], [shiftDay(day, -1), 1000], [shiftDay(day, -30), 3000], [shiftDay(day, -31), 90000]]);
    expect(stepProgress(day, totals)).toMatchObject({baseline: 2000, coverage: 2, steps: 100000});
  });

  it('includes recorded zeroes but excludes missing days from the denominator', () => {
    const totals = new Map([[day, 6000], [shiftDay(day, -1), 10000], [shiftDay(day, -2), 0]]);
    expect(stepProgress(day, totals)).toMatchObject({baseline: 5000, coverage: 2, percentage: 120, level: 'bonus'});
  });

  it('has no percentage or level without a positive baseline or daily count', () => {
    for (const totals of [new Map([[day, 5000]]), totalsWithBaseline(0, 5000), new Map([[shiftDay(day, -1), 5000]])]) {
      expect(stepProgress(day, totals)).toMatchObject({percentage: null, level: null, stepsToNextLevel: null});
    }
  });

  it('calculates the next threshold and stops adding goals at exceptional', () => {
    expect(stepProgress(day, totalsWithBaseline(10000, 9500))).toMatchObject({nextLevel: 'Baseline reached', stepsToNextLevel: 500});
    expect(stepProgress(day, totalsWithBaseline(10000, 11000))).toMatchObject({nextLevel: 'Exceptional', stepsToNextLevel: 1500});
    expect(stepProgress(day, totalsWithBaseline(10000, 12500))).toMatchObject({nextLevel: null, stepsToNextLevel: null});
  });

  it('gives every chart day its own previous baseline with all 60 loaded dates', () => {
    const totals = new Map(Array.from({length: 60}, (_, i) => [shiftDay(day, i - 59), (i + 1) * 100]));
    const history = stepHistory(day, totals);
    expect(history).toHaveLength(30);
    expect(history[0]).toMatchObject({date: shiftDay(day, -29), steps: 3100, baseline: 1550, coverage: 30});
    expect(history[29]).toMatchObject({date: day, steps: 6000, baseline: 4450, coverage: 30});
  });

  it('keeps missing chart dates as gaps', () => {
    const history = stepHistory(day, new Map([[day, 5000]]));
    expect(history[28].steps).toBeNull();
    expect(history[29].steps).toBe(5000);
  });

  it.each([
    ['2026-01-01', -1, '2025-12-31'], ['2024-03-01', -1, '2024-02-29'],
    ['2026-03-30', -1, '2026-03-29'], ['2026-10-26', -1, '2026-10-25']
  ])('uses calendar dates across year, leap-day and daylight-saving boundaries', (date, offset, result) => {
    expect(shiftDay(date, offset)).toBe(result);
  });
});
