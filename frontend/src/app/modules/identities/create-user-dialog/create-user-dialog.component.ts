import {ChangeDetectionStrategy, Component, inject, input, output, signal} from '@angular/core';
import {SelectModule} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import { AppModalComponent } from '../../../shared/modal/app-modal.component';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {MessageService} from 'primeng/api';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../../shared/current-user/current-user.service';

// Kept in sync with CloudflareEmail on the backend.
const EMAIL = /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/;
export function normalizeCloudflareEmail(email: string): string { return email.trim().toLowerCase(); }
export function validCloudflareEmail(email: string): boolean {
  return email.length <= 254 && email.indexOf('@') <= 64 && EMAIL.test(email);
}

@Component({
  selector: 'app-create-user-dialog',
  imports: [SelectModule, FormsModule, AppModalComponent, ButtonModule, InputTextModule],
  templateUrl: './create-user-dialog.component.html',
  styles: [`.dialog-form {display: flex; flex-direction: column; gap: .75rem; padding-top: .5rem;}
    input {width: 100%;} .field-error {color: var(--p-red-600);} p {margin: 0 0 .5rem; overflow-wrap: anywhere;}`],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateUserDialogComponent {
  readonly identity = input.required<Identity>();
  readonly closed = output<void>();
  readonly created = output<Identity>();
  private readonly api = inject(IdentityService);
  private readonly messages = inject(MessageService);
  private readonly currentUser = inject(CurrentUserService);
  readonly busy = signal(false);
  readonly error = signal('');
  email = '';
  role: 'ADMIN' | 'USER' = 'USER';
  readonly roles = [{label: 'User', value: 'USER'}, {label: 'Admin', value: 'ADMIN'}];

  close(): void { if (!this.busy()) this.closed.emit(); }
  create(): void {
    if (this.busy()) return;
    this.email = normalizeCloudflareEmail(this.email);
    if (!validCloudflareEmail(this.email)) {
      this.error.set('Enter a valid Cloudflare email address.');
      return;
    }
    this.error.set('');
    this.busy.set(true);
    this.api.createIdentityUser(this.identity().id, {email: this.email, role: this.role}).subscribe({
      next: identity => {
        this.busy.set(false);
        this.messages.add({severity: 'success', summary: 'User created', detail: `${identity.shortName} is now a Thereabout user.`});
        this.currentUser.load();
        this.created.emit(identity);
        this.closed.emit();
      },
      error: error => {
        this.busy.set(false);
        this.error.set(error.status === 409
          ? 'This email is already assigned, or this person already has another email. Check the identity and email.'
          : error.status === 400 ? 'Check the email address. Only a person can become a user.'
          : 'Unable to create user. Please try again.');
      }
    });
  }
}
