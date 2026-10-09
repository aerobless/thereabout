import {IdentityWriteKeys} from "../identity-write-keys";
import {IdentityNavigation} from "../identity-navigation";
import {GroupMembersComponent} from './group-members.component';
import {fullName} from '../../../shared/identity-names';
import {IdentityEditorComponent} from '../identity-editor/identity-editor.component';
import { AppModalComponent } from '../../../shared/modal/app-modal.component';
import {MessageService} from 'primeng/api';
import {CreateUserDialogComponent} from '../create-user-dialog/create-user-dialog.component';
import {registerRefresh} from '../../../shared/refresh/refresh-coordinator';
import {CurrentUserService} from '../../../shared/current-user/current-user.service';
import {inject, ChangeDetectorRef, Component, OnInit, ChangeDetectionStrategy, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TableModule} from 'primeng/table';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';

@Component({
    selector: 'app-identity-detail',
    imports: [GroupMembersComponent,
        IdentityEditorComponent,
        AppModalComponent,
        CreateUserDialogComponent,
        RouterModule,
        ButtonModule,
        CardModule,
        TableModule,
    ],
    templateUrl: './identity-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './identity-detail.component.scss'
})
export class IdentityDetailComponent implements OnInit {
  private readonly writeKeys = new IdentityWriteKeys();
  readonly fullName = fullName;
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly currentUser = inject(CurrentUserService);
  readonly canManageUsers = this.currentUser.canManageUsers;
  private readonly refresh = registerRefresh(() => this.loadIdentity(), () => !!this.creatingUser || this.editing || this.deleteVisible());

    editing = false;
    creatingUser: Identity | null = null;
    private readonly identityState = signal<Identity | null>(null);
    private readonly navigation = inject(IdentityNavigation, {optional:true});
    get identity() {return this.identityState();}
    set identity(value: Identity | null) {this.identityState.set(value); this.navigation?.group.set(!!value?.isGroup);}

    constructor(
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly messages: MessageService,
        private readonly identityService: IdentityService,
    ) {}

    readonly deleteVisible = signal(false);
    readonly deleting = signal(false);

    impersonate(): void {
        if (this.identity) this.currentUser.impersonate(this.identity);
    }

    confirmDelete(): void {
        if (this.identity && !this.identity.role) this.deleteVisible.set(true);
    }

    deleteIdentity(): void {
        const identity = this.identity;
        if (!identity || identity.role || this.deleting()) return;
        this.deleting.set(true);
        this.identityService.deleteIdentity(identity.id, identity.version, this.writeKeys.key("delete", {id:identity.id, version:identity.version})).subscribe({
            next: () => {
                this.deleting.set(false);
                this.deleteVisible.set(false);
                this.messages.add({severity: 'success', summary: 'Deleted', detail: 'Identity deleted successfully'});
                void this.router.navigate([identity.isGroup ? '/identities/groups' : '/identities']);
            },
            error: () => {
                this.deleting.set(false);
                this.messages.add({severity: 'error', summary: 'Error', detail: 'Unable to delete identity'});
            }
        });
    }

    ngOnInit(): void { this.loadIdentity(); }

    loadIdentity() {
        const id = Number(this.route.snapshot.paramMap.get('id'));
        this.identityService.getIdentities().pipe(this.refresh.track('identity')).subscribe({next: identities => {
            this.identity = identities.find(i => i.id === id) || null;
            this.changeDetector.markForCheck();
        }, error: () => {}});
    }
}
