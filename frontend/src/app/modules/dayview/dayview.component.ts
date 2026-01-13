import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {ToolbarModule} from "primeng/toolbar";
import {Router} from "@angular/router";
import {TooltipModule} from "primeng/tooltip";
import {CalendarModule} from "primeng/calendar";
import {FormsModule} from "@angular/forms";
import {PanelModule} from "primeng/panel";
import {NgIf, NgForOf, DatePipe} from "@angular/common";
import {
    GoogleMap,
    MapMarker,
    MapPolyline
} from "@angular/google-maps";
import {
    LocationHistoryEntry,
    LocationService
} from "../../../../generated/backend-api/thereabout";

@Component({
  selector: 'app-dayview',
  standalone: true,
  imports: [
    ButtonModule,
    ToolbarModule,
    TooltipModule,
    CalendarModule,
    FormsModule,
    PanelModule,
    NgIf,
    NgForOf,
    DatePipe,
    GoogleMap,
    MapMarker,
    MapPolyline
  ],
  templateUrl: './dayview.component.html',
  styleUrl: './dayview.component.scss'
})
export class DayviewComponent implements OnInit {

  selectedDate: Date = new Date();
  
  // Map configuration
  center = {lat: 47.3919661, lng: 8.3};
  zoom = 4;
  
  // Day view data
  dayViewDataFull: Array<LocationHistoryEntry> = [];
  selectedLocationEntries: LocationHistoryEntry[] = [];

  constructor(private router: Router, private locationService: LocationService) {
  }

  navigateBackToMap() {
    this.router.navigate(['']);
  }

  goToPreviousDay() {
    const previousDay = new Date(this.selectedDate);
    previousDay.setDate(previousDay.getDate() - 1);
    this.selectedDate = previousDay;
    this.loadDayViewData();
  }

  goToNextDay() {
    const nextDay = new Date(this.selectedDate);
    nextDay.setDate(nextDay.getDate() + 1);
    this.selectedDate = nextDay;
    this.loadDayViewData();
  }

  onDateChange() {
    // Handle date change - can be extended with additional logic
    console.log('Date changed to:', this.selectedDate);
    this.loadDayViewData();
  }

  ngOnInit() {
    this.loadDayViewData();
  }

  loadDayViewData() {
    if (!this.selectedDate) return;
    this.locationService.getLocations(this.dateToString(this.selectedDate), this.dateToString(this.selectedDate)).subscribe(locations => {
      this.dayViewDataFull = locations;
      this.selectedLocationEntries = [];
      
      // Center map on the first location if available
      if (locations.length > 0) {
        this.center = {lat: locations[0].latitude, lng: locations[0].longitude};
        this.zoom = 12;
      }
    });
  }

  dateToString(date: Date) {
    const year = date.getFullYear().toString().padStart(4, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');

    return `${year}-${month}-${day}`;
  }

  minifyDayViewData(data: Array<LocationHistoryEntry>) {
    return data.map(location => {
      return {lat: location.latitude, lng: location.longitude}
    });
  }
} 