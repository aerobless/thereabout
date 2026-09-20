import {DestroyRef, Injectable, inject, signal} from '@angular/core';
import {NavigationStart, Router} from '@angular/router';
import {MonoTypeOperatorFunction, Observable, Subscriber, timeout} from 'rxjs';
import {MessageService} from 'primeng/api';

/** A component owns its reads, so a newer read can cancel an older refresh. */
export class RefreshHandle {
  private readonly reads = new Map<string, {subscriber: Subscriber<unknown>; refreshing: boolean}>();
  private collecting: Promise<boolean>[] | null = null;

  constructor(readonly load: () => void, readonly blocked: () => boolean) {}

  async start(): Promise<boolean> {
    const pending: Promise<boolean>[] = [];
    this.collecting = pending;
    try { this.load(); }
    catch { pending.push(Promise.resolve(false)); }
    finally { this.collecting = null; }
    return (await Promise.all(pending)).every(Boolean);
  }

  track<T>(key: string): MonoTypeOperatorFunction<T> {
    return source => new Observable<T>(subscriber => {
      this.reads.get(key)?.subscriber.unsubscribe();
      this.reads.set(key, {subscriber: subscriber as Subscriber<unknown>, refreshing: this.collecting !== null});
      let settle!: (success: boolean) => void;
      const done = new Promise<boolean>(resolve => settle = resolve);
      this.collecting?.push(done);
      const subscription = source.pipe(timeout({first: 30_000})).subscribe({
        next: value => subscriber.next(value),
        error: error => { settle(false); subscriber.error(error); },
        complete: () => { settle(true); subscriber.complete(); }
      });
      return () => {
        subscription.unsubscribe();
        settle(true); // Cancellation is not a failed refresh.
        if (this.reads.get(key)?.subscriber === subscriber) this.reads.delete(key);
      };
    });
  }

  cancel(refreshOnly = false) {
    for (const read of this.reads.values()) {
      if (!refreshOnly || read.refreshing) read.subscriber.unsubscribe();
    }
  }
}

@Injectable({providedIn: 'root'})
export class RefreshCoordinator {
  readonly refreshing = signal(false);
  private readonly participants = new Set<RefreshHandle>();
  private readonly toast = inject(MessageService, {optional: true});
  private generation = 0;

  constructor() {
    const events = inject(Router, {optional: true})?.events.subscribe(event => {
      if (event instanceof NavigationStart) this.cancel();
    });
    inject(DestroyRef).onDestroy(() => events?.unsubscribe());
  }

  register(handle: RefreshHandle) {
    this.participants.add(handle);
    return () => { handle.cancel(); this.participants.delete(handle); };
  }

  canRefresh() {
    return !this.refreshing() && this.participants.size > 0 &&
      ![...this.participants].some(handle => handle.blocked());
  }

  async refresh() {
    if (!this.canRefresh()) return;
    const generation = ++this.generation;
    this.refreshing.set(true);
    try { navigator.vibrate?.(20); } catch { /* Optional device capability. */ }
    const results = await Promise.all([...this.participants].map(handle => handle.start()));
    if (generation !== this.generation) return;
    this.refreshing.set(false);
    if (results.some(success => !success)) {
      this.toast?.add({severity: 'warn', summary: 'Refresh incomplete',
        detail: 'Some data could not be refreshed. Previous data is still shown. Pull down to retry.'});
    }
  }

  cancel() {
    ++this.generation;
    for (const handle of this.participants) handle.cancel(true);
    this.refreshing.set(false);
  }
}

/** Register once in a component field; the callback only invokes its read loaders. */
export function registerRefresh(load: () => void, blocked: () => boolean = () => false): RefreshHandle {
  const handle = new RefreshHandle(load, blocked);
  const unregister = inject(RefreshCoordinator).register(handle);
  inject(DestroyRef).onDestroy(unregister);
  return handle;
}
