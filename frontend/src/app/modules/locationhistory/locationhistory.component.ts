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

  center = {lat: 37.774546, lng: -122.433523};
  zoom = 12;
  heatmapOptions = {radius: 5};
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
