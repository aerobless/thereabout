import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';

import { AppModalComponent } from '../../shared/modal/app-modal.component';

import { ChartModule } from 'primeng/chart';

import { DayViewData } from './day-view-data';
import { DayHealthData } from './day-health-data';
@Component({
  selector: 'app-day-steps',
  imports: [DatePipe, AppModalComponent, ChartModule],
  templateUrl: './day-steps.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DayStepsComponent {
  readonly day = inject(DayViewData);
  readonly state = inject(DayHealthData);
}
