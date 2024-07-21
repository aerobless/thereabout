import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {CalendarModule} from "primeng/calendar";
import {NgForOf, NgIf} from "@angular/common";
import {PanelModule} from "primeng/panel";
import {MessageService, SharedModule} from "primeng/api";
import {TableModule} from "primeng/table";
import {TooltipModule} from "primeng/tooltip";
import {FormsModule} from "@angular/forms";
import {
    LocationHistoryEntry, LocationHistoryList,
    LocationListService,
    LocationService
} from "../../../../../generated/backend-api/thereabout";
import {DialogModule} from "primeng/dialog";
import {InputNumberModule} from "primeng/inputnumber";
import {InputTextareaModule} from "primeng/inputtextarea";
import {DropdownModule} from "primeng/dropdown";
import {InputTextModule} from "primeng/inputtext";

@Component({
  selector: 'thereabout-list-panel',
  standalone: true,
    imports: [
        ButtonModule,
        CalendarModule,
        NgIf,
        PanelModule,
        SharedModule,
        TableModule,
        TooltipModule,
        FormsModule,
        DialogModule,
        InputNumberModule,
        InputTextareaModule,
        DropdownModule,
        InputTextModule,
        NgForOf
    ],
  templateUrl: './list-panel.component.html',
  styleUrl: './list-panel.component.scss'
})
export class ListPanelComponent {

    @Input() exactDate!: Date;
    @Input() center = {lat: 47.3919661, lng: 8.3};
    @Input() highlightedLocationEntry: LocationHistoryEntry | undefined;
    @Input() selectedLocationEntries: LocationHistoryEntry[] = [];

    // New
    @Input() locationLists!: LocationHistoryList[];

    @Output() applyZoom = new EventEmitter<number>();
    @Output() centerChange = new EventEmitter<{ lat: number, lng: number }>()
    @Output() exactDateChange = new EventEmitter<Date>();
    @Output() highlightedLocationEntryChange = new EventEmitter<LocationHistoryEntry | undefined>();
    @Output() selectedLocationEntriesChange = new EventEmitter<LocationHistoryEntry[]>();

    // New
    @Output() locationListsChange = new EventEmitter<LocationHistoryList[]>();

    // Edit Modal
    editModalVisible: boolean = false;
    editDate: Date = new Date();


    // New
    selectedLocationList: LocationHistoryList | undefined;
    editListModalVisible:  boolean = false;
    editCurrentListName: string = '';

    constructor(private readonly locationService: LocationService,
                private readonly locationListService: LocationListService,
                private messageService: MessageService,) {
    }

    locateDayView() {
        if (this.selectedLocationEntries.length > 0) {
            this.center = {
                lat: this.selectedLocationEntries[0].latitude,
                lng: this.selectedLocationEntries[0].longitude
            };
            this.centerChange.emit(this.center);
            this.applyZoom.emit(16);
        } else {
            if (this.selectedLocationList?.locationHistoryEntries.length == 0) return;
            this.center = this.minifyDayViewData(this.selectedLocationList?.locationHistoryEntries || [])[0];
            this.centerChange.emit(this.center);
            this.applyZoom.emit(11);
        }
    }

    minifyDayViewData(data: Array<LocationHistoryEntry>) {
        return data.map(location => {
            return {lat: location.latitude, lng: location.longitude}
        });
    }

    openGooglePhotos() {
        window.open("https://photos.google.com/search/" + this.dateToString(this.exactDate), "_blank");
    }

    dateToString(date: Date) {
        const year = date.getFullYear().toString().padStart(4, '0');
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');

        return `${year}-${month}-${day}`;
    }

    onMouseOverLocationEntry($event: MouseEvent, locationEntry: any) {
        if ($event.type == 'mouseleave') {
            this.highlightedLocationEntry = undefined;
            this.highlightedLocationEntryChange.emit(this.highlightedLocationEntry);
        }

        if (locationEntry) {
            this.highlightedLocationEntry = locationEntry;
            this.highlightedLocationEntryChange.emit(this.highlightedLocationEntry);
        }
    }

    notSingleLocationSelected() {
        return !(this.selectedLocationEntries.length === 1);
    }

    notAtLeastOneLocationSelected() {
        return this.selectedLocationEntries.length === 0;
    }

    showEditModal() {
        this.editModalVisible = true;
        this.editDate = new Date(this.selectedLocationEntries[0].timestamp);
    }

    convertToLocalTime(date: string) {
        return new Date(date).toLocaleTimeString().slice(0, 5);
    }

    shortCoordinates(lat: number, lng: number) {
        return `${lat.toFixed(5)},\n ${lng.toFixed(5)}`;

    }

    onSelectionChange($event: any) {
        this.selectedLocationEntriesChange.emit(this.selectedLocationEntries);
    }

    cancelListEdit() {
        this.editListModalVisible = false;
    }

    saveListEdit() {
        this.locationListService.createLocationHistoryList({
            id: 0,
            name: this.editCurrentListName,
            locationHistoryEntries: []
        }).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'List created',
                detail: `The list was successfully created.`
            });
            this.locationListService.getLocationHistoryLists().subscribe(lists => {
                this.locationLists = lists;
            });
        });

        this.editCurrentListName = '';
        this.editListModalVisible = false;
    }

    createNewList() {
        this.editCurrentListName = '';
        this.editListModalVisible = true;
    }

    removeFromList() {
        this.locationListService.removeLocationFromList(this.selectedLocationList!.id, {locationHistoryEntryId: this.selectedLocationEntries[0].id}).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location removed from list',
                detail: `The location was successfully removed from the list.`
            });
            this.locationListService.getLocationHistoryLists().subscribe(lists => {
                this.locationLists = lists;
            });
        });
    }

    deleteSelection() {
        this.selectedLocationList = undefined;
    }
}
