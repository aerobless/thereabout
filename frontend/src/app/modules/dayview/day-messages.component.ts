import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';

import { DayViewData } from './day-view-data';
import { DayMessageData } from './day-message-data';
@Component({
  selector: 'app-day-messages',
  imports: [DatePipe, RouterLink, DialogModule, TableModule, TooltipModule],
  templateUrl: './day-messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DayMessagesComponent {
  readonly day = inject(DayViewData);
  readonly state = inject(DayMessageData);
}
