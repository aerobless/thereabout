import {CalendarOccurrence} from '../../../../generated/backend-api/thereabout';
export interface CalendarBlock { event: CalendarOccurrence; left: number; width: number; lane: number; }
export function timeline(events: CalendarOccurrence[], date: string): CalendarBlock[] {
  const start = new Date(`${date}T00:00:00`).getTime();
  const next = new Date(start); next.setDate(next.getDate() + 1);
  const duration = next.getTime() - start;
  const ends: number[] = [];
  return events.filter(event => !event.allDay).slice().sort((a,b) => a.start.localeCompare(b.start) || a.key.localeCompare(b.key)).map(event => {
    const from = Math.max(start, new Date(event.start).getTime());
    const to = Math.min(next.getTime(), new Date(event.end).getTime());
    const left = Math.max(0, Math.min(100, (from - start) / duration * 100));
    const width = Math.min(100 - left, Math.max(1, (to - from) / duration * 100));
    // Labels sit above duration bars. Reserve their space without distorting duration widths.
    const labelWidth = Math.min(140, Math.max(24, event.title.length * 7)) / 720 * 100;
    let lane = ends.findIndex(end => end <= left);
    if (lane < 0) lane = ends.length;
    ends[lane] = left + Math.max(width, labelWidth);
    return {event, left, width, lane};
  });
}
