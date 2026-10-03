import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {toObservable} from '@angular/core/rxjs-interop';
import {Router} from '@angular/router';
import {CurrentUser, Identity, CurrentUserService as CurrentUserApi} from '../../../../generated/backend-api/thereabout';
import {filter, firstValueFrom, Subscription, timeout} from 'rxjs';
import {UserSelectionService} from './user-selection.service';

@Injectable({providedIn: 'root'})
export class CurrentUserService {
  private readonly api = inject(CurrentUserApi);
  private readonly selection = inject(UserSelectionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  readonly state = signal<CurrentUser | {status: 'loading'}>({status: 'loading'});
  readonly verifiedState = this.state;
  private readonly states = toObservable(this.state);
  readonly impersonatedUser = computed(() => {
    const state=this.state();
    return state.status !== 'loading' && state.impersonating
      ? {id: state.identityId, firstName: state.displayName} : null;
  });
  readonly impersonationAllowed = computed(() => {
    const state=this.state();
    return state.status !== 'loading' && state.actorRole === 'ADMIN';
  });
  readonly canManageUsers = computed(() => {
    const state=this.state();
    return state.status !== 'loading' && state.role === 'ADMIN';
  });
  readonly displayName = computed(() => {
    const state=this.state(); return state.status === 'resolved' ? state.displayName : undefined;
  });
  /** Reset views on a user or permission change, while retaining editors during background verification. */
  readonly viewKeys = computed(() => {
    const state=this.state();
    return state.status === 'loading' ? [] : [`${state.status}:${state.identityId ?? 'none'}:${state.role ?? 'none'}:${state.actorIdentityId ?? 'none'}:${state.actorRole ?? 'none'}:${state.impersonating ?? false}`];
  });

  ready(): Promise<CurrentUser> {
    return firstValueFrom(this.states.pipe(filter((state): state is CurrentUser => state.status !== 'loading')));
  }
  impersonate(user: Identity): void {
    if (!this.impersonationAllowed() || !user.role || user.isGroup) return;
    this.selection.select(user.id); this.load();
  }
  stopImpersonation(): void { this.selection.select(null); this.load(); }

  start(): Promise<CurrentUser> {
    const clear = () => { this.pending?.unsubscribe(); this.state.set({status: 'loading'}); };
    const visible = () => { if (document.visibilityState === 'visible') this.load(true); };
    const restored = (event: PageTransitionEvent) => { if (event.persisted) this.load(true); };
    document.addEventListener('visibilitychange', visible);
    window.addEventListener('pageshow', restored);
    this.destroyRef.onDestroy(() => {
      clear(); document.removeEventListener('visibilitychange', visible);
      window.removeEventListener('pageshow', restored);
    });
    this.load(); return this.ready();
  }
  load(background = false): void {
    this.pending?.unsubscribe();
    if (!background) this.state.set({status: 'loading'});
    this.pending=this.api.getCurrentUser().pipe(timeout(10000)).subscribe({
      next: state => {
        this.state.set(state);
        if (state.role !== 'ADMIN' && /^\/(configuration|identities)(\/|$)/.test(this.router.url))
          void this.router.navigateByUrl('/', {replaceUrl: true});
      },
      error: () => {
        if (this.selection.target() != null) { this.selection.select(null); this.load(); }
        else this.state.set({status: 'verification_unavailable'});
      }
    });
  }
}
