import {TestBed} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {UserSelectionService} from './user-selection.service';
import {userContextInterceptor} from './user-context.interceptor';

describe('browser user context', () => {
  beforeEach(() => TestBed.configureTestingModule({providers: [
    provideHttpClient(withInterceptors([userContextInterceptor])), provideHttpClientTesting()
  ]}));
  it('cancels old requests before the next user and scopes each new browser request', () => {
    const client=TestBed.inject(HttpClient), http=TestBed.inject(HttpTestingController), selection=TestBed.inject(UserSelectionService);
    const old=vi.fn();client.get('/api/finances/accounts').subscribe(old);
    const pending=http.expectOne('/api/finances/accounts');
    selection.select(2);
    expect(pending.cancelled).toBe(true);expect(old).not.toHaveBeenCalled();
    client.get('/backend/api/v1/health/data').subscribe();
    const scoped=http.expectOne('/backend/api/v1/health/data');
    expect(scoped.request.headers.get('X-Thereabout-Impersonate-User')).toBe('2');scoped.flush({});
    selection.select(null);
    client.get('/api/finances/accounts').subscribe();
    const actor=http.expectOne('/api/finances/accounts');
    expect(actor.request.headers.has('X-Thereabout-Impersonate-User')).toBe(false);actor.flush({});
    http.verify();
  });
  it('never forwards user selection to external origins, ingestion or MCP', () => {
    const client=TestBed.inject(HttpClient), http=TestBed.inject(HttpTestingController);
    TestBed.inject(UserSelectionService).select(2);
    for(const url of ['https://external.example/api/finances/accounts','/backend/api/v1/ingest/health','/mcp/finances']) {
      client.get(url).subscribe();const request=http.expectOne(url);
      expect(request.request.headers.has('X-Thereabout-Impersonate-User')).toBe(false);request.flush({});
    }
    http.verify();
  });
});
