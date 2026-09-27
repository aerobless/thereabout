import {Component, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CardModule} from 'primeng/card';
import {SelectModule} from 'primeng/select';
import {ButtonModule} from 'primeng/button';
import {Identity, IdentityService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../shared/current-user/current-user.service';

@Component({
  selector: 'app-impersonation-settings',
  host: {'[style.display]': "currentUser.impersonationAllowed ? 'block' : 'none'"},
  imports: [FormsModule, CardModule, SelectModule, ButtonModule],
  template: `
    @if (currentUser.impersonationAllowed) {
      <p-card>
        <ng-template #header><div class="card-header">
          <i class="pi pi-user card-header-icon" aria-hidden="true"></i>
          <span class="card-header-title">Local impersonation</span>
        </div></ng-template>
        <p>Preview the app as an existing Thereabout user in this local development tab.</p>
        <p>Currently this changes the current user and greeting. Shared location, health and finance data remain the same.</p>
        <label for="impersonation-user">User</label>
        <div class="controls">
          <p-select inputId="impersonation-user" [options]="users()" optionLabel="shortName" dataKey="id"
            [(ngModel)]="selected" [filter]="true" filterBy="shortName" appendTo="body"
            placeholder="Select a user" [loading]="loading()" [disabled]="loading()" [style]="{width: '100%'}" />
          <p-button label="Start impersonation" (onClick)="start()" [disabled]="!selected || loading()" />
        </div>
        @if (error()) { <p role="alert">{{ error() }} <button type="button" (click)="loadUsers()">Retry</button></p> }
        @if (!loading() && !error() && !users().length) { <p>No users available. Create a user from an identity's Actions card first.</p> }
      </p-card>
    }
  `,
  styles: [`:host {display: block; width: 100%; max-width: 1100px;}
    label {display: block; font-weight: 600; margin-bottom: .5rem;}
    .controls {display: flex; flex-wrap: wrap; gap: .75rem; align-items: center;}
    p-select {flex: 1; min-width: 180px;}`]
})
export class ImpersonationSettingsComponent implements OnInit {
  readonly currentUser = inject(CurrentUserService);
  private readonly api = inject(IdentityService);
  readonly users = signal<Identity[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  selected: Identity | null = null;
  ngOnInit(): void { if (this.currentUser.impersonationAllowed) this.loadUsers(); }
  loadUsers(): void {
    if (!this.currentUser.impersonationAllowed) return;
    this.loading.set(true); this.error.set('');
    this.api.getIdentities().subscribe({
      next: identities => {
        this.users.set(identities.filter(user => user.isUser && !user.isGroup));
        this.selected = this.users().find(user => user.id === this.currentUser.impersonatedUser()?.id) ?? null;
        this.loading.set(false);
      },
      error: () => { this.error.set('Unable to load users.'); this.loading.set(false); }
    });
  }
  start(): void { if (this.selected) this.currentUser.impersonate(this.selected); }
}
