import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {CalendarModule} from "primeng/calendar";
import {NgIf} from "@angular/common";
import {PanelModule} from "primeng/panel";
import {MessageService, SharedModule} from "primeng/api";
import {TableModule} from "primeng/table";
import {TooltipModule} from "primeng/tooltip";
import {FormsModule} from "@angular/forms";
import {LocationHistoryEntry, LocationService} from "../../../../../generated/backend-api/thereabout";
import {DialogModule} from "primeng/dialog";
import {InputNumberModule} from "primeng/inputnumber";
import {InputTextareaModule} from "primeng/inputtextarea";

@Component({
  selector: 'thereabout-day-panel',
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
        InputTextareaModule
    ],
  templateUrl: './day-panel.component.html',
  styleUrl: './day-panel.component.scss'
})
export class DayPanelComponent {

    @Input() exactDate!: Date;
    @Input() dayViewDataFull!: Array<LocationHistoryEntry>
    @Input() center = {lat: 47.3919661, lng: 8.3};
    @Input() highlightedLocationEntry: LocationHistoryEntry | undefined;
    @Input() selectedLocationEntries: LocationHistoryEntry[] = [];

    @Output() loadDayViewData = new EventEmitter<number>();
    @Output() loadTripViewData = new EventEmitter<void>();
    @Output() applyZoom = new EventEmitter<number>();
    @Output() centerChange = new EventEmitter<{ lat: number, lng: number }>()
    @Output() exactDateChange = new EventEmitter<Date>();
    @Output() highlightedLocationEntryChange = new EventEmitter<LocationHistoryEntry | undefined>();
    @Output() selectedLocationEntriesChange = new EventEmitter<LocationHistoryEntry[]>();

    @ViewChild('exactDateCalendarInput')
    private exactDateCalendarInput: any;

    // Edit Modal
    editModalVisible: boolean = false;
    editDate: Date = new Date();

    constructor(private readonly locationService: LocationService,
                private messageService: MessageService,) {
    }


    decrementDate() {
        this.exactDate.setDate(this.exactDate.getDate() - 1);
        this.exactDateChange.emit(this.exactDate);
        this.exactDateCalendarInput.updateInputfield();
        this.loadDayViewData.emit();
    }

    incrementDate() {
        this.exactDate.setDate(this.exactDate.getDate() + 1);
        this.exactDateChange.emit(this.exactDate);
        this.exactDateCalendarInput.updateInputfield();
        this.loadDayViewData.emit();
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
            if (this.dayViewDataFull.length == 0) return;
            this.center = this.minifyDayViewData(this.dayViewDataFull)[0];
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

    setTodayForDayView() {
        this.exactDate = new Date();
        this.exactDateChange.emit(this.exactDate);
        this.loadDayViewData.emit();
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

    createNewLocationEntry() {
        if (this.selectedLocationEntries.length === 1){
            this.locationService.addLocation({
                latitude: this.selectedLocationEntries[0].latitude,
                longitude: this.selectedLocationEntries[0].longitude,
                timestamp: new Date(this.selectedLocationEntries[0].timestamp).toISOString(),
                id: 0,
                altitude: 0,
            }).subscribe(resp => {
                this.messageService.add({
                    severity: 'success',
                    summary: 'Location created',
                    detail: `The location was successfully created.`
                });
                this.loadDayViewData.emit(resp.id);
                this.loadTripViewData.emit();
            });
        }

        if (this.selectedLocationEntries.length === 0){
            this.exactDate.setHours(12,0,0,0)
            this.locationService.addLocation({
                latitude: this.center.lat,
                longitude: this.center.lng,
                timestamp: new Date(this.exactDate).toISOString(),
                id: 0,
                altitude: 0,
            }).subscribe(resp => {
                this.messageService.add({
                    severity: 'success',
                    summary: 'Location created',
                    detail: `The location was successfully created.`
                });
                this.loadDayViewData.emit(resp.id);
            });
        }
    }

    notSingleLocationSelected() {
        return !(this.selectedLocationEntries.length === 1);
    }

    notAtLeastOneLocationSelected() {
        return this.selectedLocationEntries.length === 0;
    }

    newLocationBtnDisabled() {
        return !(this.selectedLocationEntries.length === 0 || this.selectedLocationEntries.length === 1);
    }

    showEditModal() {
        this.editModalVisible = true;
        this.editDate = new Date(this.selectedLocationEntries[0].timestamp);
    }

    cancelEdit() {
        this.loadDayViewData.emit();
        this.loadTripViewData.emit();
    }

    saveEdit() {
        this.locationService.updateLocation(this.selectedLocationEntries[0].id, this.selectedLocationEntries[0]).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location updated',
                detail: `The location was successfully updated.`
            });
            this.loadDayViewData.emit();
            this.loadTripViewData.emit();
        });
        this.editModalVisible = false;
    }

    deleteLocationEntry() {
        if (this.selectedLocationEntries.length == 0) return;

        this.locationService.deleteLocations(this.selectedLocationEntries.map(value => value.id)).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location deleted',
                detail: 'The location entry was successfully deleted.'
            });
            this.loadDayViewData.emit();
            this.loadTripViewData.emit();
        });
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

    onDateChanged() {
        this.exactDateChange.emit(this.exactDate);
        this.loadDayViewData.emit();
    }
}
