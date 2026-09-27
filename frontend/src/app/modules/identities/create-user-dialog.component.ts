import {Component, inject, input, output, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {MessageService} from 'primeng/api';
import {Identity, UsersService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../shared/users/current-user.service';

@Component({
  selector: 'app-create-user-dialog',
  imports: [FormsModule, DialogModule, ButtonModule, InputTextModule],
  template: `
    <p-dialog header="Create User" [visible]="!!identity()" (visibleChange)="close()"
      [modal]="true" [closable]="!saving()" [closeOnEscape]="!saving()" [draggable]="false"
      [style]="{width: '28rem', maxWidth: 'calc(100vw - 2rem)'}">
      <form #form="ngForm" (ngSubmit)="form.valid && save()" class="create-user-form">
        <p class="person-name">{{ identity()?.shortName }}</p>
        <label for="cloudflare-email">Cloudflare email</label>
        <input pInputText id="cloudflare-email" name="email" type="email" required email maxlength="255"
          [(ngModel)]="email" (blur)="email = email.trim().toLowerCase()" #emailControl="ngModel" [disabled]="saving()" autocomplete="email"
          [attr.aria-invalid]="!!error() || (emailControl.invalid && emailControl.touched)"
          aria-describedby="cloudflare-email-error" />
        <div id="cloudflare-email-error" role="alert" class="field-error">
          @if (error()) { {{ error() }} }
          @else if (emailControl.invalid && emailControl.touched) { Enter a valid email address. }
        </div>
        <div class="dialog-actions">
          <p-button label="Cancel" severity="secondary" [text]="true" [disabled]="saving()" (onClick)="close()" />
          <p-button label="Create User" type="submit" [loading]="saving()" [disabled]="!form.valid || saving()" />
        </div>
      </form>
    </p-dialog>`,
  styles: [`
    .create-user-form { display: flex; flex-direction: column; gap: .75rem; padding-top: .5rem; }
    .person-name { margin: 0 0 .5rem; color: var(--p-text-muted-color); }
    .field-error { color: var(--p-red-600); font-size: .875rem; }
    .field-error:empty { display: none; }
    .dialog-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .5rem; margin-top: 1rem; }
  `]
})
export class CreateUserDialogComponent {
  readonly identity = input.required<Identity>();
  readonly created = output<Identity>();
  readonly dismissed = output<void>();
  readonly saving = signal(false);
  readonly error = signal('');
  email = '';
  private readonly api = inject(UsersService);
  private readonly messages = inject(MessageService);
  private readonly currentUser = inject(CurrentUserService);

  close(): void { if (!this.saving()) this.dismissed.emit(); }

  save(): void {
    if (this.saving()) return;
    const email = this.email.trim().toLowerCase();
    if (!email || this.identity().isGroup || this.identity().isUser) return;
    this.saving.set(true);
    this.error.set('');
    this.api.createUser(this.identity().id, {email}).subscribe({
      next: identity => {
        this.saving.set(false);
        this.currentUser.refresh();
        this.messages.add({severity: 'success', summary: 'User created', detail: `${identity.shortName} is linked to Cloudflare.`});
        this.created.emit(identity);
      },
      error: error => {
        this.saving.set(false);
        this.error.set(error.status === 409 ? 'This email is already assigned, or this person already has a user.'
          : error.status === 400 ? 'Check the email address and try again.'
          : error.status === 401 || error.status === 403 ? 'Your sign-in does not allow creating users here.'
          : 'Unable to create the user. Please try again.');
      }
    });
  }
}
