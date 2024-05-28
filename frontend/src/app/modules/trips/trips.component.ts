import { Component } from '@angular/core';
import {ButtonModule} from "primeng/button";
import {ToolbarModule} from "primeng/toolbar";
import {Router} from "@angular/router";
import {MenuItem, MessageService} from "primeng/api";
import {AvatarModule} from "primeng/avatar";
import {PanelModule} from "primeng/panel";
import {ChipModule} from "primeng/chip";
import {TagModule} from "primeng/tag";
import {SplitButtonModule} from "primeng/splitbutton";

@Component({
  selector: 'app-trips',
  standalone: true,
    imports: [
        ButtonModule,
        ToolbarModule,
        AvatarModule,
        PanelModule,
        ChipModule,
        TagModule,
        SplitButtonModule
    ],
  templateUrl: './trips.component.html',
  styleUrl: './trips.component.scss'
})
export class TripsComponent {

    tripPanelMenuItems: MenuItem[] = [
        {
            icon: 'pi pi-pencil',
            label: 'Edit',
            command: () => {}
        },
        {
            icon: 'pi pi-trash',
            label: 'Delete',
            command: () => {}
        }
    ];

    constructor(private router: Router, private messageService: MessageService) {
    }

    navigateBackToMap() {
        this.router.navigate(['']);
    }
}
