import {Component, Input, OnInit, inject, output, signal} from '@angular/core';
import {SelectModule} from 'primeng/select';
import {switchMap, of} from 'rxjs';
import {CurrentUserService} from '../../../shared/current-user/current-user.service';
import {FormsModule} from '@angular/forms';
import { AppModalComponent } from '../../../shared/modal/app-modal.component';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {FloatLabelModule} from 'primeng/floatlabel';
import {CheckboxModule} from 'primeng/checkbox';
import {TableModule} from 'primeng/table';
import {TooltipModule} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';
import {Identity, IdentityInApplication, IdentityService, IdentityInApplicationService} from '../../../../../generated/backend-api/thereabout';

@Component({
  selector: 'app-identity-editor',
  imports: [SelectModule, FormsModule, AppModalComponent, ButtonModule, InputTextModule, FloatLabelModule, CheckboxModule, TableModule, TooltipModule],
  templateUrl: './identity-editor.component.html',
  styles: [`.dialog-form {display: flex; flex-direction: column; gap: 1.5rem; padding-top: .5rem;}
    td {overflow-wrap: anywhere;} h4 {margin-top: 0;} [role=alert] {color: var(--p-red-600);}`]
})
export class IdentityEditorComponent implements OnInit {
  @Input() identity: Identity | null = null;
  readonly closed = output<void>();
  readonly saved = output<void>();
  readonly changed = output<void>();
  readonly busy = signal(false);
  readonly error = signal('');
  editingIdentity: Identity = {id: 0, shortName: '', isGroup: false, identityInApplications: []};
  get isNewIdentity(): boolean { return !this.identity; }
  selectedRole: 'ADMIN' | 'USER' | null = null;
  readonly roles = [{label: 'User', value: 'USER'}, {label: 'Admin', value: 'ADMIN'}];
  private readonly currentUser = inject(CurrentUserService);
  private readonly api = inject(IdentityService);
  private readonly links = inject(IdentityInApplicationService);
  private readonly messages = inject(MessageService);
  ngOnInit(): void {
    this.selectedRole = this.identity?.role ?? null;
    if (this.identity) this.editingIdentity = {...this.identity, identityInApplications: [...(this.identity.identityInApplications ?? [])]};
  }
  close(): void { if (!this.busy()) this.closed.emit(); }
  saveIdentity(): void {
    if (this.busy() || !this.editingIdentity.shortName.trim()) return;
    this.busy.set(true); this.error.set('');
    const request = this.isNewIdentity ? this.api.createIdentity(this.editingIdentity)
      : this.api.updateIdentity(this.editingIdentity.id, this.editingIdentity);
    request.pipe(switchMap(saved => this.identity?.role && this.selectedRole && this.identity.role !== this.selectedRole
      ? this.api.updateIdentityUserRole(saved.id, {role: this.selectedRole}) : of(saved))).subscribe({
      next: () => {
        this.busy.set(false); this.saved.emit(); this.closed.emit();
        if (this.identity?.role !== this.selectedRole) this.currentUser.load();
        this.messages.add({severity: 'success', summary: 'Saved', detail: 'Identity saved successfully'});
      },
      error: error => { this.busy.set(false); this.error.set(error.status === 409 ? 'The last administrator cannot be demoted.' : 'Unable to save identity. Please try again.'); }
    });
  }
  unlinkAppIdentity(app: IdentityInApplication): void {
    if (this.busy() || app.application === 'Cloudflare') return;
    this.busy.set(true); this.error.set('');
    this.links.unlinkIdentityInApplication(app.id).subscribe({
      next: () => {
        this.editingIdentity.identityInApplications = this.editingIdentity.identityInApplications?.filter(item => item.id !== app.id);
        this.busy.set(false); this.changed.emit();
        this.messages.add({severity: 'success', summary: 'Unlinked', detail: 'Application identity unlinked successfully'});
      },
      error: () => { this.busy.set(false); this.error.set('Unable to unlink application identity.'); }
    });
  }
}
