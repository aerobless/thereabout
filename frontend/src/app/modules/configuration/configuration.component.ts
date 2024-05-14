import {Component, OnInit, ViewChild} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {InputTextModule} from "primeng/inputtext";
import {ReactiveFormsModule} from "@angular/forms";
import {ToolbarModule} from "primeng/toolbar";
import {Router} from "@angular/router";
import {FieldsetModule} from "primeng/fieldset";
import {CardModule} from "primeng/card";
import {TabViewModule} from "primeng/tabview";
import {PanelModule} from "primeng/panel";
import {
    FileUpload,
    FileUploadErrorEvent,
    FileUploadEvent,
    FileUploadModule
} from "primeng/fileupload";
import {FileImportStatus, FrontendService} from "../../../../generated/backend-api/thereabout";
import {MessageService} from "primeng/api";
import {NgIf} from "@angular/common";
import {catchError, interval, Observable, of, switchMap, takeWhile, tap} from "rxjs";

@Component({
  selector: 'app-configuration',
  standalone: true,
    imports: [
        ButtonModule,
        IconFieldModule,
        InputIconModule,
        InputTextModule,
        ReactiveFormsModule,
        ToolbarModule,
        FieldsetModule,
        CardModule,
        TabViewModule,
        PanelModule,
        FileUploadModule,
        NgIf
    ],
  templateUrl: './configuration.component.html',
  styleUrl: './configuration.component.scss'
})
export class ConfigurationComponent implements OnInit {

    importStatus: FileImportStatus.StatusEnum | unknown;
    importStatusProgress: number = 0;
    importDisabled = false;

    constructor(private router: Router, private messageService: MessageService, private frontendService: FrontendService) {
    }

    navigateBackToMap() {
        this.router.navigate(['']);
    }

    onError($event: FileUploadErrorEvent) {
        this.messageService.add({severity: 'error', summary: 'Upload failed', detail: $event.error?.error.message});
        console.log($event);
    }

    onUpload() {
        this.messageService.add({severity: 'info', summary: 'Import in progress', detail: 'Successfully uploaded file is now processing...'});
        this.updateOrPollImportStatus();
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
        this.updateOrPollImportStatus();
    }

    private updateImportStatus(): Observable<FileImportStatus> {
        return this.frontendService.fileImportStatus();
    }

    protected readonly FileImportStatus = FileImportStatus;

    onSelect() {
        this.importDisabled = true;
    }
}
