import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {DatePickerModule} from 'primeng/datepicker';
import {TableModule} from 'primeng/table';
import {TooltipModule} from 'primeng/tooltip';
import {DialogModule} from 'primeng/dialog';
import {InputNumberModule} from 'primeng/inputnumber';
import {TextareaModule} from 'primeng/textarea';
import {LocationHistoryEntry} from '../../../../../generated/backend-api/thereabout';

export interface LocationEditDraft {
  entry: LocationHistoryEntry;
  date: Date | null;
}

@Component({
  selector: 'thereabout-location-sidebar',
  imports: [FormsModule, ButtonModule, DatePickerModule, TableModule, TooltipModule, DialogModule, InputNumberModule, TextareaModule],
  templateUrl: './location-sidebar.component.html',
  styleUrl: './location-sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class LocationSidebarComponent {
  @Input() date = new Date();
  @Input() entries: LocationHistoryEntry[] = [];
  @Input() selection: LocationHistoryEntry[] = [];
  @Input() draft: LocationEditDraft | null = null;
  @Input() busy = false;
  @Input() saving = false;
  @Input() loading = false;
  @Input() error = false;
  @Output() dateChange = new EventEmitter<Date>();
  @Output() selectionChange = new EventEmitter<LocationHistoryEntry[]>();
  @Output() highlight = new EventEmitter<LocationHistoryEntry | undefined>();
  @Output() locate = new EventEmitter<void>();
  @Output() photos = new EventEmitter<void>();
  @Output() create = new EventEmitter<void>();
  @Output() edit = new EventEmitter<void>();
  @Output() remove = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  @Output() save = new EventEmitter<void>();

  get validDraft(): boolean {
    return !!this.draft?.date && Number.isFinite(this.draft.date.getTime());
  }

  changeDate(date: Date | null) {
    if (!this.saving && date && Number.isFinite(date.getTime())) this.dateChange.emit(new Date(date));
  }

  shiftDate(days: number) {
    const date = new Date(this.date);
    date.setDate(date.getDate() + days);
    this.changeDate(date);
  }

  today() { this.changeDate(new Date()); }

  convertToLocalTime(timestamp: string) {
    return new Date(timestamp).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit', hour12: false});
  }

  shortCoordinates(lat: number, lng: number) {
    return `${lat.toFixed(5)},\n ${lng.toFixed(5)}`;
  }
}
