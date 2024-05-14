import { Component } from '@angular/core';
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
    FileSelectEvent,
    FileUploadErrorEvent,
    FileUploadEvent,
    FileUploadHandlerEvent,
    FileUploadModule
} from "primeng/fileupload";
import {FrontendService} from "../../../../generated/backend-api/thereabout";
import {MessageService} from "primeng/api";

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
        FileUploadModule
    ],
  templateUrl: './configuration.component.html',
  styleUrl: './configuration.component.scss'
})
export class ConfigurationComponent {

    constructor(private router: Router, private messageService: MessageService) {
    }

    navigateBackToMap() {
        this.router.navigate(['']);
    }

    onError($event: FileUploadErrorEvent) {
        this.messageService.add({severity: 'error', summary: 'Upload failed', detail: $event.error?.error.message});
        console.log($event);
    }

    onUpload($event: FileUploadEvent) {
        this.messageService.add({severity: 'info', summary: 'Import in progress', detail: 'Successfully uploaded file is now processing...'});
    }
}
