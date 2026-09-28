import {computed, DestroyRef, inject, Injectable, isDevMode, signal} from '@angular/core';
import {CurrentUser, Identity, CurrentUserService as CurrentUserApi} from '../../../../generated/backend-api/thereabout';
import {Subscription, timeout} from 'rxjs';

export function canImpersonateLocally(development: boolean, hostname: string): boolean {
  return development && ['localhost', '127.0.0.1', '[::1]'].includes(hostname);
}

@Injectable({providedIn: 'root'})
export class CurrentUserService {
  private readonly api = inject(CurrentUserApi);
  private pending?: Subscription;
  readonly verifiedState = signal<CurrentUser | {status: 'loading'}>({status: 'loading'});
  readonly impersonationAllowed = canImpersonateLocally(isDevMode(), location.hostname);
  private readonly simulatedUser = signal<Identity | null>(null);
  readonly impersonatedUser = this.simulatedUser.asReadonly();
  readonly state = computed<CurrentUser | {status: 'loading'}>(() => {
    const user = this.simulatedUser();
    return user ? {status: 'resolved', identityId: user.id, displayName: user.shortName} : this.verifiedState();
  });

  impersonate(user: Identity): void {
    if (!this.impersonationAllowed || !user.isUser || user.isGroup) return;
    this.simulatedUser.set({...user});
  }

  stopImpersonation(): void {
    this.simulatedUser.set(null);
    this.load();
  }
  /** Only resolved non-admins are refused; local development acts as the local administrator. */
  readonly canManageUsers = computed(() => {
    const user = this.verifiedState();
    return user.status !== 'resolved' || user.isAdmin === true;
  });
  readonly displayName = computed(() => {
    const user = this.state();
    return user.status === 'resolved' ? user.displayName : undefined;
  });

  start(): void {
    const clear = () => { this.pending?.unsubscribe(); this.verifiedState.set({status: 'loading'}); };
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
    this.verifiedState.set({status: 'loading'});
    this.pending = this.api.getCurrentUser().pipe(timeout(10000)).subscribe({
      next: user => this.verifiedState.set(user),
      error: () => this.verifiedState.set({status: 'verification_unavailable'})
    });
  }
}
