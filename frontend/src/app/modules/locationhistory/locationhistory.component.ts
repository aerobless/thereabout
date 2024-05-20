import {Component, OnInit, ViewChild} from '@angular/core';
import {
    GoogleMap, MapAdvancedMarker,
    MapGeocoder,
    MapHeatmapLayer,
    MapMarker,
    MapMarkerClusterer,
    MapPolyline
} from "@angular/google-maps";
import {LocationHistoryEntry, LocationService} from "../../../../generated/backend-api/thereabout";
import {ToolbarModule} from "primeng/toolbar";
import {InputTextModule} from "primeng/inputtext";
import {CardModule} from "primeng/card";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {FormsModule} from "@angular/forms";
import {ButtonModule} from "primeng/button";
import {CalendarModule} from "primeng/calendar";
import {PanelModule} from "primeng/panel";
import {NgForOf, NgIf} from "@angular/common";
import {FloatLabelModule} from "primeng/floatlabel";
import QuickFilterDateCombo from "./quick-filter-date-combo";
import {TableModule, TableRowSelectEvent} from "primeng/table";
import {MessageService} from "primeng/api";
import {ToastModule} from "primeng/toast";
import {Router} from "@angular/router";


@Component({
    selector: 'app-locationhistory',
    standalone: true,
    imports: [
        GoogleMap,
        MapHeatmapLayer,
        ToolbarModule,
        InputTextModule,
        CardModule,
        IconFieldModule,
        InputIconModule,
        FormsModule,
        ButtonModule,
        CalendarModule,
        PanelModule,
        NgIf,
        MapPolyline,
        MapMarker,
        MapMarkerClusterer,
        MapAdvancedMarker,
        FloatLabelModule,
        TableModule,
        ToastModule,
        NgForOf,
    ],
    templateUrl: './locationhistory.component.html',
    styleUrl: './locationhistory.component.scss'
})
export class LocationhistoryComponent implements OnInit {

    // Map configuration
    center = {lat: 47.3919661, lng: 8.3};
    zoom = 4;
    searchValue: string = '';

    // Heatmap
    heatmapOptions = {radius: 8, maxIntensity: 2};
    heatmapData: { lng: number; lat: number }[] = [];
    fromDate: Date = new Date(new Date().setFullYear(new Date().getFullYear() - 1));
    toDate: Date = new Date();

    // Day view
    dayViewData: { lng: number; lat: number }[] = [];
    dayViewDataFull: Array<LocationHistoryEntry> = [];
    exactDate: Date = new Date();
    @ViewChild('exactDateCalendarInput')
    private exactDateCalendarInput: any;

    selectedLocationEntries: LocationHistoryEntry[] = [];

    constructor(private readonly locationService: LocationService, private readonly geocodeService: MapGeocoder, private messageService: MessageService, private router: Router) {
    }

    ngOnInit() {
        this.loadHeatmapData();
        this.loadDayViewData();
    }

    loadHeatmapData() {
        if (!this.fromDate || !this.toDate) return;
        this.locationService.getSparseLocations(this.dateToString(this.fromDate), this.dateToString(this.toDate)).subscribe(locations => {
            this.heatmapData = locations.map(location => {
                return {lat: location.latitude, lng: location.longitude}
            });
        });
    }

    loadDayViewData() {
        if (!this.exactDate) return;
        this.locationService.getLocations(this.dateToString(this.exactDate), this.dateToString(this.exactDate)).subscribe(locations => {
            this.dayViewDataFull = locations;
            this.dayViewData = locations.map(location => {
                return {lat: location.latitude, lng: location.longitude}
            });
            this.selectedLocationEntries = [];
        });
    }

    geocodeAddress($event: KeyboardEvent) {
        if ($event.key == 'Enter') {
            this.geocodeService.geocode({address: this.searchValue}).subscribe(result => {
                if (result.status == 'OK') {
                    let location = result.results[0].geometry.location;
                    this.center = {lat: location.lat(), lng: location.lng()};
                    this.applyZoom(11);
                }
            });
        }
    }

    dateToString(date: Date) {
        const year = date.getFullYear().toString().padStart(4, '0');
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');

        return `${year}-${month}-${day}`;
    }

    resetSearch() {
        this.searchValue = '';
    }

    decrementDate() {
        this.exactDate.setDate(this.exactDate.getDate() - 1);
        this.exactDateCalendarInput.updateInputfield();
        this.loadDayViewData();
    }

    incrementDate() {
        this.exactDate.setDate(this.exactDate.getDate() + 1);
        this.exactDateCalendarInput.updateInputfield();
        this.loadDayViewData();
    }

    setQuickFilterForHeatmap(quickFilterDateOption: QuickFilterDateCombo) {
        switch (quickFilterDateOption) {
            case QuickFilterDateCombo.YTD:
                this.fromDate = new Date(new Date().getFullYear(), 0, 1);
                this.toDate = new Date();
                break;
            case QuickFilterDateCombo.ONE_YEAR:
                this.fromDate = new Date(new Date().setFullYear(new Date().getFullYear() - 1));
                this.toDate = new Date();
                break;
            case QuickFilterDateCombo.FIVE_YEARS:
                this.fromDate = new Date(new Date().setFullYear(new Date().getFullYear() - 5));
                this.toDate = new Date();
                break;
            case QuickFilterDateCombo.FULL_HISTORY:
                this.fromDate = new Date(new Date().setFullYear(new Date().getFullYear() - 50));
                this.toDate = new Date();
                break;

        }
        this.loadHeatmapData();
    }

    protected readonly QuickFilterDateCombo = QuickFilterDateCombo;

    locateDayView() {
        if (this.selectedLocationEntries.length > 0) {
            this.center = {lat: this.selectedLocationEntries[0].latitude, lng: this.selectedLocationEntries[0].longitude};
            this.applyZoom(16);
        } else {
            if (this.dayViewData.length == 0) return;
            this.center = this.dayViewData[0];
            this.applyZoom(11);
        }
    }

    setTodayForDayView() {
        this.exactDate = new Date();
        this.loadDayViewData();
    }

    private applyZoom(zoom: number) {
        if (this.zoom == zoom) {
            this.zoom = zoom + 0.1;
        } else {
            this.zoom = zoom;
        }
    }

    openGooglePhotos() {
        window.open("https://photos.google.com/search/" + this.dateToString(this.exactDate), "_blank");
    }

    convertToLocalTime(date: string) {
        return new Date(date).toLocaleTimeString().slice(0, 5);
    }

    shortCoordinates(lat: number, lng: number) {
        return `${lat.toFixed(5)},\n ${lng.toFixed(5)}`;

    }

    onRowSelect($event: TableRowSelectEvent) {

    }

    deleteLocationEntry() {
        if (this.selectedLocationEntries.length == 0) return;

        this.locationService.deleteLocations(this.selectedLocationEntries.map(value => value.id)).subscribe(() => {
            this.messageService.add({severity: 'success', summary: 'Success', detail: 'Location entry deleted'});
            this.loadDayViewData();
        });
    }

    openConfiguration() {
        this.router.navigate(['configuration']);
    }

    openStatistics() {
        this.router.navigate(['statistics']);
    }
}
