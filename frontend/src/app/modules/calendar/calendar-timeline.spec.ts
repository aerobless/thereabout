import {describe, expect, it} from 'vitest';
import {CalendarOccurrence} from '../../../../generated/backend-api/thereabout';
import {timeline} from './calendar-timeline';

function event(key: string, start: string, end: string, allDay = false): CalendarOccurrence {
  return {key, title: key, start, end, allDay} as CalendarOccurrence;
}
describe('calendar timeline', () => {
  it('places overlapping events in separate lanes and reuses a free lane', () => {
    const blocks = timeline([
      event('a','2026-09-24T09:00:00','2026-09-24T11:00:00'),
      event('b','2026-09-24T10:00:00','2026-09-24T12:00:00'),
      event('c','2026-09-24T11:00:00','2026-09-24T12:00:00')
    ],'2026-09-24');
    expect(blocks.map(b => b.lane)).toEqual([0,1,0]);
    expect(blocks[0].left).toBeCloseTo(37.5);
    expect(blocks[0].width).toBeCloseTo(100/12);
  });
  it('clips cross-midnight events and separates all-day events', () => {
    const blocks = timeline([
      event('a','2026-09-23T23:00:00','2026-09-24T01:00:00'),
      event('b','2026-09-24T23:00:00','2026-09-25T03:00:00'),
      event('all','2026-09-24T00:00:00','2026-09-25T00:00:00',true)
    ],'2026-09-24');
    expect(blocks).toHaveLength(2);
    expect(blocks[0].left).toBe(0);
    expect(blocks[0].width).toBeCloseTo(100/24);
    expect(blocks[1].left + blocks[1].width).toBeCloseTo(100);
  });
  it('keeps very short event buttons separated', () => {
    const blocks = timeline([
      event('a','2026-09-24T09:00:00','2026-09-24T09:01:00'),
      event('b','2026-09-24T09:01:00','2026-09-24T09:02:00')
    ],'2026-09-24');
    expect(blocks.map(b => b.lane)).toEqual([0,1]);
  });
});
