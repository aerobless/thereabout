import {ChangeDetectionStrategy, Component, signal, provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {Subject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {CalendarOccurrence, CalendarService} from '../../../../generated/backend-api/thereabout';
import {CalendarCardComponent} from './calendar-card.component';

@Component({
  imports: [CalendarCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button (click)="date.set('2026-09-26')">Previous day</button>
    <button (click)="date.set('2026-09-27')">Next day</button>
    <app-calendar-card [date]="date()" />
  `
})
class DayViewHost {
  readonly date = signal('2026-09-27');
}

function occurrence(date: string, title: string): CalendarOccurrence {
  return {
    key: title, title, calendarId: 1, calendarName: 'Test calendar', color: null,
    syncing: false, eventId: title, originalStart: null, recurring: false,
    location: null, description: null, start: `${date}T10:00:00`, end: `${date}T11:00:00`,
    allDay: false, startDate: null, endDate: null, timeZone: 'Europe/Zurich', canDelete: true, guests: []
  };
}

async function setup() {
  const requests: {date: string; response: Subject<CalendarOccurrence[]>}[] = [];
  const api = {getCalendarDay: vi.fn((date: string) => {
    const response = new Subject<CalendarOccurrence[]>();
    requests.push({date, response});
    return response;
  })};
  await TestBed.configureTestingModule({imports: [DayViewHost], providers: [
    provideZonelessChangeDetection(), provideRouter([]), {provide: CalendarService, useValue: api}, MessageService
  ]}).compileComponents();
  const fixture = TestBed.createComponent(DayViewHost);
  fixture.autoDetectChanges();
  await fixture.whenStable();
  const text = () => fixture.nativeElement.textContent as string;
  const click = async (label: string) => {
    const button = [...fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>]
      .find(element => element.textContent?.trim() === label);
    expect(button).toBeDefined();
    button!.click();
    await fixture.whenStable();
  };
  // No manual detectChanges after responses: the component must schedule its own rendering.
  const respond = async (index: number, events: CalendarOccurrence[]) => {
    requests[index].response.next(events);
    requests[index].response.complete();
    await fixture.whenStable();
  };
  return {fixture, requests, text, click, respond};
}

describe('CalendarCardComponent async rendering under Day View', () => {
  it('renders initial and previous/next day responses without another interaction', async () => {
    const {fixture, requests, text, click, respond} = await setup();
    expect(text()).toContain('Loading events');
    await respond(0, [occurrence('2026-09-27', 'Sunday appointment')]);
    expect(text()).toContain('Sunday appointment');
    expect(text()).not.toContain('Loading events');
    await click('Previous day');
    expect(requests[1].date).toBe('2026-09-26');
    expect(text()).toContain('Loading events');
    await respond(1, [occurrence('2026-09-26', 'Saturday appointment')]);
    expect(text()).toContain('Saturday appointment');
    expect(text()).not.toContain('Sunday appointment');
    await click('Next day');
    expect(requests[2].date).toBe('2026-09-27');
    await respond(2, []);
    expect(text()).toContain('No events for this day.');
    expect(text()).not.toContain('Loading events');
    fixture.destroy();
  });

  it('shows an asynchronous error and a successful retry without another click', async () => {
    const {fixture, requests, text, click, respond} = await setup();
    requests[0].response.error(new Error('Unavailable'));
    await fixture.whenStable();
    expect(text()).toContain('Unable to load calendar events');
    expect(text()).not.toContain('Loading events');
    await click('Retry');
    await respond(1, [occurrence('2026-09-27', 'Recovered appointment')]);
    expect(text()).toContain('Recovered appointment');
    expect(text()).not.toContain('Unable to load calendar events');
    fixture.destroy();
  });

  it('keeps a delayed response from an older day from replacing the selected day', async () => {
    const {fixture, requests, text, click, respond} = await setup();
    await click('Previous day');
    await click('Next day');
    await respond(2, [occurrence('2026-09-27', 'Current appointment')]);
    await respond(1, [occurrence('2026-09-26', 'Stale appointment')]);
    expect(text()).toContain('Current appointment');
    expect(text()).not.toContain('Stale appointment');
    fixture.destroy();
  });
});
