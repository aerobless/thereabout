import {Component, ChangeDetectionStrategy, DestroyRef, inject, Input, OnChanges} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {interval, finalize} from 'rxjs';
import {CardModule} from 'primeng/card';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {CalendarOccurrence, CalendarService} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {CalendarBlock, timeline} from './calendar-timeline';

@Component({
  selector: 'app-calendar-card',
  imports: [DatePipe, RouterLink, CardModule, DialogModule, ButtonModule],
  templateUrl: './calendar-card.component.html',
  styleUrl: './calendar-card.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CalendarCardComponent implements OnChanges {
  @Input({required: true}) date = '';
  private readonly api = inject(CalendarService);
  private readonly toast = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh = registerRefresh(() => this.load(), () => this.deleting);
  private requestId = 0;
  private positionInitialTimeline = true;
  private trigger: HTMLElement | null = null;
  events: CalendarOccurrence[] = [];
  blocks: CalendarBlock[] = [];
  selected: CalendarOccurrence | null = null;
  visible = false;
  deleting = false;
  loading = false;
  error = '';
  deleteError = '';
  initialScrollLeft = 0;
  readonly viewTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  readonly hours = [0,6,12,18,24];
  constructor() {
    interval(30000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden && !this.deleting) this.load();
    });
  }
  ngOnChanges() { this.visible = false; this.selected = null; this.events = []; this.blocks = []; this.positionInitialTimeline = true; this.initialScrollLeft = 0; this.load(); }
  get lastAllDayDate() {
    if (!this.selected?.endDate) return this.selected?.startDate;
    const date = new Date(`${this.selected.endDate}T12:00:00`); date.setDate(date.getDate() - 1);
    return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  }
  get allDay() { return this.events.filter(e => e.allDay); }
  get height() { return Math.max(1, ...this.blocks.map(b => b.lane + 1)) * 38 + 32; }
  hourPosition(hour: number) {
    const from = new Date(`${this.date}T00:00:00`);
    const next = new Date(from); next.setDate(next.getDate()+1);
    const tick = new Date(from); tick.setHours(hour);
    return (tick.getTime()-from.getTime())/(next.getTime()-from.getTime())*100;
  }
  load() {
    if (!this.date) return;
    const id = ++this.requestId; this.loading = true;
    this.api.getCalendarDay(this.date, this.viewTimeZone)
      .pipe(this.refresh.track('calendar'), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: events => {
          if (id !== this.requestId) return;
          this.events = events; this.blocks = timeline(events,this.date); this.loading = false; this.error = '';
          if (this.positionInitialTimeline) {
            this.initialScrollLeft = window.innerWidth < 768 && this.blocks.length ? Math.max(0, this.blocks[0].left * 7.2 - 12) : 0;
            this.positionInitialTimeline = false;
          }
          if (this.selected) {
            const refreshed = events.find(e => e.key === this.selected?.key);
            if (refreshed) this.selected = refreshed;
            else { this.visible = false; this.selected = null; }
          }
        },
        error: () => { if (id === this.requestId) { this.loading = false; this.error = 'Unable to load calendar events. Previous data is still shown.'; } }
      });
  }
  open(event: CalendarOccurrence, trigger: Event) {
    this.selected = event; this.visible = true; this.deleteError = '';
    this.trigger = trigger.currentTarget as HTMLElement;
  }
  closed() { this.trigger?.focus(); }
  remove() {
    const event = this.selected;
    if (!event?.canDelete || this.deleting) return;
    this.deleting = true; this.deleteError = ''; ++this.requestId;
    this.api.deleteCalendarEvent(event.calendarId,event.eventId,event.originalStart ?? undefined)
      .pipe(finalize(() => this.deleting = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.events = this.events.filter(e => e.key !== event.key);
          this.blocks = timeline(this.events,this.date);
          if (this.selected?.key === event.key) { this.visible = false; this.selected = null; }
          this.loading = false;
          this.toast.add({severity: 'success', summary: event.recurring ? 'Occurrence deleted from Thereabout' : 'Event deleted from Thereabout'});
        },
        error: () => { this.loading = false; this.deleteError = 'Unable to delete from Thereabout. The event has been kept. Please try again.'; }
      });
  }
}
