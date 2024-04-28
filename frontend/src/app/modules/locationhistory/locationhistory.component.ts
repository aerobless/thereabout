import {Component, OnInit} from '@angular/core';
import {GoogleMap, MapHeatmapLayer} from "@angular/google-maps";
import {LocationService} from "../../../../generated/backend-api/thereabout";

@Component({
  selector: 'app-locationhistory',
  standalone: true,
  imports: [
    GoogleMap,
    MapHeatmapLayer
  ],
  templateUrl: './locationhistory.component.html',
  styleUrl: './locationhistory.component.scss'
})
export class LocationhistoryComponent implements OnInit{

  center = {lat: 47.3919661, lng: 8.3};
  zoom = 4;
  heatmapOptions = {radius: 8, maxIntensity: 2};
  heatmapData = [
    {lat: 37.782, lng: -122.447}
  ];

  constructor(private readonly locationService: LocationService,) {
  }

  ngOnInit(){
    this.loadHeatmapData();
  }

  loadHeatmapData(){
    this.locationService.getLocations().subscribe(locations => {
      this.heatmapData = locations.map(location => {
        return {lat: location.latitude, lng: location.longitude}
      });
    });
  }

}
