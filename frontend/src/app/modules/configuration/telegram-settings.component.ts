import {ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {EMPTY, Observable, Subscription, expand, finalize, switchMap, timer} from 'rxjs';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {ProgressBarModule} from 'primeng/progressbar';
import {MessageService} from 'primeng/api';
import {FrontendService, TelegramStatus} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';

@Component({
  selector: 'app-telegram-settings', imports: [DatePipe, FormsModule, CardModule, ButtonModule, InputTextModule, ProgressBarModule],
  templateUrl: './telegram-settings.component.html', styleUrl: './configuration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TelegramSettingsComponent {
  private readonly api = inject(FrontendService);
  private readonly toast = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private polling?: Subscription;
  readonly status = signal<TelegramStatus | null>(null);
  readonly phone = signal('');
  readonly code = signal('');
  readonly password = signal('');
  readonly saving = signal(false);
  readonly error = signal('');
  readonly resyncing = computed(() => this.status()?.resyncStatus === 'IN_PROGRESS');
  protected readonly TelegramStatus = TelegramStatus;
  private readonly refresh = registerRefresh(() => this.loadStatus(), () => this.saving());

  constructor() { this.loadStatus(); }

  loadStatus(): void {
    this.polling?.unsubscribe();
    this.error.set('');
    this.polling = this.api.getTelegramStatus().pipe(
      this.refresh.track('telegram'),
      expand(status => ['CONNECTING', 'WAIT_CODE', 'WAIT_PASSWORD', 'SYNCING'].includes(status.status) || status.resyncStatus === 'IN_PROGRESS'
        ? timer(2000).pipe(switchMap(() => this.api.getTelegramStatus())) : EMPTY),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: status => {
        const wasResyncing = this.resyncing();
        this.status.set(status);
        if (wasResyncing && !this.resyncing()) this.toast.add({
          severity: status.resyncStatus === 'COMPLETE' ? 'success' : status.resyncStatus === 'CANCELLED' ? 'info' : 'warn',
          summary: status.resyncStatus === 'COMPLETE' ? 'Resync complete' : status.resyncStatus === 'CANCELLED' ? 'Resync cancelled' : 'Resync ended'
        });
      }, error: () => this.error.set('Telegram status could not be loaded.')
    });
  }

  connectTelegram(): void { if (this.phone().trim()) this.run(this.api.connectTelegram({phoneNumber: this.phone().trim()})); }
  submitTelegramCode(): void { if (this.code().trim()) this.run(this.api.submitTelegramCode({code: this.code().trim()})); }
  submitTelegramPassword(): void {
    const password = this.password();
    if (!password || this.saving()) return;
    this.password.set('');
    this.run(this.api.submitTelegramPassword({password}));
  }
  disconnectTelegram(): void {
    this.phone.set(''); this.code.set(''); this.password.set('');
    this.run(this.api.disconnectTelegram());
  }
  resyncTelegram(): void { this.run(this.api.resyncTelegram()); }
  cancelTelegramResync(): void { this.run(this.api.cancelTelegramResync()); }

  private run(request: Observable<unknown>): void {
    if (this.saving()) return;
    this.saving.set(true);
    request.pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loadStatus(),
      error: () => this.toast.add({severity: 'error', summary: 'Telegram action failed'})
    });
  }
}
