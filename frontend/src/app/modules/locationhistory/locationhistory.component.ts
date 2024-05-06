import {Component, OnInit, ViewChild} from '@angular/core';
import {
  GoogleMap, MapAdvancedMarker,
  MapGeocoder,
  MapHeatmapLayer,
  MapMarker,
  MapMarkerClusterer,
  MapPolyline
} from "@angular/google-maps";
import {LocationService} from "../../../../generated/backend-api/thereabout";
import {ToolbarModule} from "primeng/toolbar";
import {InputTextModule} from "primeng/inputtext";
import {CardModule} from "primeng/card";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {FormsModule} from "@angular/forms";
import {ButtonModule} from "primeng/button";
import {CalendarModule} from "primeng/calendar";
import {PanelModule} from "primeng/panel";
import {NgIf} from "@angular/common";
import {FloatLabelModule} from "primeng/floatlabel";
import QuickFilterDateCombo from "./quickselect-date-combo";


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
  ],
  templateUrl: './locationhistory.component.html',
  styleUrl: './locationhistory.component.scss'
})
export class LocationhistoryComponent implements OnInit{

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
  exactDate: Date = new Date();
  @ViewChild('exactDateCalendarInput')
  private exactDateCalendarInput: any;

  constructor(private readonly locationService: LocationService, private readonly geocodeService: MapGeocoder) {
  }

  ngOnInit(){
    this.loadHeatmapData();
  }

  loadHeatmapData(){
    if(!this.fromDate || !this.toDate) return;
    this.locationService.getSparseLocations(this.dateToString(this.fromDate), this.dateToString(this.toDate)).subscribe(locations => {
      this.heatmapData = locations.map(location => {
        return {lat: location.latitude, lng: location.longitude}
      });
    });
  }

  loadDayViewData(){
    if(!this.exactDate) return;
    this.locationService.getLocations(this.dateToString(this.exactDate), this.dateToString(this.exactDate)).subscribe(locations => {
      this.dayViewData = locations.map(location => {
        return {lat: location.latitude, lng: location.longitude}
      });
    });
  }

  geocodeAddress($event: KeyboardEvent){
    if($event.key == 'Enter'){
      this.geocodeService.geocode({address: this.searchValue}).subscribe(result => {
        if(result.status == 'OK'){
          let location = result.results[0].geometry.location;
          this.center = {lat: location.lat(), lng: location.lng()};
          this.zoom = 11;
        }
      });
    }
  }

  dateToString(date: Date){
    return date.toISOString().substring(0, 10);
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

  setQuickFilterForHeatmap(quickFilterDateOption: QuickFilterDateCombo){
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
}
