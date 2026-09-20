import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {of, Subject, throwError} from 'rxjs';
import {vi} from 'vitest';
import {MessageService as ToastService} from 'primeng/api';
import {ChoicesService, WeightService, HeartService, PreferencesService, LocationService, HealthService, MessageService, StatisticsService, IdentityService, IdentityInApplicationService, FrontendService} from '../../../../generated/backend-api/thereabout';
import {DayviewComponent} from '../../modules/dayview/dayview.component';
import {ChoicesCardComponent} from '../../modules/dayview/choices/choices-card.component';
import {WeightCardComponent} from '../../modules/dayview/weight/weight-card.component';
import {HeartRateCardComponent} from '../../modules/dayview/heart/heart-rate-card.component';
import {HrvCardComponent} from '../../modules/dayview/heart/hrv-card.component';
import {MessagesListComponent} from '../../modules/messages/messages-list.component';
import {LocationhistoryComponent} from '../../modules/locationhistory/locationhistory.component';
import {StatisticsComponent} from '../../modules/statistics/statistics.component';
import {IdentitiesComponent} from '../../modules/identities/identities.component';
import {IdentityDetailComponent} from '../../modules/identities/identity-detail/identity-detail.component';
import {ConfigurationComponent} from '../../modules/configuration/configuration.component';
import {provideHttpClient} from '@angular/common/http';
import {RefreshCoordinator} from './refresh-coordinator';

