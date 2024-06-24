import {Component, OnInit, ViewChild} from '@angular/core';
import {
    GoogleMap,
    MapAdvancedMarker,
    MapGeocoder,
    MapHeatmapLayer,
    MapMarker,
    MapMarkerClusterer,
    MapPolyline
} from "@angular/google-maps";
import {LocationHistoryEntry, LocationService, Trip, TripService} from "../../../../generated/backend-api/thereabout";
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
import {TableModule} from "primeng/table";
import {MessageService} from "primeng/api";
import {ToastModule} from "primeng/toast";
import {ActivatedRoute, Router} from "@angular/router";
import {DialogModule} from "primeng/dialog";
import {InputNumberModule} from "primeng/inputnumber";
import {StyleClassModule} from "primeng/styleclass";
import {TooltipModule} from "primeng/tooltip";
import {InputTextareaModule} from "primeng/inputtextarea";


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
        DialogModule,
        InputNumberModule,
        StyleClassModule,
        TooltipModule,
        InputTextareaModule,
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
    dayViewDataFull: Array<LocationHistoryEntry> = [];
    exactDate: Date = new Date();
    @ViewChild('exactDateCalendarInput')
    private exactDateCalendarInput: any;

    // Edit Modal
    editModalVisible: boolean = false;
    editDate: Date = new Date();
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


    // Trip view
    currentTrip: Trip | null = null;
    tripViewDataFull: Array<LocationHistoryEntry> = [];

    constructor(private readonly locationService: LocationService,
                private readonly geocodeService: MapGeocoder,
                private messageService: MessageService,
                private tripService: TripService,
                private route: ActivatedRoute,
                private router: Router) {
    }

    ngOnInit() {
        this.loadHeatmapData();
        this.loadDayViewData();
        this.route.queryParams.subscribe(params => {
            let tripId = params['tripId'] || null;
            if (tripId) {
                this.tripService.getTrips().subscribe(trips => {
                    this.currentTrip = trips.find(trip => trip.id == tripId) || null;
                    if (this.currentTrip) {
                        this.exactDate = new Date(this.currentTrip.start);
                        this.loadDayViewData();
                        this.loadTripViewData();
                    }
                });
            }
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

    loadTripViewData() {
        if (!this.currentTrip) return;
        this.locationService.getLocations(this.currentTrip?.start, this.currentTrip?.end).subscribe(locations => {
            this.tripViewDataFull = locations;
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
            this.center = {
                lat: this.selectedLocationEntries[0].latitude,
                lng: this.selectedLocationEntries[0].longitude
            };
            this.applyZoom(16);
        } else {
            if (this.dayViewDataFull.length == 0) return;
            this.center = this.minifyDayViewData(this.dayViewDataFull)[0];
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

    deleteLocationEntry() {
        if (this.selectedLocationEntries.length == 0) return;

        this.locationService.deleteLocations(this.selectedLocationEntries.map(value => value.id)).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location deleted',
                detail: 'The location entry was successfully deleted.'
            });
            this.loadDayViewData();
            this.loadTripViewData();
        });
    }

    openConfiguration() {
        this.router.navigate(['configuration']);
    }

    openStatistics() {
        this.router.navigate(['statistics']);
    }

    onMouseOverLocationEntry($event: MouseEvent, locationEntry: any) {
        if ($event.type == 'mouseleave') {
            this.highlightedLocationEntry = undefined;
        }

        if (locationEntry) {
            this.highlightedLocationEntry = locationEntry;
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
            this.loadTripViewData();
        });
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
        this.loadDayViewData();
        this.loadTripViewData();
    }

    saveEdit() {
        this.locationService.updateLocation(this.selectedLocationEntries[0].id, this.selectedLocationEntries[0]).subscribe(() => {
            this.messageService.add({
                severity: 'success',
                summary: 'Location updated',
                detail: `The location was successfully updated.`
            });
            this.loadDayViewData();
            this.loadTripViewData();
        });
        this.editModalVisible = false;
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
                this.loadDayViewData(resp.id);
                this.loadTripViewData();
            });
        }

        if (this.selectedLocationEntries.length === 0){
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
                this.loadDayViewData(resp.id);
            });
        }
    }

    openTrips() {
        this.router.navigate(['trips']);
    }

    closeTripView() {
        this.tripViewDataFull = [];
        this.currentTrip = null;
        this.router.navigate(['']);
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

    calculateDistanceOfCurrentTrip(): number {
        if (this.tripViewDataFull.length < 2) {
            return 0;
        }

        let totalDistance = 0;

        for (let i = 0; i < this.tripViewDataFull.length - 1; i++) {
            const point1 = new google.maps.LatLng(this.tripViewDataFull[i].latitude, this.tripViewDataFull[i].longitude);
            const point2 = new google.maps.LatLng(this.tripViewDataFull[i + 1].latitude, this.tripViewDataFull[i + 1].longitude);

            const distance = google.maps.geometry.spherical.computeDistanceBetween(point1, point2);
            totalDistance += distance;
        }

        return Math.round(totalDistance/1000);
    }

    countriesInCurrentTrip(): string {
        const countries = new Set<string>();
        this.tripViewDataFull.forEach(entry => {
            if (entry.estimatedIsoCountryCode) {
                countries.add(entry.estimatedIsoCountryCode);
            }
        });
        return Array.from(countries).join(", ")
    }
}
