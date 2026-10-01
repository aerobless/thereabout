import {IdentityEditorComponent} from './identity-editor/identity-editor.component';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {inject, ChangeDetectorRef, Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import { AppModalComponent } from '../../shared/modal/app-modal.component';
import {InputTextModule} from 'primeng/inputtext';
import {TableModule} from 'primeng/table';
import {TagModule} from 'primeng/tag';
import {ToastModule} from 'primeng/toast';
import {TooltipModule} from 'primeng/tooltip';
import {SelectModule} from 'primeng/select';
import {IconFieldModule} from 'primeng/iconfield';
import {InputIconModule} from 'primeng/inputicon';
import {MessageService} from 'primeng/api';
import {
    Identity,
    IdentityInApplication,
    IdentityInApplicationService,
    IdentityService
} from '../../../../generated/backend-api/thereabout';

type IdentityRow = Identity & {appIdentityCount: number};

@Component({
    selector: 'app-identities',
    imports: [
        IdentityEditorComponent,
        FormsModule,
        RouterModule,
        ButtonModule,
        CardModule,
        AppModalComponent,
        InputTextModule,
        TableModule,
        TagModule,
        ToastModule,
        TooltipModule,
        SelectModule,
        IconFieldModule,
        InputIconModule,
    ],
    providers: [MessageService],
    templateUrl: './identities.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './identities.component.scss'
})
export class IdentitiesComponent implements OnInit {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly refresh = registerRefresh(() => { this.loadIdentities(); this.loadUnlinkedAppIdentities(); }, () => this.identityDialogVisible || this.linkDialogVisible);

    identities: IdentityRow[] = [];
    unlinkedAppIdentities: IdentityInApplication[] = [];
    unlinkedGroupIdentities: IdentityInApplication[] = [];
    unlinkedFilter = '';
    unlinkedGroupFilter = '';

    // Identity dialog
    identityDialogVisible = false;

    // Link dialog
    linkDialogVisible = false;
    linkingAppIdentity: IdentityInApplication | null = null;
    selectedIdentityForLink: Identity | null = null;

    constructor(
        private readonly identityService: IdentityService,
        private readonly identityInApplicationService: IdentityInApplicationService,
        private readonly messageService: MessageService,
    ) {}

    ngOnInit(): void {
        this.loadIdentities();
        this.loadUnlinkedAppIdentities();
    }

    loadIdentities(): void {
        this.identityService.getIdentities().pipe(this.refresh.track('identities')).subscribe({next: identities => {
            this.identities = identities.map(identity => ({...identity, appIdentityCount: identity.identityInApplications?.length ?? 0}));
            this.changeDetector.markForCheck();
        }, error: () => {}});
    }

    loadUnlinkedAppIdentities(): void {
        this.identityInApplicationService.getUnlinkedIdentityInApplications().pipe(this.refresh.track('unlinked')).subscribe({next: appIdentities => {
            this.unlinkedAppIdentities = appIdentities.filter(a => !a.isGroup);
            this.unlinkedGroupIdentities = appIdentities.filter(a => a.isGroup);
            this.changeDetector.markForCheck();
        }, error: () => {}});
    }

    showNewIdentityDialog(): void { this.identityDialogVisible = true; }

    // --- Link / Unlink ---

    showLinkDialog(appIdentity: IdentityInApplication): void {
        this.linkingAppIdentity = appIdentity;
        this.selectedIdentityForLink = null;
        this.linkDialogVisible = true;
    }

    linkAppIdentity(): void {
        if (!this.linkingAppIdentity || !this.selectedIdentityForLink) return;

        this.identityInApplicationService.linkIdentityInApplication(
            this.linkingAppIdentity.id,
            this.selectedIdentityForLink.id
        ).subscribe({
            next: () => {
                this.messageService.add({severity: 'success', summary: 'Linked', detail: 'Application identity linked successfully'});
                this.linkDialogVisible = false;
                this.changeDetector.markForCheck();
                this.loadIdentities();
                this.loadUnlinkedAppIdentities();
            },
            error: () => {
                this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to link application identity'});
            }
        });
    }

    identityLabel(identity: Identity): string {
        return identity.isGroup ? `${identity.shortName} (group)` : identity.shortName;
    }
}
