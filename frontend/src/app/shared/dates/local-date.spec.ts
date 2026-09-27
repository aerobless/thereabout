import {localDateString, parseLocalDate} from './local-date';

describe('local calendar dates', () => {
  it('round-trips leap days and rejects normalized invalid dates', () => {
    expect(localDateString(parseLocalDate('2024-02-29')!)).toBe('2024-02-29');
    expect(parseLocalDate('2026-02-29')).toBeNull();
    expect(parseLocalDate('2026-13-01')).toBeNull();
    expect(parseLocalDate('2026-09-27T23:00:00Z')).toBeNull();
    expect(parseLocalDate(undefined)).toBeNull();
  });
});
