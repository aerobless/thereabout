import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {LocationhistoryComponent} from "./modules/locationhistory/locationhistory.component";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LocationhistoryComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'thereabout';
}
