import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';

import { TooltipModule } from 'primeng/tooltip';

import { DayHealthData } from './day-health-data';
@Component({
  selector: 'app-day-workouts',
  imports: [CardModule, TableModule, TooltipModule],
  templateUrl: './day-workouts.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DayWorkoutsComponent {
  readonly state = inject(DayHealthData);
  formatWorkoutTime(start: string | undefined): string {
    if (!start) return '--';
    return new Date(start).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }
  formatDuration(seconds: number | undefined): string {
    if (!seconds) return '--';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);

    if (hours > 0 && minutes > 0) {
      return `${hours}h ${minutes}m`;
    } else if (hours > 0) {
      return `${hours}h`;
    } else {
      return `${minutes}m`;
    }
  }
  formatEnergy(energy: number | undefined, units: string | undefined): string {
    if (energy === undefined || energy === null) return '--';
    const unitsStr = units || '';
    return `${Math.round(energy)} ${unitsStr}`.trim();
  }
}
