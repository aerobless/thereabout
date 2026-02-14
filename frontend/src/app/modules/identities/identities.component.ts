import {Component, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {CardModule} from 'primeng/card';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {DialogModule} from 'primeng/dialog';
import {FloatLabelModule} from 'primeng/floatlabel';
import {InputTextModule} from 'primeng/inputtext';
import {TableModule} from 'primeng/table';
import {TagModule} from 'primeng/tag';
import {ToastModule} from 'primeng/toast';
import {TooltipModule} from 'primeng/tooltip';
import {SelectModule} from 'primeng/select';
import {ConfirmationService, MessageService} from 'primeng/api';
import {ToolbarComponent} from '../../shared/toolbar/toolbar.component';
import {
    Identity,
    IdentityInApplication,
    IdentityInApplicationService,
    IdentityService
} from '../../../../generated/backend-api/thereabout';

@Component({
    selector: 'app-identities',
    imports: [
        FormsModule,
        ButtonModule,
        CardModule,
        ConfirmDialogModule,
        DialogModule,
        FloatLabelModule,
        InputTextModule,
        TableModule,
        TagModule,
        ToastModule,
        TooltipModule,
        SelectModule,
        ToolbarComponent,
    ],
    providers: [ConfirmationService, MessageService],
    templateUrl: './identities.component.html',
    styleUrl: './identities.component.scss'
})
export class IdentitiesComponent implements OnInit {

    identities: Identity[] = [];
    unlinkedAppIdentities: IdentityInApplication[] = [];

    // Identity dialog
    identityDialogVisible = false;
    editingIdentity: Identity = this.emptyIdentity();
    isNewIdentity = true;

    // Link dialog
    linkDialogVisible = false;
    linkingAppIdentity: IdentityInApplication | null = null;
    selectedIdentityForLink: Identity | null = null;

    constructor(
        private readonly identityService: IdentityService,
        private readonly identityInApplicationService: IdentityInApplicationService,
        private readonly confirmationService: ConfirmationService,
        private readonly messageService: MessageService,
    ) {}

    ngOnInit(): void {
        this.loadIdentities();
        this.loadUnlinkedAppIdentities();
    }

    loadIdentities(): void {
        this.identityService.getIdentities().subscribe(identities => {
            this.identities = identities;
        });
    }

    loadUnlinkedAppIdentities(): void {
        this.identityInApplicationService.getUnlinkedIdentityInApplications().subscribe(appIdentities => {
            this.unlinkedAppIdentities = appIdentities;
        });
    }

    // --- Identity CRUD ---

    showNewIdentityDialog(): void {
        this.editingIdentity = this.emptyIdentity();
        this.isNewIdentity = true;
        this.identityDialogVisible = true;
    }

    showEditIdentityDialog(identity: Identity): void {
        this.editingIdentity = {...identity, identityInApplications: [...(identity.identityInApplications || [])]};
        this.isNewIdentity = false;
        this.identityDialogVisible = true;
    }

    saveIdentity(): void {
        if (this.isNewIdentity) {
            this.identityService.createIdentity(this.editingIdentity).subscribe({
                next: () => {
                    this.messageService.add({severity: 'success', summary: 'Created', detail: 'Identity created successfully'});
                    this.identityDialogVisible = false;
                    this.loadIdentities();
                },
                error: () => {
                    this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to create identity'});
                }
            });
        } else {
            this.identityService.updateIdentity(this.editingIdentity.id, this.editingIdentity).subscribe({
                next: () => {
                    this.messageService.add({severity: 'success', summary: 'Updated', detail: 'Identity updated successfully'});
                    this.identityDialogVisible = false;
                    this.loadIdentities();
                },
                error: () => {
                    this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update identity'});
                }
            });
        }
    }

    confirmDeleteIdentity(identity: Identity): void {
        this.confirmationService.confirm({
            message: `Are you sure you want to delete "${identity.shortName}"?`,
            header: 'Delete Identity',
            icon: 'pi pi-exclamation-triangle',
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => {
                this.identityService.deleteIdentity(identity.id).subscribe({
                    next: () => {
                        this.messageService.add({severity: 'success', summary: 'Deleted', detail: 'Identity deleted successfully'});
                        this.loadIdentities();
                        this.loadUnlinkedAppIdentities();
                    },
                    error: () => {
                        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to delete identity'});
                    }
                });
            }
        });
    }

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
                this.loadIdentities();
                this.loadUnlinkedAppIdentities();
            },
            error: () => {
                this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to link application identity'});
            }
        });
    }

    unlinkAppIdentity(appIdentity: IdentityInApplication): void {
        this.identityInApplicationService.unlinkIdentityInApplication(appIdentity.id).subscribe({
            next: () => {
                this.messageService.add({severity: 'success', summary: 'Unlinked', detail: 'Application identity unlinked successfully'});
                this.loadIdentities();
                this.loadUnlinkedAppIdentities();
                // Update the editing dialog's list if open
                if (this.identityDialogVisible && this.editingIdentity.identityInApplications) {
                    this.editingIdentity.identityInApplications =
                        this.editingIdentity.identityInApplications.filter(a => a.id !== appIdentity.id);
                }
            },
            error: () => {
                this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to unlink application identity'});
            }
        });
    }

    // --- Helpers ---

    private emptyIdentity(): Identity {
        return {id: 0, shortName: '', firstName: '', lastName: '', email: '', relationship: '', identityInApplications: []};
    }

    getAppIdentityCount(identity: Identity): number {
        return identity.identityInApplications?.length || 0;
    }

    identityLabel(identity: Identity): string {
        const parts = [identity.shortName];
        if (identity.firstName || identity.lastName) {
            parts.push(`(${[identity.firstName, identity.lastName].filter(Boolean).join(' ')})`);
        }
        return parts.join(' ');
    }
}
