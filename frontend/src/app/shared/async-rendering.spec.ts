import {provideZonelessChangeDetection, Type} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {MessageService} from 'primeng/api';
import {of} from 'rxjs';
import {ChoicesCardComponent} from '../modules/dayview/choices/choices-card.component';
import {WeightCardComponent} from '../modules/dayview/weight/weight-card.component';
import {HeartRateCardComponent} from '../modules/dayview/heart/heart-rate-card.component';
import {HrvCardComponent} from '../modules/dayview/heart/hrv-card.component';
import {MessagesListComponent} from '../modules/messages/messages-list.component';
import {IdentitiesComponent} from '../modules/identities/identities.component';
import {IdentityDetailComponent} from '../modules/identities/identity-detail/identity-detail.component';
import {LocationhistoryComponent} from '../modules/locationhistory/locationhistory.component';

const date = '2026-09-15';
const identity = {id: 42, firstName: 'Async identity', isGroup: false, identityInApplications: []};
const cases: {name: string; component: Type<unknown>; date?: string; path: string; response: object; expected: string}[] = [
  {name: 'Choices', component: ChoicesCardComponent, date, path: '/choices',
    response: {date, score: 3, editable: true, series: [{date, score: 3}]}, expected: '+3'},
  {name: 'weight', component: WeightCardComponent, date, path: '/health/weight-progress',
    response: {date, preferences: {weightGoalKg: 75, weightGoalStartedOn: date}, latestWeightKg: 79,
      coverage: 1, previousCoverage: 1, state: 'TOWARD', arrow: 'DOWN', series: []}, expected: 'Moving toward goal'},
  {name: 'heart rate', component: HeartRateCardComponent, date, path: '/health/heart-rate',
    response: {date, selectedDay: {date, averageBpm: 73, recordCount: 1, restingRecordCount: 0}, series: []}, expected: '73'},
  {name: 'HRV', component: HrvCardComponent, date, path: '/health/hrv',
    response: {date, state: 'UP', selectedDay: {date, averageMs: 56, recordCount: 1}, series: []}, expected: 'Improving HRV trend'},
  {name: 'messages', component: MessagesListComponent, path: '/message/list',
    response: {content: [{id: 1, body: 'Asynchronous message', timestamp: `${date}T10:00:00Z`}], totalElements: 1}, expected: 'Asynchronous message'},
  {name: 'identities', component: IdentitiesComponent, path: '/identity', response: [identity], expected: 'Async identity'},
  {name: 'identity detail', component: IdentityDetailComponent, path: '/identity', response: [identity], expected: 'Async identity'}
];

async function setup<T>(component: Type<T>, selectedDate?: string) {
  await TestBed.configureTestingModule({imports: [component], providers: [
    provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]), MessageService,
    {provide: ActivatedRoute, useValue: {queryParams: of({}), snapshot: {paramMap: {get: () => '42'}}}}
  ]}).compileComponents();
  const fixture = TestBed.createComponent(component);
  if (selectedDate) fixture.componentRef.setInput('date', selectedDate);
  fixture.autoDetectChanges();
  await fixture.whenStable();
  const http = TestBed.inject(HttpTestingController);
  const request = (path: string) => {
    const active = http.match(req => req.url.endsWith(path)).filter(req => !req.cancelled);
    expect(active).toHaveLength(1);
    return active[0];
  };
  const text = () => (fixture.nativeElement as HTMLElement).textContent;
  return {fixture, http, request, text};
}

describe('Asynchronous page rendering', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  for (const test of cases) {
    it(`renders ${test.name} HTTP responses without another event or forced change detection`, async () => {
      const {fixture, request, text} = await setup(test.component, test.date);
      if (test.component === IdentitiesComponent) {
        request('/identity-in-application/unlinked').flush([]);
        await fixture.whenStable();
      }
      expect(text()).not.toContain(test.expected);
      // Deliver after initial rendering, and deliberately do not call detectChanges.
      request(test.path).flush(test.response);
      await fixture.whenStable();
      expect(text()).toContain(test.expected);
      fixture.destroy();
    });
  }

  it('renders a weight load error and a successful retry without unrelated interaction', async () => {
    const {fixture, request, text} = await setup(WeightCardComponent, date);
    request('/health/weight-progress').flush({}, {status: 500, statusText: 'Unavailable'});
    await fixture.whenStable();
    expect(text()).toContain('Unable to load weight');
    fixture.componentInstance.load();
    await fixture.whenStable();
    expect(text()).toContain('Loading weight');
    request('/health/weight-progress').flush(cases[1].response);
    await fixture.whenStable();
    expect(text()).toContain('Moving toward goal');
    fixture.destroy();
  });

  it('reconciles Choices after a delayed write without leaving its controls locked', async () => {
    const {fixture, request, text} = await setup(ChoicesCardComponent, date);
    request('/choices').flush(cases[0].response);
    await fixture.whenStable();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Add one Choices point"]');
    button.click();
    await fixture.whenStable();
    request(`/choices/${date}/adjust`).flush({date, score: 4});
    await fixture.whenStable();
    request('/choices').flush({date, score: 4, editable: true, series: [{date, score: 4}]});
    await fixture.whenStable();
    expect(text()).toContain('+4');
    expect(button.disabled).toBe(false);
    fixture.destroy();
  });

  it('updates the heatmap data binding and error state after delayed requests', async () => {
    // Replace the external Google Maps renderer; exercise the actual page's async state bindings.
    TestBed.overrideComponent(LocationhistoryComponent, {set: {imports: [], template:
      '{{heatmapLoading ? "Loading map" : "Map ready"}} {{heatmapError ? "Map error" : ""}} Points: {{heatmapData.length}}'}});
    const {fixture, request, text} = await setup(LocationhistoryComponent);
    request('/location/sparse').flush([{latitude: 47, longitude: 8}]);
    await fixture.whenStable();
    expect(text()).toContain('Map ready');
    expect(text()).toContain('Points: 1');
    fixture.componentInstance.loadHeatmapData();
    await fixture.whenStable();
    expect(text()).toContain('Loading map');
    request('/location/sparse').flush({}, {status: 500, statusText: 'Unavailable'});
    await fixture.whenStable();
    expect(text()).toContain('Map error');
    fixture.destroy();
  });
});
