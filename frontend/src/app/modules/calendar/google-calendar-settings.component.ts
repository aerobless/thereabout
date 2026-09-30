import {Component, ChangeDetectionStrategy, ChangeDetectorRef, DestroyRef, inject, OnInit} from '@angular/core';
import {signal} from '@angular/core';
import {MultiSelectModule} from 'primeng/multiselect';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {interval, Observable, finalize} from 'rxjs';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import { AppModalComponent } from '../../shared/modal/app-modal.component';
import {InputTextModule} from 'primeng/inputtext';
import {Identity, IdentityService, CalendarService, CalendarInfo, GoogleCalendarStatus, GoogleCredentials} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {MessageService} from 'primeng/api';

type SecretKey = 'clientId' | 'clientSecret' | 'refreshToken';
@Component({
  selector: 'app-google-calendar-settings',
  imports: [MultiSelectModule, FormsModule, DatePipe, CardModule, ButtonModule, AppModalComponent, InputTextModule],
  templateUrl: './google-calendar-settings.component.html',
  styleUrl: './google-calendar-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GoogleCalendarSettingsComponent implements OnInit {
  readonly users = signal<Identity[]>([]);
  private readonly identities = inject(IdentityService);
  private readonly api = inject(CalendarService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(MessageService);
  private readonly refresh = registerRefresh(() => this.load(), () => this.busy || Object.keys(this.dirty).length > 0);
  readonly fields: {key: SecretKey; label: string; help: string}[] = [
    {key: 'clientId', label: 'Google client ID', help: 'Identifies your Google OAuth application.'},
    {key: 'clientSecret', label: 'Google client secret', help: 'Authenticates your Google OAuth application.'},
    {key: 'refreshToken', label: 'Google refresh token', help: 'Maintains access to your Google calendars.'}
  ];
  status: GoogleCalendarStatus | null = null;
  values: Partial<Record<SecretKey, string>> = {};
  dirty: Partial<Record<SecretKey, boolean>> = {};
  revealing: Partial<Record<SecretKey, boolean>> = {};
  focused: SecretKey | null = null;
  busy = false;
  error = '';
  loadError = '';
  notice = '';
  selectionVisible = false;
  available: CalendarInfo[] = [];
  selected = new Set<number>();
  private revision = 0;

  ngOnInit() {
    this.identities.getIdentities().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: identities => this.users.set(identities.filter(i => !!i.role && !i.isGroup)),
      error: () => { this.error='Unable to load users.'; this.changeDetector.markForCheck(); }
    });
    this.load();
    interval(5000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden && !this.busy) this.load();
    });
  }
  load() {
    const revision = this.revision;
    this.api.getGoogleCalendarStatus().pipe(this.refresh.track('google-status'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: status => { if (revision === this.revision) { this.loadError = ''; this.accept(status); } },
      error: () => {
        if (revision !== this.revision) return;
        this.loadError = 'Unable to load Google Calendar settings.';
        this.changeDetector.markForCheck();
      }
    });
  }
  private accept(status: GoogleCalendarStatus) {
    this.status = status;
    // HTTP and polling callbacks must notify Angular even without a user interaction.
    this.changeDetector.markForCheck();
  }
  focus(key: SecretKey) {
    this.focused = key;
    if (this.dirty[key] || !this.status?.secrets[key]) return;
    this.revealing[key] = true;
    this.api.revealGoogleCalendarSecret(key).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.revealing[key] = false;
        if (this.focused === key && !this.dirty[key]) this.values[key] = response.value;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.revealing[key] = false;
        this.error = 'Unable to reveal this credential.';
        this.changeDetector.markForCheck();
      }
    });
  }
  blur(key: SecretKey) {
    if (this.focused === key) this.focused = null;
    if (!this.dirty[key]) delete this.values[key];
  }
  change(key: SecretKey, value: string) { this.values[key] = value; this.dirty[key] = true; }
  saveSecrets() {
    const values: GoogleCredentials = {};
    this.fields.forEach(({key}) => { if (this.dirty[key]) values[key] = this.values[key] ?? ''; });
    this.run(this.api.saveGoogleCalendarCredentials(values), status => {
      this.values = {}; this.dirty = {}; this.focused = null; this.accept(status);
      this.notice = status.state === 'READY' ? 'Google credentials saved and validated.' : 'Credentials saved. Complete or correct them to enable sync.';
    });
  }
  async copyWebhookUrl(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      this.toast.add({severity: 'success', summary: 'Webhook URL copied'});
    } catch {
      this.toast.add({severity: 'error', summary: 'Copy failed', detail: 'Select the URL and copy it manually.'});
    }
  }
  chooseCalendars() {
    this.run(this.api.getAvailableGoogleCalendars(), calendars => {
      this.available = calendars; this.selected = new Set(calendars.filter(c => c.selected).map(c => c.id));
      this.selectionVisible = true;
    });
  }
  selectable(calendar: CalendarInfo) { return ['reader','writer','owner','writerWithoutPrivateAccess'].includes(calendar.accessRole); }
  toggle(id: number, checked: boolean) { checked ? this.selected.add(id) : this.selected.delete(id); }
  import() {
    this.run(this.api.importGoogleCalendars({calendarIds: [...this.selected]}), () => {
      this.selectionVisible = false; this.notice = 'Calendar selection saved. Selected calendars are queued for full import.'; this.load();
    });
  }
  saveUsers(calendar: CalendarInfo, userIds: number[]) {
    this.run(this.api.assignGoogleCalendarUsers(calendar.id, {userIds}), () => {
      calendar.userIds=[...userIds];
      this.toast.add({severity: 'success', summary: 'Calendar users saved'});
      this.load();
    });
  }
  syncNow() { this.run(this.api.syncGoogleCalendars(), () => { this.notice = 'Incremental synchronization queued.'; this.load(); }); }
  get canSync() { return this.status?.state === 'READY' && !this.busy; }
  private run<T>(request: Observable<T>, success: (value: T) => void) {
    if (this.busy) return;
    ++this.revision; this.busy = true; this.error = ''; this.notice = '';
    request.pipe(finalize(() => { this.busy = false; this.changeDetector.markForCheck(); }), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { success(value); this.changeDetector.markForCheck(); },
      error: () => this.error = 'The operation failed. Check credentials, permissions, and the HTTPS callback URL, then retry.'
    });
  }
}
