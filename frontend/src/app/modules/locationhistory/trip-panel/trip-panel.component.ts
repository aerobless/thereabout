import {Component, EventEmitter, Input, Output} from '@angular/core';
import {PanelModule} from "primeng/panel";
import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";
import {LocationHistoryEntry, Trip} from "../../../../../generated/backend-api/thereabout";

@Component({
    selector: 'thereabout-trip-panel',
    imports: [
        PanelModule,
        ButtonModule,
        TooltipModule
    ],
    templateUrl: './trip-panel.component.html',
    styleUrl: './trip-panel.component.scss'
})
export class TripPanelComponent {

  @Input() trip!: Trip;

  @Input() tripViewDataFull!: Array<LocationHistoryEntry>;

  @Output() closeTripView = new EventEmitter<void>();


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
    return Array.from(this.trip.visitedCountries!.values()).map(e => e.countryIsoCode).join(", ")
  }
}
