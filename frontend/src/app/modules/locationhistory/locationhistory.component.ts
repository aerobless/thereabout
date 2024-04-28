import {Component, OnInit, ViewChild} from '@angular/core';
import {GoogleMap, MapGeocoder, MapHeatmapLayer} from "@angular/google-maps";
import {LocationService} from "../../../../generated/backend-api/thereabout";
import {ToolbarModule} from "primeng/toolbar";
import {InputTextModule} from "primeng/inputtext";
import {CardModule} from "primeng/card";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {FormsModule} from "@angular/forms";
import {ButtonModule} from "primeng/button";


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
    ButtonModule
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

  searchValue: string = '';

  constructor(private readonly locationService: LocationService, private readonly geocodeService: MapGeocoder) {
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

  resetSearch() {
    this.searchValue = '';
  }
}
