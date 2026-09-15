import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Observable, Subject, of} from 'rxjs';
import {Mock, vi} from 'vitest';
import {WeightCardComponent} from './weight-card.component';
import {Preferences, PreferencesService, UpdatePreferences, WeightProgress, WeightService} from '../../../../../generated/backend-api/thereabout';

describe('WeightCardComponent', () => {
  let fixture: ComponentFixture<WeightCardComponent>;
  let component: WeightCardComponent;
  let getWeightProgress: Mock<(date: string, days: 7 | 30) => Observable<WeightProgress>>;
  let updatePreferences: Mock<(value: UpdatePreferences) => Observable<Preferences>>;
  const preferences: Preferences = {weightGoalKg: 75, weightGoalStartedOn: '2026-09-01'};
  const result = (date = '2026-09-15'): WeightProgress => ({
    date, preferences, zoneMinKg: 74.5, zoneMaxKg: 75.5, coverage: 1, previousCoverage: 1,
    latestWeightKg: 79, latestWeightDate: date, trendKg: 79, previousTrendKg: 80,
    gapKg: 4, weeklyGapChangeKg: -1, state: 'TOWARD', arrow: 'DOWN',
    series: [{date, readingKg: 79, trendKg: 79, coverage: 1}]
  });
  beforeEach(async () => {
    getWeightProgress = vi.fn().mockReturnValue(of(result()));
    updatePreferences = vi.fn();
    await TestBed.configureTestingModule({imports: [WeightCardComponent], providers: [
      {provide: WeightService, useValue: {getWeightProgress}}, {provide: PreferencesService, useValue: {updatePreferences}}
    ]}).overrideComponent(WeightCardComponent, {set: {template: ''}}).compileComponents();
    fixture = TestBed.createComponent(WeightCardComponent);
    component = fixture.componentInstance;
    component.date = '2026-09-15';
  });
  it('defaults to 30 days and changes only the independent weight range', () => {
    component.ngOnChanges();
    expect(getWeightProgress).toHaveBeenLastCalledWith('2026-09-15', 30);
    component.visible = true;
    component.selectRange(7);
    expect(component.visible).toBe(true);
    expect(getWeightProgress).toHaveBeenLastCalledWith('2026-09-15', 7);
    component.selectRange(7);
    expect(getWeightProgress).toHaveBeenCalledTimes(2);
    expect(component.chartData?.datasets.map(dataset => dataset.label)).toContain('Target zone');
  });
  it('clears old progress, closes the dialog and ignores old responses and errors', () => {
    const old = new Subject<WeightProgress>();
    const current = new Subject<WeightProgress>();
    getWeightProgress.mockReturnValueOnce(old).mockReturnValueOnce(current);
    component.ngOnChanges();
    old.next(result());
    component.visible = true;
    component.date = '2026-09-16';
    component.ngOnChanges();
    expect(component.visible).toBe(false);
    expect(component.progress).toBeNull();
    expect(component.loading).toBe(true);
    current.next(result('2026-09-16'));
    old.next(result());
    old.error(new Error('stale'));
    expect(component.progress?.date).toBe('2026-09-16');
    expect(component.error).toBe(false);
  });
  it('distinguishes loading, errors and empty history and preserves chart gaps', () => {
    const request = new Subject<WeightProgress>();
    getWeightProgress.mockReturnValue(request);
    component.load();
    expect(component.loading).toBe(true);
    request.error(new Error('offline'));
    expect(component.error).toBe(true);
    expect(component.loading).toBe(false);
    getWeightProgress.mockReturnValue(of({...result(), state: 'NO_DATA', trendKg: undefined,
      latestWeightKg: undefined, coverage: 0, series: [{date: component.date, coverage: 0}]}));
    component.load();
    expect(component.error).toBe(false);
    expect(component.status).toBe('No recent weigh-ins');
    expect(component.chartData?.datasets[3].data).toEqual([null]);
  });
  it('keeps colour independent from weight direction', () => {
    component.progress = {...result(), state: 'TOWARD', arrow: 'UP'};
    expect(component.arrow).toBe('↑');
    expect(component.status).toBe('Moving toward goal');
    component.progress = {...result(), state: 'AWAY', arrow: 'DOWN'};
    expect(component.arrow).toBe('↓');
    expect(component.status).toBe('Moving away from goal');
  });
  it('validates positive finite goals with at most one decimal place', () => {
    for (const invalid of [null, 0, -1, NaN, Infinity, 75.12]) {
      component.goalDraft = invalid;
      expect(component.validGoal).toBe(false);
      component.saveGoal();
    }
    expect(updatePreferences).not.toHaveBeenCalled();
    for (const valid of [0.1, 75, 75.1]) {
      component.goalDraft = valid;
      expect(component.validGoal).toBe(true);
    }
  });
  it('preserves previous goal and draft on save failure, and cancel discards the draft', () => {
    component.load();
    component.editGoal();
    component.goalDraft = 78.5;
    const request = new Subject<Preferences>();
    updatePreferences.mockReturnValue(request);
    component.saveGoal();
    expect(component.saving).toBe(true);
    request.error(new Error('offline'));
    expect(component.saving).toBe(false);
    expect(component.editing).toBe(true);
    expect(component.goalDraft).toBe(78.5);
    expect(component.preferences?.weightGoalKg).toBe(75);
    expect(component.saveError).toContain('Could not save');
    component.cancelEdit();
    expect(component.goalDraft).toBe(75);
    expect(component.editing).toBe(false);
  });
  it('refreshes the selected historical date after saving the new current challenge', () => {
    component.load();
    component.editGoal();
    component.goalDraft = 78;
    const saved = {...preferences, weightGoalKg: 78, weightGoalStartedOn: '2026-09-15'};
    updatePreferences.mockReturnValue(of(saved));
    component.date = '2026-08-01';
    getWeightProgress.mockReturnValue(of({...result(component.date), preferences: saved, state: 'BEFORE_CHALLENGE'}));
    component.saveGoal();
    expect(updatePreferences).toHaveBeenCalledWith({weightGoalKg: 78});
    expect(getWeightProgress).toHaveBeenLastCalledWith('2026-08-01', 30);
    expect(component.preferences).toEqual(saved);
    expect(component.editing).toBe(false);
    expect(component.status).toBe('Before current goal');
  });
});
