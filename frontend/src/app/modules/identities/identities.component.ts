import {IdentityWriteKeys} from "./identity-write-keys";
import {IdentityNavigation} from "./identity-navigation";
import {toSignal} from "@angular/core/rxjs-interop";
import {map, of} from "rxjs";
import {ActivatedRoute} from "@angular/router";
import {fullName} from '../../shared/identity-names';
import {IdentityEditorComponent} from './identity-editor/identity-editor.component';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {inject, signal, computed, effect, ChangeDetectorRef, Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
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

type IdentityRow = Identity & {appIdentityCount: number; fullName: string};

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
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './identities.component.scss'
})
export class IdentitiesComponent implements OnInit {
  private readonly writeKeys = new IdentityWriteKeys();
  readonly nameSort = [{field: 'firstName', order: 1}, {field: 'lastName', order: 1}];
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly refresh = registerRefresh(() => { this.loadIdentities(); this.loadUnlinkedAppIdentities(); }, () => this.identityDialogVisible || this.linkDialogVisible);

    private readonly route = inject(ActivatedRoute);
    private readonly navigation = inject(IdentityNavigation, {optional:true});
    readonly isGroup = toSignal((this.route.data ?? of(this.route.snapshot?.data ?? {})).pipe(map(data => data['isGroup'] === true)), {initialValue:false});
    private readonly identityRows = signal<IdentityRow[]>([]);
    readonly visibleIdentities = computed(() => this.identityRows().filter(identity => !!identity.isGroup === this.isGroup()));
    get identities() { return this.visibleIdentities(); }
    set identities(rows: IdentityRow[]) { this.identityRows.set(rows); }
    private readonly personApps = signal<IdentityInApplication[]>([]);
    private readonly groupApps = signal<IdentityInApplication[]>([]);
    get unlinkedAppIdentities() { return this.personApps(); }
    set unlinkedAppIdentities(rows: IdentityInApplication[]) { this.personApps.set(rows); }
    get unlinkedGroupIdentities() { return this.groupApps(); }
    set unlinkedGroupIdentities(rows: IdentityInApplication[]) { this.groupApps.set(rows); }
    identityFilter = '';
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
    ) { effect(() => this.navigation?.group.set(this.isGroup())); }

    ngOnInit(): void {
        this.loadIdentities();
        this.loadUnlinkedAppIdentities();
    }

    loadIdentities(): void {
        this.identityService.getIdentities().pipe(this.refresh.track('identities')).subscribe({next: identities => {
            this.identities = identities.map(identity => ({...identity, fullName: fullName(identity), appIdentityCount: identity.identityInApplications?.length ?? 0}));
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
            this.selectedIdentityForLink.id, this.selectedIdentityForLink.version, this.linkingAppIdentity.version, this.writeKeys.key("link", {id:this.linkingAppIdentity.id, version:this.linkingAppIdentity.version, identityId:this.selectedIdentityForLink.id, identityVersion:this.selectedIdentityForLink.version})
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
        return identity.isGroup ? `${fullName(identity)} (group)` : fullName(identity);
    }
}
