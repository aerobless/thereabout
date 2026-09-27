import {IdentityEditorComponent} from '../identity-editor/identity-editor.component';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {CreateUserDialogComponent} from '../create-user-dialog/create-user-dialog.component';
import {registerRefresh} from '../../../shared/refresh/refresh-coordinator';
import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {TableModule} from 'primeng/table';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';

@Component({
    selector: 'app-identity-detail',
    imports: [
        IdentityEditorComponent,
        ConfirmDialogModule,
        CreateUserDialogComponent,
        RouterModule,
        ButtonModule,
        CardModule,
        TableModule,
    ],
    providers: [ConfirmationService],
    templateUrl: './identity-detail.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './identity-detail.component.scss'
})
export class IdentityDetailComponent implements OnInit {
  private readonly refresh = registerRefresh(() => this.loadIdentity(), () => !!this.creatingUser || this.editing);

    editing = false;
    creatingUser: Identity | null = null;
    identity: Identity | null = null;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmation: ConfirmationService,
        private readonly messages: MessageService,
        private readonly identityService: IdentityService,
    ) {}

    confirmDelete(): void {
        const identity = this.identity;
        if (!identity || identity.isUser) return;
        this.confirmation.confirm({
            header: 'Delete Identity', message: `Are you sure you want to delete "${identity.shortName}"?`,
            icon: 'pi pi-exclamation-triangle', acceptButtonStyleClass: 'p-button-danger',
            accept: () => this.identityService.deleteIdentity(identity.id).subscribe({
                next: () => {
                    this.messages.add({severity: 'success', summary: 'Deleted', detail: 'Identity deleted successfully'});
                    void this.router.navigate(['/identities']);
                },
                error: () => this.messages.add({severity: 'error', summary: 'Error', detail: 'Unable to delete identity'})
            })
        });
    }

    ngOnInit(): void { this.loadIdentity(); }

    loadIdentity() {
        const id = Number(this.route.snapshot.paramMap.get('id'));
        this.identityService.getIdentities().pipe(this.refresh.track('identity')).subscribe({next: identities => {
            this.identity = identities.find(i => i.id === id) || null;
        }, error: () => {}});
    }
}
