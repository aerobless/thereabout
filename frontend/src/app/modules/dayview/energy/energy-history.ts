import {DailyMetricValue} from '../../../../../generated/backend-api/thereabout';
import {shiftDay} from '../steps-progress';

export interface EnergyDay {
  date: string;
  active: number | null;
  basal: number | null;
  total: number | null;
  incomplete: boolean;
  unsupportedUnits: string[];
}

export function energyHistory(active: DailyMetricValue[], basal: DailyMetricValue[], date: string, days: number): EnergyDay[] {
  return Array.from({length: days}, (_, index) => {
    const day = shiftDay(date, index - days + 1);
    const unsupportedUnits = new Set<string>();
    const sum = (records: DailyMetricValue[]) => {
      const values: number[] = [];
      for (const record of records) {
        if (record.date !== day || typeof record.qty !== 'number' || !Number.isFinite(record.qty) || record.qty < 0) continue;
        const unit = record.units?.trim().toLowerCase() || 'kcal';
        if (unit === 'kcal') values.push(record.qty);
        else if (unit === 'kj') values.push(record.qty / 4.184);
        else unsupportedUnits.add(record.units!.trim());
      }
      return values.length ? values.reduce((total, value) => total + value, 0) : null;
    };
    const activeValue = sum(active);
    const basalValue = sum(basal);
    const incomplete = (activeValue === null) !== (basalValue === null) || unsupportedUnits.size > 0;
    return {
      date: day, active: activeValue, basal: basalValue, incomplete,
      total: !incomplete && activeValue !== null && basalValue !== null ? activeValue + basalValue : null,
      unsupportedUnits: [...unsupportedUnits]
    };
  });
}

export function formatEnergy(value: number | null | undefined): string {
  return value == null ? '—' : `${Math.round(value).toLocaleString()} kcal`;
}
