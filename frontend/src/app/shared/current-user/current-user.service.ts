import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {CurrentUser, CurrentUserService as CurrentUserApi} from '../../../../generated/backend-api/thereabout';
import {Subscription, timeout} from 'rxjs';

@Injectable({providedIn: 'root'})
export class CurrentUserService {
  private readonly api = inject(CurrentUserApi);
  private pending?: Subscription;
  readonly state = signal<CurrentUser | {status: 'loading'}>({status: 'loading'});
  readonly displayName = computed(() => {
    const user = this.state();
    return user.status === 'resolved' ? user.displayName : undefined;
  });

  start(): void {
    const clear = () => { this.pending?.unsubscribe(); this.state.set({status: 'loading'}); };
    const visible = () => { if (document.visibilityState === 'visible') this.load(); else clear(); };
    const restored = (event: PageTransitionEvent) => { if (event.persisted) this.load(); };
    document.addEventListener('visibilitychange', visible);
    window.addEventListener('pagehide', clear);
    window.addEventListener('pageshow', restored);
    this.destroyRef.onDestroy(() => {
      clear();
      document.removeEventListener('visibilitychange', visible);
      window.removeEventListener('pagehide', clear);
      window.removeEventListener('pageshow', restored);
    });
    this.load();
  }
  private readonly destroyRef = inject(DestroyRef);

  load(): void {
    this.pending?.unsubscribe();
    // Always discard the previous person before resolving a new session.
    this.state.set({status: 'loading'});
    this.pending = this.api.getCurrentUser().pipe(timeout(10000)).subscribe({
      next: user => this.state.set(user),
      error: () => this.state.set({status: 'verification_unavailable'})
    });
  }
}
