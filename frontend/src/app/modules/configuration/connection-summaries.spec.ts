import {TestBed} from '@angular/core/testing';
import {Subject, of, throwError} from 'rxjs';
import {CalendarService, FrontendService, GoogleCalendarStatus, OpenAIService, SplitwiseService} from '../../../../generated/backend-api/thereabout';
import {ConnectionSummaries} from './connection-summaries';

describe('Connection summaries', () => {
  it('reports unavailable reads without treating missing data as unconfigured, and ignores stale summary responses', () => {
    const google = new Subject<GoogleCalendarStatus>();
    TestBed.configureTestingModule({providers: [
      ConnectionSummaries,
      {provide: CalendarService, useValue: {getGoogleCalendarStatus: () => google}},
      {provide: SplitwiseService, useValue: {
        splitwiseSettings: () => of({configured: true, tested: false}),
        splitwiseStatus: () => of({state: 'IDLE'})
      }},
      {provide: OpenAIService, useValue: {openAiGetSettings: () => throwError(() => new Error('Offline'))}},
      {provide: FrontendService, useValue: {getTelegramStatus: () => of({configured: false, status: 'DISCONNECTED'})}}
    ]});
    const summaries = TestBed.inject(ConnectionSummaries);
    summaries.load();
    expect(summaries.values()['google-calendar'].state).toBe('loading');
    expect(summaries.values().openai.state).toBe('unavailable');
    expect(summaries.values().splitwise.state).toBe('configured');
    expect(summaries.values().telegram.label).toBe('Server setup needed');
    summaries.update('google-calendar', {state: 'connected', label: 'Connected'});
    google.next({state: 'NOT_CONFIGURED', secrets: {clientId: false, clientSecret: false, refreshToken: false}, account: null, error: null, webhookUrl: null, webhookHealth: 'DISABLED', calendars: []});
    expect(summaries.values()['google-calendar'].state).toBe('connected');
  });
});
