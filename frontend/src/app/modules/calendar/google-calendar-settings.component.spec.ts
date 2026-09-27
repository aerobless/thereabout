import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {CalendarService, GoogleCalendarStatus} from '../../../../generated/backend-api/thereabout';
import {GoogleCalendarSettingsComponent} from './google-calendar-settings.component';

const storedStatus: GoogleCalendarStatus = {
  account: 'test@example.com', state: 'READY', error: null, webhookUrl: null,
  webhookHealth: 'DISABLED', secrets: {clientId: true, clientSecret: true, refreshToken: true}, calendars: []
};

async function setup() {
  const status = new Subject<GoogleCalendarStatus>();
  const reveal = new Subject<{value: string}>();
  const save = new Subject<GoogleCalendarStatus>();
  const api = {
    getGoogleCalendarStatus: vi.fn(() => status),
    revealGoogleCalendarSecret: vi.fn(() => reveal),
    saveGoogleCalendarCredentials: vi.fn(() => save)
  };
  await TestBed.configureTestingModule({imports: [GoogleCalendarSettingsComponent], providers: [
    provideZonelessChangeDetection(), {provide: CalendarService, useValue: api}
  ]}).compileComponents();
  const fixture = TestBed.createComponent(GoogleCalendarSettingsComponent);
  fixture.autoDetectChanges();
  await fixture.whenStable();
  const root: HTMLElement = fixture.nativeElement;
  const input = (key = 'clientSecret') => root.querySelector<HTMLInputElement>(`#google-${key}`)!;
  const ready = async () => { status.next(storedStatus); await fixture.whenStable(); };
  const focus = async () => { input().focus(); await fixture.whenStable(); };
  // Responses must update the DOM themselves; no manual detectChanges or extra clicks.
  return {fixture, root, input, api, status, reveal, save, ready, focus};
}

describe('Google Calendar settings asynchronous rendering', () => {
  it('shows loading rather than absent credentials until status arrives, then Stored without interaction', async () => {
    const {fixture, root, input, api, ready} = await setup();
    expect(root.textContent).not.toContain('Not configured');
    expect(input().placeholder).toBe('Loading…');
    expect(input().disabled).toBe(true);
    await ready();
    expect(root.textContent).toContain('Stored');
    expect(input().disabled).toBe(false);
    expect(input().value).toBe('');
    expect(input().placeholder).toBe('••••••••');
    expect(api.revealGoogleCalendarSecret).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('reveals a focused credential as soon as its response arrives and clears it on blur', async () => {
    const {fixture, input, api, reveal, ready, focus} = await setup();
    await ready();
    await focus();
    expect(api.revealGoogleCalendarSecret).toHaveBeenCalledExactlyOnceWith('clientSecret');
    expect(input().readOnly).toBe(true);
    reveal.next({value: 'synthetic-test-credential'});
    await fixture.whenStable();
    expect(input().type).toBe('text');
    expect(input().value).toBe('synthetic-test-credential');
    expect(input().readOnly).toBe(false);
    input().blur();
    await fixture.whenStable();
    expect(input().type).toBe('password');
    expect(input().value).toBe('');
    fixture.destroy();
  });

  it('does not display or retain a reveal that arrives after the field loses focus', async () => {
    const {fixture, input, reveal, ready, focus} = await setup();
    await ready();
    await focus();
    input().blur();
    await fixture.whenStable();
    reveal.next({value: 'synthetic-late-credential'});
    await fixture.whenStable();
    expect(input().value).toBe('');
    expect(input().type).toBe('password');
    expect(fixture.componentInstance.values.clientSecret).toBeUndefined();
    expect(input().readOnly).toBe(false);
    fixture.destroy();
  });

  it('shows a status request failure without claiming credentials are missing', async () => {
    const {fixture, root, input, status} = await setup();
    status.error(new Error('Offline'));
    await fixture.whenStable();
    expect(root.textContent).toContain('Unable to load Google Calendar settings.');
    expect(root.textContent).not.toContain('Not configured');
    expect(input().placeholder).toBe('Unavailable');
    fixture.destroy();
  });

  it('shows reveal failures and releases the input without another interaction', async () => {
    const {fixture, root, input, reveal, ready, focus} = await setup();
    await ready();
    await focus();
    reveal.error(new Error('Offline'));
    await fixture.whenStable();
    expect(root.textContent).toContain('Unable to reveal this credential.');
    expect(input().readOnly).toBe(false);
    expect(input().value).toBe('');
    fixture.destroy();
  });

  it('saves only edited credentials and masks them again when the async save finishes', async () => {
    const {fixture, root, input, api, reveal, save, ready, focus} = await setup();
    await ready();
    await focus();
    // Use an editable, already revealed field as in the normal user flow.
    reveal.next({value: 'synthetic-old-credential'});
    await fixture.whenStable();
    input().value = 'synthetic-updated-credential';
    input().dispatchEvent(new Event('input', {bubbles: true}));
    await fixture.whenStable();
    const button = [...root.querySelectorAll('button')].find(element => element.textContent?.includes('Save and validate'))!;
    button.click();
    await fixture.whenStable();
    expect(api.saveGoogleCalendarCredentials).toHaveBeenCalledExactlyOnceWith({clientSecret: 'synthetic-updated-credential'});
    expect(button.disabled).toBe(true);
    save.next(storedStatus);
    save.complete();
    await fixture.whenStable();
    expect(button.disabled).toBe(false);
    expect(input().value).toBe('');
    expect(input().type).toBe('password');
    expect(root.textContent).toContain('Google credentials saved and validated.');
    fixture.destroy();
  });
});
