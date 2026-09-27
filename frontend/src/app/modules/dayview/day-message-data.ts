import { Injectable, inject, DestroyRef, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { registerRefresh } from '../../shared/refresh/refresh-coordinator';
import { localDateString } from '../../shared/dates/local-date';
import { DayViewData } from './day-view-data';
import { Message, MessageService as MessageApiService } from '../../../../generated/backend-api/thereabout';
import { CurrentUserService } from '../../shared/users/current-user.service';
@Injectable()
export class DayMessageData {
  private readonly day = inject(DayViewData);
  private readonly destroyRef = inject(DestroyRef);
  private readonly messageApiService = inject(MessageApiService);
  private readonly currentUser = inject(CurrentUserService);
  readonly identityId = computed(() => { const user = this.currentUser.user(); return user?.status === 'AUTHENTICATED' ? user.identityId ?? null : null; });
  private readonly refresh = registerRefresh(() => this.loadMessages(true));
  get selectedDate(): Date { return this.day.selectedDate(); }
  readonly dateToString = localDateString;
  messages = signal<Message[]>([]);
  messagesDialogVisible = signal(false);
  messagesLoading = signal(false);
  messagesError = signal(false);
  private messageRequestId = 0;
  readonly sentMessageCount = computed(() => this.identityId() == null ? 0 :
    this.messages().filter(message => message.sender?.identityId === this.identityId()).length);
  readonly receivedMessageCount = computed(() => this.identityId() == null ? 0 :
    this.messages().filter(message => message.receiver?.identityId === this.identityId() && message.sender?.identityId !== this.identityId()).length);
  loadMessages(preserve = false) {
    if (!this.selectedDate)
      return;
    const dateStr = this.dateToString(this.selectedDate);
    const requestId = ++this.messageRequestId;
    if (!preserve) {
      this.messagesDialogVisible.set(false);
      this.messages.set([]);
    }
    if (!preserve)
      this.messagesLoading.set(true);
    this.messagesError.set(false);
    this.messageApiService.getMessages(dateStr).pipe(this.refresh.track('messages'), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (messages) => {
        if (requestId !== this.messageRequestId)
          return;
        this.messages.set([...messages].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp)));
        this.messagesLoading.set(false);
      },
      error: (error) => {
        if (requestId !== this.messageRequestId)
          return;
        console.error('Error loading messages:', error);
        if (!preserve)
          this.messages.set([]);
        this.messagesLoading.set(false);
        this.messagesError.set(!preserve);
      }
    });
  }
  formatMessageTime(timestamp: string | undefined): string {
    if (!timestamp)
      return '--';
    return new Date(timestamp).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }
}
