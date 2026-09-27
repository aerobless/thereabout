import { Injectable, signal } from '@angular/core';

/** Date context shared only by one mounted Day View and its cards. */
@Injectable()
export class DayViewData {
  readonly selectedDate = signal(new Date());
}
