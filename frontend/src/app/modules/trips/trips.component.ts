import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {Router} from "@angular/router";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {MenuItem, MessageService} from "primeng/api";
import {AvatarModule} from "primeng/avatar";
import {PanelModule} from "primeng/panel";
import {ChipModule} from "primeng/chip";
import {TagModule} from "primeng/tag";
import {SplitButtonModule} from "primeng/splitbutton";
import {Trip, TripService} from "../../../../generated/backend-api/thereabout";
import {NgForOf, NgIf} from "@angular/common";
import {getFlagEmoji} from "../../util/country-util";
import {DialogModule} from "primeng/dialog";
import {InputTextModule} from "primeng/inputtext";
import {InputTextareaModule} from "primeng/inputtextarea";
import {CalendarModule} from "primeng/calendar";
import {FormsModule} from "@angular/forms";
import {FloatLabelModule} from "primeng/floatlabel";
import {GoogleMap, MapHeatmapLayer, MapMarker, MapPolyline} from "@angular/google-maps";
import {TableModule} from "primeng/table";
import {ProgressSpinnerModule} from "primeng/progressspinner";
import {ReformatDatePipe} from "../../util/reformat-date.pipe";

@Component({
  selector: 'app-trips',
  standalone: true,
    imports: [
        ButtonModule,
        ToolbarComponent,
        AvatarModule,
        PanelModule,
        ChipModule,
        TagModule,
        SplitButtonModule,
        NgForOf,
        DialogModule,
        InputTextModule,
        InputTextareaModule,
        CalendarModule,
        NgIf,
        FormsModule,
        FloatLabelModule,
        GoogleMap,
        MapHeatmapLayer,
        MapMarker,
        MapPolyline,
        TableModule,
        ProgressSpinnerModule,
        ReformatDatePipe
    ],
  templateUrl: './trips.component.html',
  styleUrl: './trips.component.scss'
})
export class TripsComponent implements OnInit {

    addTripDialogVisible: boolean = false;
    tripPanelMenuItems: MenuItem[] = [
        {
            icon: 'pi pi-pencil',
            label: 'Edit',
            command: () => this.editTrip(this.currentTrip!)
        },
        {
            icon: 'pi pi-trash',
            label: 'Delete',
            command: () => this.deleteTrip(this.currentTrip!)
        }
    ];

    trips: Trip[] = [];
    currentTrip: Trip = {description: "", end: "", id: 0, start: "", title: ""};
    currentTripStart: Date | undefined;
    currentTripEnd: Date | undefined;
    isLoading: boolean = false;

    ngOnInit(): void {
        this.updateTrips();
    }

    private updateTrips() {
        this.isLoading = true;
        this.tripService.getTrips().subscribe(trips => {
            this.trips = trips;
            this.isLoading = false;
        });
    }

    constructor(private router: Router, private messageService: MessageService, private tripService: TripService) {
    }

    getYears(): string[] {
        return this.trips
            .sort((a, b) => a.start.localeCompare(b.start))
            .reverse()
            .map(trip => trip.start.substring(0, 4))
            .filter((year, index, self) => self.indexOf(year) === index);
    }

    getTripsForYear(year: string): Trip[] {
        return this.trips.filter(trip => {
            return trip.start.startsWith(year);
        });
    }

    calculateDaysSpent(trip: Trip): number {
        const startDate = new Date(trip.start);
        const endDate = new Date(trip.end);
        const differenceInTime = endDate.getTime() - startDate.getTime();
        const differenceInDays = differenceInTime / (1000 * 3600 * 24);
        return Math.round(differenceInDays);
    }

    protected readonly getFlagEmoji = getFlagEmoji;

    saveTrip() {
        this.addTripDialogVisible = false;
        this.tripService.addTrip(this.currentTrip).subscribe(trip => {
            this.updateTrips();
            this.messageService.add({severity:'success', summary: 'Success', detail: 'Trip added'});
        });
    }

    updateTrip() {
        this.addTripDialogVisible = false;
        this.tripService.updateTrip(this.currentTrip.id, this.currentTrip).subscribe(trip => {
            this.updateTrips();
            this.messageService.add({severity:'success', summary: 'Success', detail: 'Trip updated'});
        });
    }

    addTrip(){
        this.addTripDialogVisible = true;
        this.currentTrip = {description: "", end: "", id: 0, start: "", title: ""};
        this.currentTripStart = undefined;
        this.currentTripEnd = undefined;
    }

    deleteTrip(trip: Trip) {
        this.tripService.deleteTrip(trip.id).subscribe(() => {
            this.updateTrips();
            this.messageService.add({severity:'success', summary: 'Success', detail: 'Trip deleted'});
        });
    }

    editTrip(trip: Trip) {
        this.addTripDialogVisible = true;
    }

    onTripPanelMenuClick(trip: Trip) {
        this.currentTrip = trip;
        this.currentTripStart = new Date(trip.start);
        this.currentTripEnd = new Date(trip.end);
        console.log(this.currentTrip);
    }

    isSaveButtonDisabled(): boolean {
        return !this.currentTripEnd || !this.currentTripStart || !this.currentTrip.title || this.isStartAfterEnd();
    }

    isStartAfterEnd(): boolean {
        return this.currentTripStart! > this.currentTripEnd!;
    }

    isEdit(){
        return this.currentTrip.id !== 0;
    }

    formatIsoDateWithoutTimeZoneAdjustment(date: Date): string {
        return date.getFullYear() + '-' +
            String(date.getMonth() + 1).padStart(2, '0') + '-' +
            String(date.getDate()).padStart(2, '0') + 'T' +
            String(date.getHours()).padStart(2, '0') + ':' +
            String(date.getMinutes()).padStart(2, '0') + ':' +
            String(date.getSeconds()).padStart(2, '0') + '.' +
            String(date.getMilliseconds()).padStart(3, '0');
    }

    viewTrip(trip: Trip){
        this.router.navigate(['locationhistory'], {queryParams: {tripId: trip.id}});
    }

}