describe('Page refresh integration', () => {
  const components = [DayviewComponent, ChoicesCardComponent, WeightCardComponent, HeartRateCardComponent, HrvCardComponent, MessagesListComponent, LocationhistoryComponent, StatisticsComponent, IdentitiesComponent, IdentityDetailComponent, ConfigurationComponent];
  const locations = {getLocations: vi.fn(), getSparseLocations: vi.fn()};
  const health = {getHealthDataByDateRange: vi.fn()};
  const messages = {getMessages: vi.fn(), getMessageList: vi.fn()};
  const choices = {getChoicesHistory: vi.fn()};
  const weight = {getWeightProgress: vi.fn()};
  const heart = {getHeartRateHistory: vi.fn(), getHrvHistory: vi.fn()};
  const identities = {getIdentities: vi.fn()};
  const linked = {getUnlinkedIdentityInApplications: vi.fn(), getIdentityInApplicationsByApplication: vi.fn()};
  const stats = {getStatistics: vi.fn()};
  const config = {getFrontendConfiguration: vi.fn(), fileImportStatus: vi.fn(), getTelegramStatus: vi.fn()};
  beforeEach(async () => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({imports: components, providers: [provideRouter([]), provideHttpClient(), ToastService,
      {provide: ActivatedRoute, useValue: {queryParams: of({}), snapshot: {paramMap: {get: () => '42'}}}},
      {provide: LocationService, useValue: locations}, {provide: HealthService, useValue: health},
      {provide: MessageService, useValue: messages}, {provide: ChoicesService, useValue: choices},
      {provide: WeightService, useValue: weight}, {provide: PreferencesService, useValue: {}},
      {provide: HeartService, useValue: heart}, {provide: IdentityService, useValue: identities},
      {provide: IdentityInApplicationService, useValue: linked}, {provide: StatisticsService, useValue: stats},
      {provide: FrontendService, useValue: config}
    ]});
    for (const component of components) TestBed.overrideComponent(component, {set: {template: ''}});
    await TestBed.compileComponents();
  });

  it('refreshes all seven Day View sources without changing dates, map position or card ranges', async () => {
    // Pending responses let us inspect preserved data and completion across independent cards.
    const pending = new Subject<never>();
    for (const fn of [locations.getLocations, health.getHealthDataByDateRange, messages.getMessages, choices.getChoicesHistory, weight.getWeightProgress, heart.getHeartRateHistory, heart.getHrvHistory]) fn.mockReturnValue(pending);
    const page = TestBed.createComponent(DayviewComponent).componentInstance;
    page.selectedDate = new Date(2026, 8, 15); page.center = {lat: 12, lng: 34}; page.zoom = 9; page.selectedDaySteps = 1234;
    const cards = [TestBed.createComponent(ChoicesCardComponent).componentInstance, TestBed.createComponent(WeightCardComponent).componentInstance, TestBed.createComponent(HeartRateCardComponent).componentInstance, TestBed.createComponent(HrvCardComponent).componentInstance];
    for (const card of cards) { card.date = '2026-09-15'; card.days = 7; }
    const coordinator = TestBed.inject(RefreshCoordinator); const run = coordinator.refresh();
    expect(locations.getLocations).toHaveBeenCalledWith('2026-09-15', '2026-09-15');
    expect(health.getHealthDataByDateRange).toHaveBeenCalledWith('2026-07-18', '2026-09-15');
    expect(messages.getMessages).toHaveBeenCalledWith('2026-09-15');
    for (const fn of [choices.getChoicesHistory, weight.getWeightProgress, heart.getHeartRateHistory, heart.getHrvHistory]) expect(fn).toHaveBeenCalledWith('2026-09-15', 7);
    expect(page.selectedDaySteps).toBe(1234); expect(page.stepsLoading).toBe(false);
    expect(page.center).toEqual({lat: 12, lng: 34}); expect(page.zoom).toBe(9);
    expect(coordinator.refreshing()).toBe(true);
    pending.complete(); await run; expect(coordinator.refreshing()).toBe(false);
  });

  it('keeps health and message values on failure while applying successful location updates', async () => {
    locations.getLocations.mockReturnValue(of([]));
    health.getHealthDataByDateRange.mockReturnValue(throwError(() => new Error('offline')));
    messages.getMessages.mockReturnValue(throwError(() => new Error('offline')));
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    const page = TestBed.createComponent(DayviewComponent).componentInstance;
    page.selectedDaySteps = 1234; page.selectedDayActiveEnergy = 500;
    page.messages = [{id: 1, type: 'text', source: 'Telegram', sender: {name: 'Sender'}, receiver: {name: 'Receiver'}, timestamp: '2026-09-15T12:00:00Z', body: 'kept'}];
    await TestBed.inject(RefreshCoordinator).refresh();
    expect(page.selectedDaySteps).toBe(1234); expect(page.selectedDayActiveEnergy).toBe(500);
    expect(page.messages[0].body).toBe('kept'); expect(page.messagesError).toBe(false);
    error.mockRestore();
  });

  it('replays the exact message page, filters and ordering', async () => {
    messages.getMessageList.mockReturnValue(of({content: [], totalElements: 100}));
    const page = TestBed.createComponent(MessagesListComponent).componentInstance;
    page.loadMessages({forceUpdate: () => {}, first: 40, rows: 20, sortField: 'timestamp', sortOrder: 1, filters: {source: {value: 'Telegram'}, message: {value: 'hello'}}});
    const previous = messages.getMessageList.mock.calls[0];
    await TestBed.inject(RefreshCoordinator).refresh();
    expect(messages.getMessageList.mock.calls[1]).toEqual(previous);
    expect(previous.slice(0, 4)).toEqual([2, 20, 'timestamp,asc', 'hello']);
  });

  it('refreshes the current heatmap range without resetting its viewport', async () => {
    locations.getSparseLocations.mockReturnValue(of([{latitude: 1, longitude: 2}]));
    const page = TestBed.createComponent(LocationhistoryComponent).componentInstance;
    page.fromDate = new Date(2026, 8, 1); page.toDate = new Date(2026, 8, 15);
    page.center = {lat: 12, lng: 34}; page.zoom = 8;
    await TestBed.inject(RefreshCoordinator).refresh();
    expect(locations.getSparseLocations).toHaveBeenCalledWith('2026-09-01', '2026-09-15');
    expect(page.heatmapData).toEqual([{lat: 1, lng: 2}]); expect(page.center).toEqual({lat: 12, lng: 34}); expect(page.zoom).toBe(8);
  });

  it('refreshes statistics and identity reads while retaining local filters', async () => {
    stats.getStatistics.mockReturnValue(of({visitedCountries: []}));
    identities.getIdentities.mockReturnValue(of([{id: 42, name: 'Updated'}]));
    linked.getUnlinkedIdentityInApplications.mockReturnValue(of([]));
    TestBed.createComponent(StatisticsComponent);
    const list = TestBed.createComponent(IdentitiesComponent).componentInstance; list.unlinkedFilter = 'alice';
    const detail = TestBed.createComponent(IdentityDetailComponent).componentInstance;
    await TestBed.inject(RefreshCoordinator).refresh();
    expect(stats.getStatistics).toHaveBeenCalledTimes(1); expect(identities.getIdentities).toHaveBeenCalledTimes(2);
    expect(linked.getUnlinkedIdentityInApplications).toHaveBeenCalledTimes(1);
    expect(list.unlinkedFilter).toBe('alice'); expect(detail.identity?.id).toBe(42);
  });

  it('refreshes configuration snapshots without resetting fields or starting duplicate polling', async () => {
    config.getFrontendConfiguration.mockReturnValue(of({})); config.fileImportStatus.mockReturnValue(of({status: 'IN_PROGRESS', progress: 50}));
    config.getTelegramStatus.mockReturnValue(of({status: 'WAIT_CODE'})); linked.getIdentityInApplicationsByApplication.mockReturnValue(of([]));
    const page = TestBed.createComponent(ConfigurationComponent).componentInstance;
    page.telegramPhone = 'draft'; page.telegramCode = '123'; page.receiverName = 'kept';
    await TestBed.inject(RefreshCoordinator).refresh();
    expect(page.importStatusProgress).toBe(50); expect(page.telegramPhone).toBe('draft'); expect(page.telegramCode).toBe('123'); expect(page.receiverName).toBe('kept');
    expect(page.telegramPolling).toBe(false); expect(config.getTelegramStatus).toHaveBeenCalledTimes(1);
  });
});
