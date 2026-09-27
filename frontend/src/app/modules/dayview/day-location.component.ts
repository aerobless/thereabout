import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';

import { CardModule } from 'primeng/card';

import { DialogModule } from 'primeng/dialog';

import { GoogleMap, MapPolyline, MapMarker } from '@angular/google-maps';
import { LocationSidebarComponent } from './location-sidebar/location-sidebar.component';
import { DayViewData } from './day-view-data';
import { DayLocationState } from './day-location-state';
@Component({
  selector: 'app-day-location',
  imports: [DatePipe, NgTemplateOutlet, CardModule, DialogModule, GoogleMap, MapPolyline, MapMarker, LocationSidebarComponent],
  templateUrl: './day-location.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DayLocationComponent {
  readonly day = inject(DayViewData);
  readonly state = inject(DayLocationState);
  readonly dateChange = output<Date>();
}
