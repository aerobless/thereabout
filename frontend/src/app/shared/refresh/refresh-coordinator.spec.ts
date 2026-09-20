import {TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {Subject, of, NEVER, throwError} from 'rxjs';
import {vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {RefreshCoordinator, RefreshHandle} from './refresh-coordinator';

describe('RefreshCoordinator', () => {
  const toast = {add: vi.fn()};
  beforeEach(() => { toast.add.mockReset(); TestBed.configureTestingModule({providers: [provideRouter([]), {provide: MessageService, useValue: toast}]}); });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('waits for every participant, prevents overlap and reports partial failures', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const slow = new Subject<number>(); const received = vi.fn();
    const first = new RefreshHandle(() => slow.pipe(first.track('read')).subscribe(received), () => false);
    const second = new RefreshHandle(() => throwError(() => new Error('offline')).pipe(second.track('read')).subscribe({error: () => {}}), () => false);
    coordinator.register(first); coordinator.register(second);
    const start = vi.spyOn(first, 'start');
    const running = coordinator.refresh();
    await coordinator.refresh();
    expect(start).toHaveBeenCalledTimes(1);
    expect(coordinator.refreshing()).toBe(true);
    slow.next(42); slow.complete(); await running;
    expect(received).toHaveBeenCalledWith(42);
    expect(coordinator.refreshing()).toBe(false);
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({summary: 'Refresh incomplete'}));
  });

  it('cancels superseded reads so old results cannot overwrite a new selection', async () => {
    const old = new Subject<number>(); const received = vi.fn();
    const handle = new RefreshHandle(() => old.pipe(handle.track('data')).subscribe(received), () => false);
    const running = handle.start();
    of(2).pipe(handle.track('data')).subscribe(received);
    old.next(1); await running;
    expect(received.mock.calls).toEqual([[2]]);
  });

  it('times out a stalled request and permits retry', async () => {
    vi.useFakeTimers();
    const coordinator = TestBed.inject(RefreshCoordinator);
    const handle = new RefreshHandle(() => NEVER.pipe(handle.track('read')).subscribe({error: () => {}}), () => false);
    coordinator.register(handle);
    const running = coordinator.refresh();
    await vi.advanceTimersByTimeAsync(30_000); await running;
    expect(coordinator.refreshing()).toBe(false);
    expect(coordinator.canRefresh()).toBe(true);
    expect(toast.add).toHaveBeenCalledTimes(1);
  });

  it('cancels on navigation and unregisters destroyed participants', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const source = new Subject<number>(); const next = vi.fn();
    const handle = new RefreshHandle(() => source.pipe(handle.track('data')).subscribe(next), () => false);
    const unregister = coordinator.register(handle);
    const running = coordinator.refresh();
    await TestBed.inject(Router).navigateByUrl('/'); await running;
    source.next(1);
    expect(next).not.toHaveBeenCalled();
    expect(coordinator.refreshing()).toBe(false);
    expect(toast.add).not.toHaveBeenCalled();
    unregister(); expect(coordinator.canRefresh()).toBe(false);
  });

  it('does not cancel ordinary initial reads during URL normalization', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const source = new Subject<number>(); const next = vi.fn();
    const handle = new RefreshHandle(() => {}, () => false);
    coordinator.register(handle);
    source.pipe(handle.track('initial')).subscribe(next);
    await TestBed.inject(Router).navigateByUrl('/');
    source.next(42); source.complete();
    expect(next).toHaveBeenCalledWith(42);
  });

  it('blocks during saves and tolerates unavailable or failing vibration', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    let saving = true;
    const handle = new RefreshHandle(() => {}, () => saving);
    coordinator.register(handle);
    expect(coordinator.canRefresh()).toBe(false);
    saving = false;
    const vibrate = vi.fn(() => { throw new Error('unsupported'); });
    vi.stubGlobal('navigator', {vibrate});
    await coordinator.refresh();
    expect(vibrate).toHaveBeenCalledWith(20);
    vi.stubGlobal('navigator', {});
    await coordinator.refresh();
    expect(coordinator.refreshing()).toBe(false);
    vi.unstubAllGlobals();
  });
});
