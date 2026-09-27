import {computed, inject, Injectable, signal} from '@angular/core';
import {Subscription} from 'rxjs';
import {CurrentUser, UsersService} from '../../../../generated/backend-api/thereabout';

@Injectable({providedIn: 'root'})
export class CurrentUserService {
  private readonly api = inject(UsersService);
  private request?: Subscription;
  readonly user = signal<CurrentUser | null>(null);
  readonly displayName = computed(() => this.user()?.status === 'AUTHENTICATED' ? this.user()?.displayName ?? '' : '');
  readonly notice = computed(() => {
    switch (this.user()?.status) {
      case 'UNASSIGNED': return 'No Thereabout user assigned';
      case 'INVALID_IDENTITY': return 'Your sign-in could not be verified';
      case 'UNAVAILABLE': return 'User information is temporarily unavailable';
      default: return '';
    }
  });

  refresh(): void {
    this.request?.unsubscribe();
    this.user.set(null);
    this.request = this.api.getCurrentUser().subscribe({
      next: user => this.user.set(user),
      error: () => this.user.set({status: 'UNAVAILABLE'})
    });
  }
}
