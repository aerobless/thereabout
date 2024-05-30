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
import {Trip} from "../../../../generated/backend-api/thereabout";
import {NgForOf} from "@angular/common";
import {getFlagEmoji} from "../../util/country-util";
import {DialogModule} from "primeng/dialog";
import {InputTextModule} from "primeng/inputtext";
import {InputTextareaModule} from "primeng/inputtextarea";
import {CalendarModule} from "primeng/calendar";

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
        SplitButtonModule,
        NgForOf,
        DialogModule,
        InputTextModule,
        InputTextareaModule,
        CalendarModule
    ],
  templateUrl: './trips.component.html',
  styleUrl: './trips.component.scss'
})
export class TripsComponent {

    addTripDialogVisible: boolean = false;
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

    trips: Trip[] = [
        {
            id: 1,
            start: '2024-01-01',
            end: '2024-01-10',
            description: 'A trip to the beach',
            title: 'Beach Trip',
            visitedCountries: [
                {
                    countryIsoCode: 'DE',
                    countryName: 'Germany'
                },
                {
                    countryIsoCode: 'FR',
                    countryName: 'France'
                }
            ]
        },
        {
            id: 2,
            start: '2024-02-01',
            end: '2024-02-10',
            description: 'A trip to the mountains',
            title: 'Mountain Trip',
            visitedCountries: [
                {
                    countryIsoCode: 'DE',
                    countryName: 'Germany'
                }
            ]
        },
        {
            id: 3,
            start: '2021-03-01',
            end: '2021-03-08',
            description: 'A trip to the city',
            title: 'City Trip',
            visitedCountries: [
                {
                    countryIsoCode: 'FR',
                    countryName: 'France'
                },
                {
                    countryIsoCode: 'IT',
                    countryName: 'Italy'
                }
            ]
        }
    ];

    constructor(private router: Router, private messageService: MessageService) {
    }

    navigateBackToMap() {
        this.router.navigate(['']);
    }

    getYears(): string[] {
        return this.trips
            .map(trip => trip.start.substring(0, 4))
            .filter((year, index, self) => self.indexOf(year) === index);
    }

    getTripsForYear(year: string): Trip[] {
        return this.trips.filter(trip => {
            return trip.start.startsWith(year);
        });
    }

    calculateDaysSpent(trip: Trip): number {
        return new Date(trip.end).getDate() - new Date(trip.start).getDate();
    }

    protected readonly getFlagEmoji = getFlagEmoji;
}
