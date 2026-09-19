import {DailyMetricValue} from '../../../../../generated/backend-api/thereabout';
import {shiftDay} from '../steps-progress';

export interface DurationDay { date: string; total: number | null; core: number | null; deep: number | null; rem: number | null; partial: boolean; }
export const stages = ['core', 'deep', 'rem'] as const;
const valid = (n: unknown): n is number => typeof n === 'number' && Number.isFinite(n) && n >= 0;
export function durationHistory(records: DailyMetricValue[], date: string, days: number, sleep: boolean): DurationDay[] {
  return Array.from({length: days}, (_, i) => {
    const day = shiftDay(date, i - days + 1);
    const samples = records.filter(r => r.date === day);
    const sum = (key: 'qty' | typeof stages[number]) => {
      const values = samples.map(r => r[key]).filter(valid);
      return values.length ? values.reduce((a, b) => a + b, 0) : null;
    };
    const result: DurationDay = {date: day, total: sum('qty'), core: sum('core'), deep: sum('deep'), rem: sum('rem'), partial: false};
    const stageTotal = stages.reduce((a, key) => a + (result[key] ?? 0), 0);
    result.partial = sleep && stages.some(key => result[key] !== null) &&
      (samples.some(r => stages.some(key => !valid(r[key]))) || result.total === null || Math.abs(stageTotal - result.total) > .05);
    return result;
  });
}
export function average(days: DurationDay[]): number | null {
  const values = days.map(d => d.total).filter(valid);
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}
export function duration(value: number | null, sleep: boolean): string {
  if (value === null) return '—';
  if (!sleep) return `${Math.round(value).toLocaleString()} min`;
  const minutes = Math.round(value * 60);
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`;
}
