import {DestroyRef, Injectable, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CalendarService, FrontendService, GoogleCalendarStatus, OpenAIService, SplitwiseService, SplitwiseSettings, SplitwiseStatus, TelegramStatus} from '../../../../generated/backend-api/thereabout';
import {Observable, forkJoin} from 'rxjs';

export const connections = [
  {id: 'google-calendar', label: 'Google Calendar', icon: 'pi pi-calendar'},
  {id: 'splitwise', label: 'Splitwise', icon: 'pi pi-wallet'},
  {id: 'openai', label: 'OpenAI', icon: 'pi pi-sparkles'},
  {id: 'telegram', label: 'Telegram', icon: 'pi pi-send'}
] as const;
export type ConnectionId = typeof connections[number]['id'];
export type ConnectionSummary = {state: 'loading' | 'unavailable' | 'unconfigured' | 'configured' | 'connected' | 'attention'; label: string};
const loading: ConnectionSummary = {state: 'loading', label: 'Loading…'};

export function googleSummary(status: GoogleCalendarStatus): ConnectionSummary {
  if (status.error) return {state: 'attention', label: 'Needs attention'};
  if (status.state === 'READY') return {state: 'connected', label: 'Connected'};
  return Object.values(status.secrets).some(Boolean)
    ? {state: 'configured', label: 'Setup incomplete'} : {state: 'unconfigured', label: 'Not configured'};
}
export function splitwiseSummary(settings: SplitwiseSettings, status?: SplitwiseStatus | null): ConnectionSummary {
  if (status?.error) return {state: 'attention', label: 'Needs attention'};
  return settings.tested ? {state: 'connected', label: 'Connected'} : settings.configured
    ? {state: 'configured', label: 'Key saved · not tested'} : {state: 'unconfigured', label: 'Not configured'};
}
export function telegramSummary(status: TelegramStatus): ConnectionSummary {
  if (!status.configured) return {state: 'unconfigured', label: 'Server setup needed'};
  if (status.status === 'ERROR' || status.resyncStatus === 'ERROR') return {state: 'attention', label: 'Needs attention'};
  if (status.status === 'READY' || status.status === 'SYNCING') return {state: 'connected', label: status.status === 'SYNCING' ? 'Syncing' : 'Connected'};
  return {state: 'configured', label: status.status === 'CONNECTING' ? 'Connecting…' : 'Login needed'};
}
// Only display summaries are shared. Credentials and editable forms stay in their panel.
@Injectable()
export class ConnectionSummaries {
  private readonly calendar = inject(CalendarService);
  private readonly splitwise = inject(SplitwiseService);
  private readonly openai = inject(OpenAIService);
  private readonly frontend = inject(FrontendService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly revisions: Record<ConnectionId, number> = {'google-calendar': 0, splitwise: 0, openai: 0, telegram: 0};
  readonly values = signal<Record<ConnectionId, ConnectionSummary>>({'google-calendar': loading, splitwise: loading, openai: loading, telegram: loading});
  update(id: ConnectionId, summary: ConnectionSummary): void {
    ++this.revisions[id];
    this.values.update(values => ({...values, [id]: summary}));
  }
  unavailable(id: ConnectionId): void { this.update(id, {state: 'unavailable', label: 'Unavailable'}); }
  load(): void {
    this.read('google-calendar', this.calendar.getGoogleCalendarStatus(), googleSummary);
    this.read('splitwise', forkJoin({settings: this.splitwise.splitwiseSettings(), status: this.splitwise.splitwiseStatus()}), value => splitwiseSummary(value.settings, value.status));
    this.read('openai', this.openai.openAiGetSettings('body', false, {transferCache: false}), settings => ({state: settings.configured ? 'configured' : 'unconfigured', label: settings.configured ? 'Key configured' : 'Not configured'}));
    this.read('telegram', this.frontend.getTelegramStatus(), telegramSummary);
  }
  private read<T>(id: ConnectionId, request: Observable<T>, summary: (value: T) => ConnectionSummary): void {
    const revision = this.revisions[id];
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { if (this.revisions[id] === revision) this.update(id, summary(value)); },
      error: () => { if (this.revisions[id] === revision) this.unavailable(id); }
    });
  }
}
