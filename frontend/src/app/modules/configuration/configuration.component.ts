import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {InputTextModule} from "primeng/inputtext";
import {ReactiveFormsModule} from "@angular/forms";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {FieldsetModule} from "primeng/fieldset";
import {CardModule} from "primeng/card";
import {PanelModule} from "primeng/panel";
import {FileUploadErrorEvent, FileUploadHandlerEvent, FileUploadModule} from "primeng/fileupload";
import {
    FileImportStatus,
    FrontendConfigurationResponse,
    FrontendService,
    IdentityInApplicationService
} from "../../../../generated/backend-api/thereabout";
import {MessageService} from "primeng/api";

import {catchError, combineLatest, interval, Observable, of, switchMap, takeWhile} from "rxjs";
import {ChipModule} from "primeng/chip";
import {TooltipModule} from "primeng/tooltip";
import {SelectModule} from "primeng/select";
import {FormsModule} from "@angular/forms";
import { HttpClient } from "@angular/common/http";
import {ProgressBarModule} from "primeng/progressbar";
import {AutoCompleteCompleteEvent, AutoCompleteModule} from "primeng/autocomplete";

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
    ToolbarComponent,
    FieldsetModule,
    CardModule,
    PanelModule,
    FileUploadModule,
    ChipModule,
    TooltipModule,
    SelectModule,
    FormsModule,
    ProgressBarModule,
    AutoCompleteModule
],
    templateUrl: './configuration.component.html',
    styleUrl: './configuration.component.scss'
})
export class ConfigurationComponent implements OnInit {

    importStatus: FileImportStatus.StatusEnum | unknown;
    importStatusProgress: number = 0;
    importDisabled = false;
    thereaboutConfig?: FrontendConfigurationResponse;

    // Receiver field for WhatsApp and Telegram imports
    receiverName: string = '';
    whatsAppReceivers: string[] = [];
    telegramReceivers: string[] = [];
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
            label: 'Telegram Chat History',
            value: 'TELEGRAM_CHAT',
            accept: '.json',
            description: 'Upload your Telegram chat export (JSON from Telegram Desktop) here. It will be imported into Thereabout.'
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

        this.http.post('/backend/api/v1/config/import-file', formData).subscribe({
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
        this.updateImportStatus().subscribe(e => {
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
                ).subscribe((status) => {
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
        this.frontendService.getFrontendConfiguration().subscribe(e => {
            this.thereaboutConfig = e;
        })
        this.updateOrPollImportStatus();
        this.loadChatReceivers();
    }

    loadChatReceivers() {
        combineLatest([
            this.identityInApplicationService.getIdentityInApplicationsByApplication('WhatsApp'),
            this.identityInApplicationService.getIdentityInApplicationsByApplication('Telegram')
        ]).subscribe(([whatsApp, telegram]) => {
            this.whatsAppReceivers = whatsApp.map(i => i.identifier);
            this.telegramReceivers = telegram.map(i => i.identifier);
        });
    }

    get receiversForCurrentApp(): string[] {
        if (this.selectedImportType.value === 'WHATSAPP_CHAT') return this.whatsAppReceivers;
        if (this.selectedImportType.value === 'TELEGRAM_CHAT') return this.telegramReceivers;
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
        return this.selectedImportType.value === 'WHATSAPP_CHAT' || this.selectedImportType.value === 'TELEGRAM_CHAT';
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
