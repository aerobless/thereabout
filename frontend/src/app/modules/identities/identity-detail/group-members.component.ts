import {ChangeDetectionStrategy, Component, computed, inject, input, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MultiSelectModule} from 'primeng/multiselect';
import {MessageService} from 'primeng/api';
import {firstValueFrom, forkJoin, map} from 'rxjs';
import {IdentityService} from '../../../../../generated/backend-api/thereabout';
import {loadResource, errorMessage} from '../../finances/shared/finance-resource';

@Component({
  selector:'app-group-members', imports:[FormsModule,MultiSelectModule], changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<h3>Members</h3><p>Members can read this group's complete message history. Removing a member revokes access.</p>
    @if(state().data; as data) {
      <p-multiselect ariaLabel="Group members" placeholder="Select members" [options]="data.users" optionLabel="firstName" optionValue="id" [ngModel]="selection() ?? data.members.userIds" (ngModelChange)="selection.set($event)" [disabled]="saving()" appendTo="body" />
      <button class="app-button primary" [disabled]="saving() || selection() === null" (click)="save()">Save members</button>
    } @else if(state().loading) { <p role="status">Loading members…</p> }
    @if(error() || state().error) { <p role="alert">{{error() || state().error}}</p> }`,
  styles:`:host { display:block; } p { color:var(--app-muted); line-height:1.5; } p-multiselect { display:flex; margin:1rem 0; width:min(100%,30rem); }`
})
export class GroupMembersComponent {
  readonly groupId=input.required<number>();
  private readonly api=inject(IdentityService);
  private readonly messages=inject(MessageService);
  private readonly revision=signal(0);
  private readonly query=computed(()=>({id:this.groupId(),revision:this.revision()}));
  readonly state=loadResource(this.query,q=>forkJoin({members:this.api.getGroupMembers(q.id), users:this.api.getIdentities()}).pipe(
    // Only existing personal-data users are eligible; use first names in this user-only selector.
    map(data=>({...data, users:data.users.filter(user=>!!user.role && !user.isGroup)}))
  ));
  readonly selection=signal<number[] | null>(null);
  readonly saving=signal(false); readonly error=signal('');
  async save() {
    const members=this.state().data?.members, userIds=this.selection(); if(!members || !userIds || this.saving())return;
    this.saving.set(true);this.error.set('');
    try { await firstValueFrom(this.api.saveGroupMembers(this.groupId(),{version:members.version,userIds}));this.selection.set(null);this.revision.update(n=>n+1);this.messages.add({severity:'success',summary:'Members saved',life:3000}); }
    catch(error) { this.error.set(errorMessage(error)); } finally { this.saving.set(false); }
  }
}
