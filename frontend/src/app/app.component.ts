import {Component, OnInit} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {LocationhistoryComponent} from "./modules/locationhistory/locationhistory.component";
import {NgIf} from "@angular/common";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LocationhistoryComponent, NgIf],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit{
  title = 'thereabout';
  googleMapsIntegrationReady = false;

  ngOnInit(): void {
    this.waitForGoogleMaps().then(() => {
      this.googleMapsIntegrationReady = true;
    });
  }

  waitForGoogleMaps(): Promise<void> {
    return new Promise((resolve) => {
      function checkGoogleMapsReady() {
        if ((window as any)['googleMapsIntegrationReady']) {
          resolve();
        } else {
          setTimeout(checkGoogleMapsReady, 100);
        }
      }

      checkGoogleMapsReady();
    });
  }

}
