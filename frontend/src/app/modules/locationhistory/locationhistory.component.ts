import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {
    GoogleMap,
    MapGeocoder,
    MapHeatmapLayer,
    MapMarker,
    MapPolyline
} from "@angular/google-maps";
import {
    LocationHistoryEntry, LocationHistoryList,
    LocationListService,
    LocationService
} from "../../../../generated/backend-api/thereabout";
import {InputTextModule} from "primeng/inputtext";
import {CardModule} from "primeng/card";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {FormsModule} from "@angular/forms";
import {ButtonModule} from "primeng/button";
import {DatePickerModule} from "primeng/datepicker";

import {FloatLabelModule} from "primeng/floatlabel";
import QuickFilterDateCombo from "./quick-filter-date-combo";
import {TableModule} from "primeng/table";
import {MessageService} from "primeng/api";
import {ToastModule} from "primeng/toast";
import {ActivatedRoute} from "@angular/router";
import {DialogModule} from "primeng/dialog";
import {InputNumberModule} from "primeng/inputnumber";
import {StyleClassModule} from "primeng/styleclass";
import {TooltipModule} from "primeng/tooltip";
import {TextareaModule} from "primeng/textarea";
import {DayPanelComponent} from "./day-panel/day-panel.component";
import {ListPanelComponent} from "./list-panel/list-panel.component";
import {TabsModule} from "primeng/tabs";
import {AvatarModule} from "primeng/avatar";
import {ToggleSwitchModule} from "primeng/toggleswitch";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";


@Component({
    selector: 'app-locationhistory',
    imports: [
    GoogleMap,
    MapHeatmapLayer,
    ToolbarComponent,
    InputTextModule,
    CardModule,
    IconFieldModule,
    InputIconModule,
    FormsModule,
    ButtonModule,
    DatePickerModule,
    MapPolyline,
    MapMarker,
    FloatLabelModule,
    TableModule,
    ToastModule,
    DialogModule,
    InputNumberModule,
    StyleClassModule,
    TooltipModule,
    TextareaModule,
    DayPanelComponent,
    ListPanelComponent,
    TabsModule,
    AvatarModule,
    ToggleSwitchModule
],
    templateUrl: './locationhistory.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
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
    alwaysShowHeatmap = true;

    // Day view
    dayViewDataFull: Array<LocationHistoryEntry> = [];
    exactDate: Date = new Date();
    tabIndex: number = 0;

    // Edit Modal
    selectedLocationEntries: LocationHistoryEntry[] = [];
    highlightedLocationEntry: LocationHistoryEntry | undefined;

    blueHighlightMarker = {
        path: "M0,0 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0",
        fillColor: "blue",
        fillOpacity: 0.6,
        strokeWeight: 0,
        rotation: 0,
        scale: 2,
    };

    lineSymbol = {
        path: 'M 0,-1 0,1',
        strokeOpacity: 1,
        scale: 4
    };

    // Date range view
    dateRangeFrom: string | undefined;
    dateRangeTo: string | undefined;
    dateRangeViewDataFull: Array<LocationHistoryEntry> = [];

    // Lists
    locationLists: LocationHistoryList[] = [];

    constructor(private readonly locationService: LocationService,
                private readonly locationListService: LocationListService,
                private readonly geocodeService: MapGeocoder,
                private messageService: MessageService,
                private route: ActivatedRoute) {
    }

    ngOnInit() {
        this.loadHeatmapData();
        this.loadLocationListData();

        this.route.queryParams.subscribe(params => {
            const selectedDate = this.parseIsoDate(params['date']);
            const fromDate = this.parseIsoDate(params['fromDate']);
            const toDate = this.parseIsoDate(params['toDate']);
            const hasValidDateRange = fromDate !== null && toDate !== null && fromDate.getTime() <= toDate.getTime();

            if (hasValidDateRange) {
                this.dateRangeFrom = this.dateToString(fromDate);
                this.dateRangeTo = this.dateToString(toDate);
                this.exactDate = selectedDate ?? fromDate;
                this.loadDateRangeViewData();
            } else {
                this.dateRangeFrom = undefined;
                this.dateRangeTo = undefined;
                this.dateRangeViewDataFull = [];
                if (selectedDate) {
                    this.exactDate = selectedDate;
                }
            }

            this.loadDayViewData();
        });
    }

    private parseIsoDate(value: unknown): Date | null {
        if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            return null;
        }

        const [year, month, day] = value.split('-').map(Number);
        const date = new Date(year, month - 1, day);

        if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
            return null;
        }

        return date;
    }

    private loadLocationListData() {
        this.locationListService.getLocationHistoryLists().subscribe(lists => {
            this.locationLists = lists;
        });
    }

    loadHeatmapData() {
        if (!this.fromDate || !this.toDate) return;
        this.locationService.getSparseLocations(this.dateToString(this.fromDate), this.dateToString(this.toDate)).subscribe(locations => {
            this.heatmapData = locations.map(location => {
                return {lat: location.latitude, lng: location.longitude}
            });
        });
    }

    loadDayViewData(preselectedLocationId?: number) {
        if (!this.exactDate) return;
        this.locationService.getLocations(this.dateToString(this.exactDate), this.dateToString(this.exactDate)).subscribe(locations => {
            this.dayViewDataFull = locations;
            if(preselectedLocationId){
                this.selectedLocationEntries = locations.filter(value => value.id === preselectedLocationId);
            } else {
                this.selectedLocationEntries = [];
            }
        });
    }

    loadDateRangeViewData() {
        if (!this.dateRangeFrom || !this.dateRangeTo) return;
        this.locationService.getLocations(this.dateRangeFrom, this.dateRangeTo).subscribe(locations => {
            this.dateRangeViewDataFull = locations;
        });
    }

    minifyDayViewData(data: Array<LocationHistoryEntry>) {
        return data.map(location => {
            return {lat: location.latitude, lng: location.longitude}
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

    applyZoom(zoom: number) {
        if (this.zoom == zoom) {
            this.zoom = zoom + 0.1;
        } else {
            this.zoom = zoom;
        }
    }

    markerDragged(entry: LocationHistoryEntry, $event: google.maps.MapMouseEvent) {
        entry.latitude = $event.latLng!.lat();
        entry.longitude = $event.latLng!.lng();
        this.locationService.updateLocation(entry.id, entry).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location updated',
                detail: `The location was successfully updated.`
            });
            this.loadDateRangeViewData();
        });
    }

    dayLineClick($event: google.maps.PolyMouseEvent) {
        if (!$event.latLng) {
            return;
        }

        let closestPoint: LocationHistoryEntry | null = null;
        let minDistance = Number.MAX_VALUE;

        this.dayViewDataFull.forEach((entry) => {
            const entryPoint = new google.maps.LatLng(entry.latitude, entry.longitude);
            const distance = google.maps.geometry.spherical.computeDistanceBetween(
                $event.latLng!,
                entryPoint
            );

            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = entry;
            }
        });

        if (closestPoint) {
            // Deselect if already selected
            if(this.selectedLocationEntries.length === 1 && this.selectedLocationEntries[0] === closestPoint){
                this.selectedLocationEntries = [];
                return;
            }
            // otherwise select
            this.selectedLocationEntries = [closestPoint];
        }
    }

    reloadDataOnTabChange() {
        this.loadLocationListData();
    }

    goToDayView($event: Date) {
        this.exactDate = $event;
        this.tabIndex = 0;
        this.loadDayViewData(this.selectedLocationEntries[0].id);
    }
}
