import {Component, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {Location} from '@angular/common';
import {provideLocationMocks} from '@angular/common/testing';
import {NavigationCancel, provideRouter, Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {Subject, filter, firstValueFrom, of, take} from 'rxjs';
import {MessageService} from 'primeng/api';
import {CalendarService, FrontendService, IdentityService, IdentityInApplicationService, OpenAIService, SplitwiseService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../shared/current-user/current-user.service';
import {adminOnly} from '../../app.routes';
import {configurationRoutes} from './configuration.routes';
import {ConfigurationComponent} from './configuration.component';
import {OpenAiSettingsComponent} from './openai-settings.component';
import {ConnectionsComponent} from './connections.component';
import {searchConfiguration} from './configuration-search';

@Component({template: '<p>Home</p>'})
class HomeComponent {}

describe('Configuration navigation', () => {
  const admin = signal(true);
  const google = {account: null, state: 'READY', error: null, webhookUrl: null, webhookHealth: 'DISABLED', secrets: {clientId: true, clientSecret: true, refreshToken: true}, calendars: []};
  let frontend: {getTelegramStatus: ReturnType<typeof vi.fn>; getIngestionKey: ReturnType<typeof vi.fn>; getFrontendConfiguration: ReturnType<typeof vi.fn>; fileImportStatus: ReturnType<typeof vi.fn>};
  let openai: {openAiGetSettings: ReturnType<typeof vi.fn>; openAiSaveSettings: ReturnType<typeof vi.fn>; openAiTestSettings: ReturnType<typeof vi.fn>};
  let messages: {add: ReturnType<typeof vi.fn>};
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    admin.set(true);
    messages = {add: vi.fn()};
    frontend = {
      getTelegramStatus: vi.fn(() => of({configured: false, status: 'DISCONNECTED'})),
      getIngestionKey: vi.fn(() => of({value: 'synthetic-ingestion-key'})),
      getFrontendConfiguration: vi.fn(() => of({versionDetails: {version: 'test-version', branch: 'test', commitRef: 'test-commit', commitTime: '2026-10-03T20:14:24Z'}})),
      fileImportStatus: vi.fn(() => of({status: 'IDLE', progress: 0}))
    };
    openai = {
      openAiGetSettings: vi.fn(() => of({configured: true, model: 'test-model'})),
      openAiSaveSettings: vi.fn(() => of({configured: true, model: 'saved-model'})),
      openAiTestSettings: vi.fn(() => of({success: true, message: 'Connected'}))
    };
    // Give each test fresh route objects and provider scopes.
    const children = configurationRoutes.map(route => ({...route, children: route.children?.map(child => ({...child, children: child.children?.map(leaf => ({...leaf}))}))}));
    await TestBed.configureTestingModule({providers: [
      provideZonelessChangeDetection(),
      provideLocationMocks(),
      provideRouter([{path: '', component: HomeComponent}, {path: 'configuration', canActivate: [adminOnly], canActivateChild: [adminOnly], children}]),
      {provide: CurrentUserService, useValue: {ready: async () => ({}), canManageUsers: admin}},
      {provide: MessageService, useValue: messages},
      {provide: FrontendService, useValue: frontend},
      {provide: CalendarService, useValue: {getGoogleCalendarStatus: () => of(google)}},
      {provide: IdentityService, useValue: {getIdentities: () => of([])}},
      {provide: IdentityInApplicationService, useValue: {getIdentityInApplicationsByApplication: () => of([])}},
      {provide: OpenAIService, useValue: openai},
      {provide: SplitwiseService, useValue: {
        splitwiseSettings: () => of({configured: false, tested: false, initialized: false, enabled: false, revision: 0, members: [], categories: []}),
        splitwiseCatalog: () => of({groups: [], accounts: [], categories: [], sourceCategories: []}),
        splitwiseStatus: () => of({state: 'IDLE', processed: 0, rows: []})
      }}
    ]}).compileComponents();
    harness = await RouterTestingHarness.create();
    TestBed.inject(Router).setUpLocationChangeListener();
  });
  const shell = () => harness.fixture.debugElement.query(By.directive(ConfigurationComponent)).injector.get(ConfigurationComponent);
  const router = () => TestBed.inject(Router);

  it('redirects both entry points, mounts only Google and keeps credentials out of summary requests', async () => {
    await harness.navigateByUrl('/configuration');
    expect(router().url).toBe('/configuration/connections/google-calendar');
    expect(harness.routeNativeElement?.querySelector('app-google-calendar-settings')).not.toBeNull();
    expect(harness.routeNativeElement?.querySelector('app-openai-settings')).toBeNull();
    expect(frontend.getIngestionKey).not.toHaveBeenCalled();
    expect(harness.routeNativeElement?.querySelector('.configuration-tabs [aria-current="page"]')?.textContent).toBe('Connections');
    await harness.navigateByUrl('/configuration/connections');
    expect(router().url).toBe('/configuration/connections/google-calendar');
  });

  it('opens every deep link with its existing controls and keeps API keys masked', async () => {
    for (const [path, selector] of [
      ['connections/splitwise', '#splitwise-key'], ['connections/openai', '#openai-model'],
      ['connections/telegram', '#telegram-settings'], ['location-imports', 'app-file-import'],
      ['api-access', '#finance-mcp-key'], ['about', '#version-settings']
    ]) {
      await harness.navigateByUrl('/configuration/' + path);
      expect(harness.routeNativeElement?.querySelector(selector)).not.toBeNull();
    }
    await harness.navigateByUrl('/configuration/api-access');
    expect(harness.routeNativeElement?.querySelector<HTMLInputElement>('#thereabout-api-key')?.type).toBe('password');
    expect(harness.routeNativeElement?.querySelector<HTMLInputElement>('#finance-mcp-key')?.type).toBe('password');
  });

  it('denies direct access to every section for non-administrators', async () => {
    admin.set(false);
    for (const path of ['connections/google-calendar', 'connections/splitwise', 'connections/openai', 'connections/telegram', 'location-imports', 'api-access', 'about']) {
      await harness.navigateByUrl('/configuration/' + path);
      expect(router().url).toBe('/');
    }
    expect(openai.openAiGetSettings).not.toHaveBeenCalled();
  });

  it('searches names and aliases, opens the correct panel and focuses its section', async () => {
    await harness.navigateByUrl('/configuration/about');
    shell().complete({query: 'model', originalEvent: new Event('input')});
    expect(shell().suggestions().map(item => item.category)).toEqual(['OpenAI']);
    await shell().select(shell().suggestions()[0]);
    await harness.fixture.whenStable();
    expect(router().url).toBe('/configuration/connections/openai#openai-settings');
    expect(document.activeElement?.id).toBe('openai-settings');
    shell().complete({query: 'no-such-setting', originalEvent: new Event('input')});
    expect(shell().suggestions()).toEqual([]);
    expect(searchConfiguration('health export')[0].path).toBe('/configuration/location-imports');
    expect(searchConfiguration('   ')).toEqual([]);
    await shell().select(searchConfiguration('webhook')[0]);
    await harness.fixture.whenStable();
    expect(document.activeElement?.id).toBe('google-webhook-setup');
    expect(harness.routeNativeElement?.querySelector<HTMLDetailsElement>('#google-webhook-setup')?.closest('details')?.open).toBe(true);
    await shell().select(searchConfiguration('category mapping')[0]);
    await harness.fixture.whenStable();
    expect(document.activeElement?.id).toBe('splitwise-categories');
  });

  it('keeps edits on Stay, and clears the abandoned key on Discard when leaving Configuration', async () => {
    await harness.navigateByUrl('/configuration/connections/openai');
    const panel = harness.fixture.debugElement.query(By.directive(OpenAiSettingsComponent)).injector.get(OpenAiSettingsComponent);
    panel.key.set('synthetic-draft');
    const stay = router().navigateByUrl('/configuration/about');
    await vi.waitFor(() => expect(shell().navigation.confirming()).toBe(true));
    shell().navigation.answer(false);
    expect(await stay).toBe(false);
    expect(panel.key()).toBe('synthetic-draft');
    const leave = router().navigateByUrl('/');
    await vi.waitFor(() => expect(shell().navigation.confirming()).toBe(true));
    shell().navigation.answer(true);
    expect(await leave).toBe(true);
    expect(panel.key()).toBe('');
    expect(openai.openAiSaveSettings).not.toHaveBeenCalled();
  });

  it('blocks navigation during a save and allows it after completion without another click to render', async () => {
    await harness.navigateByUrl('/configuration/connections/openai');
    const panel = harness.fixture.debugElement.query(By.directive(OpenAiSettingsComponent)).injector.get(OpenAiSettingsComponent);
    const saved = new Subject<{configured: boolean; model: string}>();
    openai.openAiSaveSettings.mockReturnValue(saved);
    panel.model.set('saved-model');
    const saving = panel.save();
    expect(await router().navigateByUrl('/configuration/about')).toBe(false);
    expect(shell().navigation.confirming()).toBe(false);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    saved.next({configured: true, model: 'saved-model'}); saved.complete(); await saving;
    await harness.fixture.whenStable();
    expect(panel.hasUnsavedChanges()).toBe(false);
    expect(await router().navigateByUrl('/configuration/about')).toBe(true);
  });

  it('updates the service summary after a successful connection test', async () => {
    await harness.navigateByUrl('/configuration/connections/openai');
    const panel = harness.fixture.debugElement.query(By.directive(OpenAiSettingsComponent)).injector.get(OpenAiSettingsComponent);
    const connections = harness.fixture.debugElement.query(By.directive(ConnectionsComponent)).injector.get(ConnectionsComponent);
    expect(connections.summaries.values().openai.state).toBe('configured');
    await panel.test(); await harness.fixture.whenStable();
    expect(connections.summaries.values().openai.state).toBe('connected');
    expect(harness.routeNativeElement?.querySelector('a[href$="/openai"] small')?.textContent).toBe('Connected');
  });

  it('protects a draft on browser Back and restores the current mobile selection on Stay', async () => {
    await harness.navigateByUrl('/configuration/about');
    await harness.navigateByUrl('/configuration/connections/openai');
    const panel = harness.fixture.debugElement.query(By.directive(OpenAiSettingsComponent)).injector.get(OpenAiSettingsComponent);
    const connections = harness.fixture.debugElement.query(By.directive(ConnectionsComponent)).injector.get(ConnectionsComponent);
    panel.model.set('unsaved-model');
    const switching = connections.choose('telegram');
    await vi.waitFor(() => expect(shell().navigation.confirming()).toBe(true));
    shell().navigation.answer(false); await switching;
    expect(connections.selected()).toBe('openai');
    const cancelled = firstValueFrom(router().events.pipe(filter(event => event instanceof NavigationCancel), take(1)));
    TestBed.inject(Location).back();
    await vi.waitFor(() => expect(shell().navigation.confirming()).toBe(true));
    shell().navigation.answer(false); await cancelled;
    expect(router().url).toBe('/configuration/connections/openai');
    expect(panel.model()).toBe('unsaved-model');
  });
});
