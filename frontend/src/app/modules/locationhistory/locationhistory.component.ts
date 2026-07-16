import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {
    GoogleMap,
    MapGeocoder,
    MapHeatmapLayer,
    MapMarker,
    MapPolyline
} from "@angular/google-maps";
import {
    LocationHistoryEntry,
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
    TabsModule,
    AvatarModule,
    ToggleSwitchModule
],
    templateUrl: './locationhistory.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './locationhistory.component.scss'
})
export class LocationhistoryComponent implements OnInit {

    // Embed view
    embedMode = false;
    embedRangeValid = false;
    embedFromDate: Date | undefined;
    embedToDate: Date | undefined;
    private embedMap: google.maps.Map | undefined;

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

    constructor(private readonly locationService: LocationService,
                private readonly geocodeService: MapGeocoder,
                private messageService: MessageService,
                private route: ActivatedRoute) {
    }

    ngOnInit() {
        let standardViewDataLoaded = false;

        this.route.queryParams.subscribe(params => {
            this.embedMode = params['embed'] === 'true';
            if (!this.embedMode && !standardViewDataLoaded) {
                this.loadHeatmapData();
                standardViewDataLoaded = true;
            }

            const selectedDate = this.parseIsoDate(params['date']);
            const fromDate = this.parseIsoDate(params['fromDate']);
            const toDate = this.parseIsoDate(params['toDate']);
            const hasValidDateRange = fromDate !== null && toDate !== null && fromDate.getTime() <= toDate.getTime();

            if (hasValidDateRange) {
                this.dateRangeFrom = this.dateToString(fromDate);
                this.dateRangeTo = this.dateToString(toDate);
                this.embedRangeValid = true;
                this.embedFromDate = fromDate;
                this.embedToDate = toDate;
                this.exactDate = selectedDate && (!this.embedMode || this.isWithinEmbedRange(selectedDate))
                    ? selectedDate
                    : fromDate;
                this.loadDateRangeViewData();
            } else {
                this.dateRangeFrom = undefined;
                this.dateRangeTo = undefined;
                this.dateRangeViewDataFull = [];
                this.embedRangeValid = false;
                this.embedFromDate = undefined;
                this.embedToDate = undefined;
                if (selectedDate) {
                    this.exactDate = selectedDate;
                }
            }

            if (!this.embedMode || hasValidDateRange) {
                this.loadDayViewData();
            } else {
                this.dayViewDataFull = [];
                this.selectedLocationEntries = [];
            }
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

    loadHeatmapData() {
        if (!this.fromDate || !this.toDate) return;
        this.locationService.getSparseLocations(this.dateToString(this.fromDate), this.dateToString(this.toDate)).subscribe(locations => {
            this.heatmapData = locations.map(location => {
                return {lat: location.latitude, lng: location.longitude}
            });
        });
    }

    loadDayViewData(preselectedLocationId?: number, focusEmbedDay = false) {
        if (!this.exactDate) return;
        this.locationService.getLocations(this.dateToString(this.exactDate), this.dateToString(this.exactDate)).subscribe(locations => {
            this.dayViewDataFull = locations;
            if(preselectedLocationId){
                this.selectedLocationEntries = locations.filter(value => value.id === preselectedLocationId);
            } else {
                this.selectedLocationEntries = [];
            }
            if (focusEmbedDay) {
                this.fitEmbedDayBounds();
            }
        });
    }

    loadDateRangeViewData() {
        if (!this.dateRangeFrom || !this.dateRangeTo) return;
        this.locationService.getLocations(this.dateRangeFrom, this.dateRangeTo).subscribe(locations => {
            this.dateRangeViewDataFull = locations;
            this.fitEmbedTripBounds();
        });
    }

    onEmbedMapInitialized(map: google.maps.Map) {
        this.embedMap = map;
        this.fitEmbedTripBounds();
    }

    private fitEmbedTripBounds() {
        if (!this.embedMode || !this.embedMap || this.dateRangeViewDataFull.length === 0) {
            return;
        }

        if (this.dateRangeViewDataFull.length === 1) {
            const onlyLocation = this.dateRangeViewDataFull[0];
            this.embedMap.setCenter({lat: onlyLocation.latitude, lng: onlyLocation.longitude});
            this.embedMap.setZoom(15);
            return;
        }

        const bounds = new google.maps.LatLngBounds();
        for (const location of this.dateRangeViewDataFull) {
            bounds.extend({lat: location.latitude, lng: location.longitude});
        }
        this.embedMap.fitBounds(bounds, 24);
    }

    private fitEmbedDayBounds() {
        if (!this.embedMode || !this.embedMap || this.dayViewDataFull.length === 0) {
            return;
        }

        if (this.dayViewDataFull.length === 1) {
            const onlyLocation = this.dayViewDataFull[0];
            this.embedMap.setCenter({lat: onlyLocation.latitude, lng: onlyLocation.longitude});
            this.embedMap.setZoom(15);
            return;
        }

        const bounds = new google.maps.LatLngBounds();
        for (const location of this.dayViewDataFull) {
            bounds.extend({lat: location.latitude, lng: location.longitude});
        }
        this.embedMap.fitBounds(bounds, 48);
    }

    decrementEmbedDate() {
        this.shiftEmbedDate(-1);
    }

    incrementEmbedDate() {
        this.shiftEmbedDate(1);
    }

    private shiftEmbedDate(days: number) {
        if (!this.embedRangeValid) return;

        const nextDate = new Date(this.exactDate);
        nextDate.setDate(nextDate.getDate() + days);
        if (!this.isWithinEmbedRange(nextDate)) return;

        this.exactDate = nextDate;
        this.loadDayViewData(undefined, true);
    }

    onEmbedDateChanged() {
        if (!this.isWithinEmbedRange(this.exactDate)) return;
        this.loadDayViewData(undefined, true);
    }

    canDecrementEmbedDate() {
        return !!this.embedFromDate && this.exactDate.getTime() > this.embedFromDate.getTime();
    }

    canIncrementEmbedDate() {
        return !!this.embedToDate && this.exactDate.getTime() < this.embedToDate.getTime();
    }

    openInThereabout() {
        if (!this.dateRangeFrom || !this.dateRangeTo) return;

        const url = new URL('/locationhistory', window.location.origin);
        url.searchParams.set('fromDate', this.dateRangeFrom);
        url.searchParams.set('toDate', this.dateRangeTo);
        window.open(url.toString(), '_blank', 'noopener');
    }

    private isWithinEmbedRange(date: Date) {
        return !!this.embedFromDate && !!this.embedToDate
            && date.getTime() >= this.embedFromDate.getTime()
            && date.getTime() <= this.embedToDate.getTime();
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

}
