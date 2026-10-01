import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {By} from '@angular/platform-browser';
import {provideRouter, Router} from '@angular/router';
import {of, Subject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {CalendarService, CurrentUser, CurrentUserService as CurrentUserApi, HealthService, LauncherService} from '../../../../generated/backend-api/thereabout';
import {AppComponent} from '../../app.component';
import {LauncherComponent} from '../../modules/launcher/launcher.component';
import {CurrentUserService} from './current-user.service';

describe('An open editor during user verification', () => {
  afterEach(() => vi.restoreAllMocks());

  it('preserves the modal and draft across a tab switch, but resets them on a verified user change', async () => {
    const first = new Subject<CurrentUser>(), refresh = new Subject<CurrentUser>();
    const api = {getCurrentUser: vi.fn().mockReturnValueOnce(first).mockReturnValue(refresh)};
    const group = {id: 1, section: 'Personal', name: 'Tools', position: 0};
    await TestBed.configureTestingModule({imports: [AppComponent], providers: [
      provideZonelessChangeDetection(), provideRouter([{path: '', component: LauncherComponent}]), MessageService,
      {provide: CurrentUserApi, useValue: api},
      {provide: LauncherService, useValue: {getLauncher: () => of({groups: [group], shortcuts: []})}},
      {provide: HealthService, useValue: {getHealthDataByDateRange: () => of({metrics: {}})}},
      {provide: CalendarService, useValue: {getUpcomingCalendarEvent: () => of([])}}
    ]}).compileComponents();
    const current = TestBed.inject(CurrentUserService);
    const admin: CurrentUser = {status: 'resolved', identityId: 1, role: 'ADMIN', actorIdentityId: 1, actorRole: 'ADMIN'};
    const ready = current.start(); first.next(admin); await ready;
    const fixture = TestBed.createComponent(AppComponent);
    await fixture.whenStable();
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    const launcher = () => fixture.debugElement.query(By.directive(LauncherComponent)).componentInstance as LauncherComponent;
    const original = launcher();
    original.openGroup(group);
    await fixture.whenStable();
    const input = document.querySelector<HTMLInputElement>('#group-name')!;
    input.value = 'My unsaved group'; input.dispatchEvent(new Event('input', {bubbles: true}));
    await fixture.whenStable();

    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    await fixture.whenStable();
    expect(launcher()).toBe(original);
    expect(document.querySelector('#group-name')).toBe(input);

    visibility.mockReturnValue('visible'); document.dispatchEvent(new Event('visibilitychange'));
    await fixture.whenStable();
    expect(api.getCurrentUser).toHaveBeenCalledTimes(2);
    expect(document.querySelector('#group-name')).toBe(input);
    refresh.next({...admin}); await fixture.whenStable();
    expect(launcher()).toBe(original);
    expect(original.dialog()).toBe('group');
    expect(input.value).toBe('My unsaved group');
    expect(original.groupDraft.name).toBe('My unsaved group');

    refresh.next({...admin, identityId: 2}); await fixture.whenStable();
    expect(launcher()).not.toBe(original);
    expect(launcher().dialog()).toBeNull();
    expect(document.querySelector('#group-name')).toBeNull();
  });
});
