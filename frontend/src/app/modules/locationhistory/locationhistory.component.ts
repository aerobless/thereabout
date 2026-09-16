import {Component, OnInit, ChangeDetectionStrategy, DestroyRef, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {
    GoogleMap,
    MapGeocoder,
    MapPolyline
} from "@angular/google-maps";
import {
    LocationHistoryEntry,
    LocationService
} from "../../../../generated/backend-api/thereabout";
import {InputTextModule} from "primeng/inputtext";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {FormsModule} from "@angular/forms";
import {ButtonModule} from "primeng/button";
import {DatePickerModule} from "primeng/datepicker";

import {FloatLabelModule} from "primeng/floatlabel";
import QuickFilterDateCombo from "./quick-filter-date-combo";
import {ActivatedRoute} from "@angular/router";
import {TooltipModule} from "primeng/tooltip";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {ThereaboutHeatmapLayerDirective} from "./thereabout-heatmap-layer.directive";


@Component({
    selector: 'app-locationhistory',
    imports: [
    GoogleMap,
    ThereaboutHeatmapLayerDirective,
    ToolbarComponent,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    FormsModule,
    ButtonModule,
    DatePickerModule,
    MapPolyline,
    FloatLabelModule,
    TooltipModule,
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
    heatmapData: { lng: number; lat: number }[] = [];
    fromDate: Date = new Date(new Date().setFullYear(new Date().getFullYear() - 1));
    toDate: Date = new Date();

    // Day view
    dayViewDataFull: Array<LocationHistoryEntry> = [];
    exactDate: Date = new Date();

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
                private route: ActivatedRoute) {
    }

    private readonly destroyRef = inject(DestroyRef);
    private heatmapRequestId = 0;
    private dayRequestId = 0;
    private rangeRequestId = 0;
    heatmapLoading = false;
    heatmapError = false;

    ngOnInit() {
        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
            this.embedMode = params['embed'] === 'true';
            ++this.dayRequestId;
            ++this.rangeRequestId;
            ++this.heatmapRequestId;
            this.dayViewDataFull = [];
            this.dateRangeViewDataFull = [];
            const selectedDate = this.parseIsoDate(params['date']);
            const fromDate = this.parseIsoDate(params['fromDate']);
            const toDate = this.parseIsoDate(params['toDate']);
            const valid = fromDate !== null && toDate !== null && fromDate <= toDate;
            this.dateRangeFrom = valid ? this.dateToString(fromDate) : undefined;
            this.dateRangeTo = valid ? this.dateToString(toDate) : undefined;
            this.embedRangeValid = valid;
            this.embedFromDate = valid ? fromDate : undefined;
            this.embedToDate = valid ? toDate : undefined;
            if (!this.embedMode) {
                this.fromDate = valid ? fromDate : new Date(new Date().setFullYear(new Date().getFullYear() - 1));
                this.toDate = valid ? toDate : new Date();
                this.loadHeatmapData();
            } else if (valid) {
                this.exactDate = selectedDate && this.isWithinEmbedRange(selectedDate) ? selectedDate : fromDate;
                this.loadDateRangeViewData();
                this.loadDayViewData();
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
        const requestId = ++this.heatmapRequestId;
        this.heatmapData = [];
        this.heatmapError = false;
        this.heatmapLoading = false;
        if (!this.fromDate || !this.toDate || this.fromDate > this.toDate) return;
        this.heatmapLoading = true;
        this.locationService.getSparseLocations(this.dateToString(this.fromDate), this.dateToString(this.toDate))
            .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: locations => {
                    if (requestId !== this.heatmapRequestId) return;
                    this.heatmapData = locations.map(location => ({lat: location.latitude, lng: location.longitude}));
                    this.heatmapLoading = false;
                },
                error: () => {
                    if (requestId !== this.heatmapRequestId) return;
                    this.heatmapLoading = false;
                    this.heatmapError = true;
                }
            });
    }

    loadDayViewData(focusEmbedDay = false) {
        if (!this.exactDate) return;
        const requestId = ++this.dayRequestId;
        this.dayViewDataFull = [];
        this.locationService.getLocations(this.dateToString(this.exactDate), this.dateToString(this.exactDate))
            .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: locations => {
                    if (requestId !== this.dayRequestId) return;
                    this.dayViewDataFull = locations;
                    if (focusEmbedDay) this.fitEmbedDayBounds();
                },
                error: () => { /* Keep the trip visible when a day cannot be loaded. */ }
            });
    }

    loadDateRangeViewData() {
        if (!this.dateRangeFrom || !this.dateRangeTo) return;
        const requestId = ++this.rangeRequestId;
        this.locationService.getLocations(this.dateRangeFrom, this.dateRangeTo)
            .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: locations => {
                    if (requestId !== this.rangeRequestId) return;
                    this.dateRangeViewDataFull = locations;
                    this.fitEmbedTripBounds();
                },
                error: () => { /* Keep the map usable when the trip cannot be loaded. */ }
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
        this.loadDayViewData(true);
    }

    onEmbedDateChanged() {
        if (!this.isWithinEmbedRange(this.exactDate)) return;
        this.loadDayViewData(true);
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
            this.geocodeService.geocode({address: this.searchValue}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
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

}
