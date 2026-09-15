import {DailyMetricValue} from '../../../../generated/backend-api/thereabout';

export type StepLevel = 'below' | 'almost' | 'reached' | 'bonus' | 'exceptional';
export interface StepProgress {
  date: string;
  steps: number | null;
  baseline: number | null;
  coverage: number;
  percentage: number | null;
  level: StepLevel | null;
  label: string;
  nextLevel: string | null;
  stepsToNextLevel: number | null;
}

const LEVELS: {ratio: number; level: StepLevel; label: string}[] = [
  {ratio: 0, level: 'below', label: 'Below baseline'},
  {ratio: 0.9, level: 'almost', label: 'Almost there'},
  {ratio: 1, level: 'reached', label: 'Baseline reached'},
  {ratio: 1.1, level: 'bonus', label: 'Bonus'},
  {ratio: 1.25, level: 'exceptional', label: 'Exceptional'}
];

// Calendar arithmetic avoids UTC parsing and daylight-saving offsets.
export function shiftDay(date: string, offset: number): string {
  const [year, month, day] = date.split('-').map(Number);
  const shifted = new Date(year, month - 1, day + offset, 12);
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}-${String(shifted.getDate()).padStart(2, '0')}`;
}

export function dailyStepTotals(samples: DailyMetricValue[]): Map<string, number> {
  const totals = new Map<string, number>();
  for (const sample of samples) {
    const {date, qty} = sample;
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date) || shiftDay(date, 0) !== date ||
        typeof qty !== 'number' || !Number.isFinite(qty) || qty < 0) continue;
    totals.set(date, (totals.get(date) ?? 0) + qty);
  }
  return totals;
}

export function stepProgress(date: string, totals: Map<string, number>): StepProgress {
  const history = Array.from({length: 30}, (_, i) => totals.get(shiftDay(date, -i - 1)))
    .filter((steps): steps is number => steps !== undefined);
  const baseline = history.length ? history.reduce((sum, steps) => sum + steps, 0) / history.length : null;
  const steps = totals.get(date) ?? null;
  const ratio = steps !== null && baseline !== null && baseline > 0 ? steps / baseline : null;
  const index = ratio === null ? -1 : LEVELS.filter(level => ratio >= level.ratio).length - 1;
  const current = LEVELS[index];
  const next = index >= 0 ? LEVELS[index + 1] : undefined;
  return {
    date, steps, baseline, coverage: history.length,
    percentage: ratio === null ? null : ratio * 100,
    level: current?.level ?? null,
    label: steps === null ? 'No step data' : current?.label ?? 'Baseline unavailable',
    nextLevel: next?.label ?? null,
    stepsToNextLevel: next && baseline !== null && steps !== null
      ? Math.max(1, Math.ceil(baseline * next.ratio - steps)) : null
  };
}

export function stepHistory(date: string, totals: Map<string, number>): StepProgress[] {
  return Array.from({length: 30}, (_, i) => stepProgress(shiftDay(date, i - 29), totals));
}
