import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {DestroyRef, inject, Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {InputTextModule} from "primeng/inputtext";
import {ReactiveFormsModule} from "@angular/forms";
import {FieldsetModule} from "primeng/fieldset";
import {CardModule} from "primeng/card";
import {PanelModule} from "primeng/panel";
import {FileUploadErrorEvent, FileUploadHandlerEvent, FileUploadModule} from "primeng/fileupload";
import {
    FileImportStatus,
    FrontendConfigurationResponse,
    FrontendService,
    IdentityInApplicationService,
    TelegramStatus
} from "../../../../generated/backend-api/thereabout";
import {MessageService} from "primeng/api";

import {catchError, finalize, interval, Observable, of, switchMap, takeWhile} from "rxjs";
import {ChipModule} from "primeng/chip";
import {TooltipModule} from "primeng/tooltip";
import {SelectModule} from "primeng/select";
import {FormsModule} from "@angular/forms";
import { HttpClient } from "@angular/common/http";
import {ProgressBarModule} from "primeng/progressbar";
import {AutoCompleteCompleteEvent, AutoCompleteModule} from "primeng/autocomplete";
import {DatePipe} from "@angular/common";

interface ImportTypeOption {
    label: string;
    value: string;
    accept: string;
    description: string;
}

@Component({
    selector: 'app-configuration',
    imports: [
    ButtonModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    ReactiveFormsModule,
    FieldsetModule,
    CardModule,
    PanelModule,
    FileUploadModule,
    ChipModule,
    TooltipModule,
    SelectModule,
    FormsModule,
    ProgressBarModule,
    AutoCompleteModule,
    DatePipe
],
    templateUrl: './configuration.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './configuration.component.scss'
})
export class ConfigurationComponent implements OnInit {
    private readonly destroyRef = inject(DestroyRef);
    private telegramSaving = false;
    private uploading = false;
    private readonly refresh = registerRefresh(() => {
        this.loadConfiguration();
        this.loadChatReceivers();
        // Refresh snapshots only: existing polling continues without creating new timers.
        this.updateImportStatus().pipe(this.refresh.track('import'), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: status => { this.importStatus = status.status; this.importStatusProgress = status.progress; },
            error: () => {}
        });
        this.frontendService.getTelegramStatus().pipe(this.refresh.track('telegram'), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: status => this.telegramStatus = status,
            error: () => {}
        });
    }, () => this.telegramSaving || this.uploading);

    importStatus: FileImportStatus.StatusEnum | unknown;
    importStatusProgress: number = 0;
    importDisabled = false;
    thereaboutConfig?: FrontendConfigurationResponse;

    telegramStatus: TelegramStatus | null = null;
    telegramPhone = '';
    telegramCode = '';
    telegramPassword = '';
    telegramPolling = false;
    telegramResyncPolling = false;

    // Receiver field for WhatsApp import
    receiverName: string = '';
    whatsAppReceivers: string[] = [];
    filteredReceivers: string[] = [];

    importTypeOptions: ImportTypeOption[] = [
        {
            label: 'Google Maps Records.json',
            value: 'GOOGLE_MAPS_RECORDS',
            accept: '.json',
            description: 'Upload your <a href="https://takeout.google.com/" target="_blank">Google Maps <b>Records.json</b></a> here. It will then be imported into Thereabout. Beware that for large files, the import may take a while.'
        },
        {
            label: 'WhatsApp Chat History',
            value: 'WHATSAPP_CHAT',
            accept: '.txt',
            description: 'Upload your WhatsApp chat export (.txt) here. It will be imported into Thereabout.'
        },
        {
            label: 'Health Auto Export JSON',
            value: 'HEALTH_AUTO_EXPORT_JSON',
            accept: '.json',
            description: 'Upload your Health Auto Export JSON (workouts, steps, metrics, etc.) here. It will be imported into Thereabout using the same health data storage as the REST API.'
        }
    ];
    selectedImportType: ImportTypeOption = this.importTypeOptions[0];

    constructor(
        private messageService: MessageService,
        private frontendService: FrontendService,
        private identityInApplicationService: IdentityInApplicationService,
        private http: HttpClient
    ) {
    }

    onError($event: FileUploadErrorEvent) {
        this.messageService.add({severity: 'error', summary: 'Upload failed', detail: $event.error?.error.message});
        console.log($event);
    }

    onUpload() {
        this.messageService.add({severity: 'info', summary: 'Import in progress', detail: 'Successfully uploaded file is now processing...'});
        this.updateOrPollImportStatus();
    }

    customUpload(event: FileUploadHandlerEvent) {
        const file = event.files[0];
        const formData = new FormData();
        formData.append('file', file, file.name);
        formData.append('importType', this.selectedImportType.value);

        if (this.isReceiverRequired) {
            formData.append('receiver', this.receiverName);
        }

        this.importDisabled = true;

        this.uploading = true;
        this.http.post('/backend/api/v1/config/import-file', formData).pipe(finalize(() => this.uploading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => {
                this.onUpload();
            },
            error: (err) => {
                this.messageService.add({severity: 'error', summary: 'Upload failed', detail: err?.error?.message || 'Upload failed'});
                this.importDisabled = false;
            }
        });
    }

    private updateOrPollImportStatus() {
        this.updateImportStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(e => {
            this.importStatus = e.status;
            this.importStatusProgress = e.progress;

            if(e.status === FileImportStatus.StatusEnum.InProgress) {
                interval(1000).pipe(
                    switchMap(() => this.updateImportStatus()),
                    takeWhile((status) => status.status === FileImportStatus.StatusEnum.InProgress, true),
                    catchError(err => {
                        console.error('Polling error', err);
                        return of(null);
                    })
                ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((status) => {
                    if(status){
                        this.importStatus = status.status;
                        this.importStatusProgress = status.progress;
                    }
                    if (status && status.status !== FileImportStatus.StatusEnum.InProgress) {
                        this.messageService.add({
                            severity: 'success',
                            summary: 'Import complete',
                            detail: 'File processing completed.'
                        });
                        this.importDisabled = false;
                    }
                });
            }
        });
    }

    ngOnInit(): void {
        this.loadConfiguration();
        this.updateOrPollImportStatus();
        this.loadChatReceivers();
        this.loadTelegramStatus();
    }

    private loadConfiguration() {
        this.frontendService.getFrontendConfiguration().pipe(this.refresh.track('configuration'), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: config => this.thereaboutConfig = config, error: () => {}
        });
    }

    loadTelegramStatus() {
        this.frontendService.getTelegramStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: (s) => {
                this.telegramStatus = s;
                const wait = s.status === TelegramStatus.StatusEnum.WaitCode
                    || s.status === TelegramStatus.StatusEnum.WaitPassword
                    || s.status === TelegramStatus.StatusEnum.Connecting;
                if (wait && !this.telegramPolling) {
                    this.telegramPolling = true;
                    interval(2000).pipe(
                        switchMap(() => this.frontendService.getTelegramStatus()),
                        takeWhile((next) =>
                            next.status === TelegramStatus.StatusEnum.WaitCode
                            || next.status === TelegramStatus.StatusEnum.WaitPassword
                            || next.status === TelegramStatus.StatusEnum.Connecting,
                            true
                        ),
                    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                        next: (next) => {
                            this.telegramStatus = next;
                            if (next.status === TelegramStatus.StatusEnum.Ready || next.status === TelegramStatus.StatusEnum.Error) {
                                this.telegramPolling = false;
                            }
                        },
                        complete: () => { this.telegramPolling = false; }
                    });
                }
                this.startResyncPollingIfNeeded(s);
            },
            error: () => { this.telegramStatus = null; }
        });
    }

    private startResyncPollingIfNeeded(s: TelegramStatus) {
        const inProgress = s.resyncStatus === TelegramStatus.ResyncStatusEnum.InProgress;
        if (inProgress && !this.telegramResyncPolling) {
            this.telegramResyncPolling = true;
            interval(2000).pipe(
                switchMap(() => this.frontendService.getTelegramStatus()),
                takeWhile((next) => next.resyncStatus === TelegramStatus.ResyncStatusEnum.InProgress, true),
                catchError(() => of(null))
            ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: (next) => {
                    if (next) {
                        this.telegramStatus = next;
                        if (next.resyncStatus !== TelegramStatus.ResyncStatusEnum.InProgress) {
                            const severity = next.resyncStatus === TelegramStatus.ResyncStatusEnum.Complete ? 'success' :
                                next.resyncStatus === TelegramStatus.ResyncStatusEnum.Cancelled ? 'info' : 'warn';
                            const summary = next.resyncStatus === TelegramStatus.ResyncStatusEnum.Complete ? 'Resync complete' :
                                next.resyncStatus === TelegramStatus.ResyncStatusEnum.Cancelled ? 'Resync cancelled' : 'Resync ended';
                            this.messageService.add({ severity, summary, detail: 'Telegram backfill finished.' });
                        }
                    }
                },
                complete: () => { this.telegramResyncPolling = false; }
            });
        }
    }

    connectTelegram() {
        if (!this.telegramPhone?.trim()) return;
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.connectTelegram({ phoneNumber: this.telegramPhone.trim() }).pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => { this.loadTelegramStatus(); },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Telegram connect failed', detail: err?.error?.message || 'Connect failed' })
        });
    }

    submitTelegramCode() {
        if (!this.telegramCode?.trim()) return;
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.submitTelegramCode({ code: this.telegramCode.trim() }).pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => { this.telegramCode = ''; this.loadTelegramStatus(); },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Code failed', detail: err?.error?.message || 'Submit failed' })
        });
    }

    submitTelegramPassword() {
        if (!this.telegramPassword) return;
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.submitTelegramPassword({ password: this.telegramPassword }).pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => { this.telegramPassword = ''; this.loadTelegramStatus(); },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Password failed', detail: err?.error?.message || 'Submit failed' })
        });
    }

    disconnectTelegram() {
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.disconnectTelegram().pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => {
                this.telegramPhone = '';
                this.telegramCode = '';
                this.telegramPassword = '';
                this.loadTelegramStatus();
            },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Disconnect failed', detail: err?.error?.message })
        });
    }

    resyncTelegram() {
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.resyncTelegram().pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => {
                this.messageService.add({ severity: 'info', summary: 'Resync started', detail: 'Telegram messages are syncing. You can cancel anytime.' });
                this.loadTelegramStatus();
            },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Resync failed', detail: err?.error?.message })
        });
    }

    cancelTelegramResync() {
        if (this.telegramSaving) return;
        this.telegramSaving = true;
        this.frontendService.cancelTelegramResync().pipe(finalize(() => this.telegramSaving = false), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: () => { this.loadTelegramStatus(); },
            error: (err) => this.messageService.add({ severity: 'error', summary: 'Cancel failed', detail: err?.error?.message })
        });
    }

    get isTelegramResyncInProgress(): boolean {
        return this.telegramStatus?.resyncStatus === TelegramStatus.ResyncStatusEnum.InProgress;
    }

    get telegramResyncProgress(): number {
        return this.telegramStatus?.resyncProgress ?? 0;
    }

    protected readonly TelegramStatus = TelegramStatus;

    loadChatReceivers() {
        this.identityInApplicationService.getIdentityInApplicationsByApplication('WhatsApp').pipe(this.refresh.track('receivers'), takeUntilDestroyed(this.destroyRef)).subscribe({next: (whatsApp) => {
            this.whatsAppReceivers = whatsApp.map(i => i.identifier);
        }, error: () => {}});
    }

    get receiversForCurrentApp(): string[] {
        if (this.selectedImportType.value === 'WHATSAPP_CHAT') return this.whatsAppReceivers;
        return [];
    }

    filterReceivers(event: AutoCompleteCompleteEvent) {
        const query = event.query.toLowerCase();
        this.filteredReceivers = this.receiversForCurrentApp.filter(r => r.toLowerCase().includes(query));
    }

    onImportTypeChange() {
        this.receiverName = '';
    }

    get isReceiverRequired(): boolean {
        return this.selectedImportType.value === 'WHATSAPP_CHAT';
    }

    get isBrowseDisabled(): boolean {
        if (this.importDisabled) return true;
        if (this.isReceiverRequired && !this.receiverName?.trim()) return true;
        return false;
    }

    private updateImportStatus(): Observable<FileImportStatus> {
        return this.frontendService.fileImportStatus();
    }

    protected readonly FileImportStatus = FileImportStatus;

    configureOverland() {
        const url = `${window.location.protocol}//${window.location.host}/backend/api/v1/location/geojson`;
        const deviceId = 'iPhone';
        window.location.href = `overland://setup?url=${url}&token=${this.thereaboutConfig?.thereaboutApiKey}&device_id=${deviceId}`;
    }
}
